# aipassage 微服务化改造说明（micro_server 分支）

## 架构

```
                          ┌─ aipassage-user    (8411) 登录注册/用户管理/配额支付   [ap_user]
浏览器 → aipassage-gateway ┼─ aipassage-article (8421) 任务状态机/SSE推送/统计      [ap_article]
  (8090) 鉴权+限流+路由     ├─ aipassage-ai      (8431) Agent编排/任务消费/进度发布  [ap_agent]
                          └─ aipassage-image    (8441) 图源策略/两级缓存/COS       [ap_image]
                                        （ai / image 不暴露到网关，仅内部调用）
基础设施：MySQL 8 / Redis 7 / Nacos 2.4（docker compose）
```

## 服务间协作机制

| 通道 | 方向 | 用途 |
|---|---|---|
| Redis Stream（`aipassage:task:stream`，消费组手动 ACK） | article → ai | 投递 TITLE/OUTLINE/CONTENT 三阶段任务 |
| Redis pub/sub（`aipassage:progress`） | ai → article | Agent 流式进度事件，article 转发给 SSE 连接 |
| OpenFeign + same-token（`SA-SAME-TOKEN` 头） | 双向 | 配额扣减/退还（taskId 幂等）、上下文获取、结果回写、失败上报 |

认证：user 服务登录签发 Sa-Token 令牌（Redis 共享），网关 `SaReactorFilter` 统一校验；
各服务用 `@SaCheckRole` 做角色控制，`LoginUserHolder` 读当前用户。
`/api/internal/**` 内部接口不经过网关路由，且被 `InternalAuthFilter` 的 same-token 校验保护。

## 启动步骤

```bash
# 0. 基础设施（本机已有 MySQL/Redis 可跳过，只起 nacos）
docker compose up -d

# 1. 数据库拆分（幂等脚本，从 ai_passage_creator 复制表到 ap_* schema）
mysql -uroot -p < docs/sql/split-migration.sql

# 2. 依次启动五个服务（顺序无强依赖，Nacos 需先就绪）
./mvnw -pl aipassage-user    spring-boot:run
./mvnw -pl aipassage-article spring-boot:run
./mvnw -pl aipassage-ai      spring-boot:run
./mvnw -pl aipassage-image   spring-boot:run
./mvnw -pl aipassage-gateway spring-boot:run

# 3. 前端（vite 代理已指向网关 8090）
cd frontend/aiPassage && pnpm dev
```

密钥配置（已被 .gitignore 的 `*local.yml` 规则忽略，不会入库）：
- `aipassage-ai/src/main/resources/application-local.yml` → DashScope key
- `aipassage-image/src/main/resources/application-local.yml` → Pexels / 腾讯云 COS

## 建任务全链路（拆分前后对照）

1. `POST /api/article/create` → article：Feign 扣配额（幂等键 taskId）→ 建 PENDING 文章 → 状态置 PROCESSING/TITLE_GENERATING → XADD 任务消息
2. ai 消费 Stream 消息 → 取上下文 → StateGraph 编排 DashScope 生成 → 流式 chunk 经 pub/sub → article → SSE 浏览器
3. 结果回写：ai Feign 调 article `/internal/article/{taskId}/result` → 落库 + 推 TITLES_GENERATED 等阶段消息（顺序与单体一致）
4. 用户确认标题/大纲 → article 更新 phase → XADD 下一阶段消息
5. 失败：ai 上报 `/internal/article/{taskId}/fail` → article 标 FAILED → Feign 退配额 → SSE ERROR → 关连接

## 已知限制（后续迭代项）

1. **Agent 图状态仍是 MemorySaver**（进程内存）：ai 服务重启后正在执行的阶段会中断并由失败补偿兜底；要支持断点续跑需换 Redis/JDBC Saver。
2. Stream 消息消费崩溃（进程死亡）会留下 pending 消息，未实现 pending 重投/XAUTOCLAIM 对账。
3. 密码仍是 MD5+盐（兼容存量用户），建议后续换 BCrypt + 存量迁移。
4. 支付（PaymentRecord/Stripe）仍只有表结构，配额目前只能管理员改库或注册赠送。
5. mermaid-cli 仍是本机进程依赖，容器化 image 服务时需打进镜像。
