package com.kanade.aipassage.controller;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.DeleteRequest;
import com.kanade.aipassage.common.ResultUtils;
import com.kanade.aipassage.common.auth.LoginUser;
import com.kanade.aipassage.common.auth.LoginUserHolder;
import com.kanade.aipassage.common.feign.AiClient;
import com.kanade.aipassage.common.feign.FeignResponseUtils;
import com.kanade.aipassage.common.messaging.TaskMessage;
import com.kanade.aipassage.exception.ErrorCode;
import com.kanade.aipassage.exception.ThrowUtils;
import com.kanade.aipassage.model.dto.*;
import com.kanade.aipassage.model.entity.Article;
import com.kanade.aipassage.model.enums.ArticlePhaseEnum;
import com.kanade.aipassage.model.enums.ArticleStatusEnum;
import com.kanade.aipassage.model.enums.ArticleStyleEnum;
import com.kanade.aipassage.model.enums.SseMessageTypeEnum;
import com.kanade.aipassage.model.vo.AgentExecutionStats;
import com.kanade.aipassage.model.vo.ArticleVO;
import com.kanade.aipassage.service.ArticleService;
import com.kanade.aipassage.service.TaskProducer;
import com.kanade.aipassage.sse.SseEmitterManager;
import com.kanade.aipassage.utils.GsonUtils;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Resource
    private ArticleService articleService;

    @Resource
    private TaskProducer taskProducer;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private AiClient aiClient;

    /**
     * SSE 进度推送
     */
    @GetMapping("/progress/{taskId}")
    @Operation(summary = "获取文章生成进度(SSE)")
    public SseEmitter getProgress(@PathVariable String taskId) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        // 校验权限（内部会检查任务是否存在以及用户是否有权限访问）
        LoginUser loginUser = LoginUserHolder.getLoginUser();
        articleService.getArticleDetail(taskId, loginUser);

        // 创建 SSE Emitter
        SseEmitter emitter = sseEmitterManager.createEmitter(taskId);

        // 按数据库当前状态补发快照，弥补连接建立前错过的消息（晚连接/断线重连）
        sendStateSnapshot(taskId);

        log.info("SSE 连接已建立, taskId={}", taskId);
        return emitter;
    }

    /**
     * 连接建立时把当前进度重放给前端，复用页面已支持的消息类型，前端无需感知。
     * 只有终态（COMPLETED/FAILED）才会关闭连接；生成中补发已有结果后继续等实时消息。
     */
    private void sendStateSnapshot(String taskId) {
        Article article = articleService.getByTaskId(taskId);
        if (article == null) {
            return;
        }
        String status = article.getStatus();
        if (ArticleStatusEnum.FAILED.getValue().equals(status)) {
            Map<String, Object> data = new HashMap<>();
            data.put("type", SseMessageTypeEnum.ERROR.getValue());
            data.put("message", article.getErrorMessage() == null ? "生成失败" : article.getErrorMessage());
            sseEmitterManager.send(taskId, GsonUtils.toJson(data));
            sseEmitterManager.complete(taskId);
            return;
        }
        if (ArticleStatusEnum.COMPLETED.getValue().equals(status)) {
            Map<String, Object> merge = new HashMap<>();
            merge.put("fullContent", article.getFullContent() == null ? "" : article.getFullContent());
            sendSnapshot(taskId, SseMessageTypeEnum.MERGE_COMPLETE, merge);
            sendSnapshot(taskId, SseMessageTypeEnum.ALL_COMPLETE, Map.of("taskId", taskId));
            sseEmitterManager.complete(taskId);
            return;
        }
        String phase = article.getPhase();
        if (ArticlePhaseEnum.TITLE_SELECTING.getValue().equals(phase)) {
            sendJsonSnapshot(taskId, SseMessageTypeEnum.TITLES_GENERATED, "titleOptions", article.getTitleOptions());
        } else if (ArticlePhaseEnum.OUTLINE_EDITING.getValue().equals(phase)) {
            sendJsonSnapshot(taskId, SseMessageTypeEnum.OUTLINE_GENERATED, "outline", article.getOutline());
        } else if (ArticlePhaseEnum.CONTENT_GENERATING.getValue().equals(phase)) {
            // 正文流式 chunk 无法重放，先把大纲和步骤对齐，后续实时 chunk 继续推送
            sendJsonSnapshot(taskId, SseMessageTypeEnum.OUTLINE_GENERATED, "outline", article.getOutline());
            sendJsonSnapshot(taskId, SseMessageTypeEnum.AGENT2_COMPLETE, "outline", article.getOutline());
        }
    }

    private void sendSnapshot(String taskId, SseMessageTypeEnum type, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type.getValue());
        message.putAll(data);
        sseEmitterManager.send(taskId, GsonUtils.toJson(message));
    }

    /**
     * 数据库里以 JSON 字符串保存的字段（titleOptions/outline）解析后原样塞进消息
     */
    private void sendJsonSnapshot(String taskId, SseMessageTypeEnum type, String key, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put(key, GsonUtils.fromJson(json, Object.class));
        sendSnapshot(taskId, type, data);
    }

    /**
     * 创建文章任务
     */
    @PostMapping("/create")
    @Operation(summary = "创建文章任务")
    public BaseResponse<String> createArticle(@RequestBody ArticleCreateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTopic() == null || request.getTopic().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "选题不能为空");
        // 校验风格参数（允许为空）
        ThrowUtils.throwIf(!ArticleStyleEnum.isValid(request.getStyle()),
                ErrorCode.PARAMS_ERROR, "无效的文章风格");

        LoginUser loginUser = LoginUserHolder.getLoginUser();

        // 扣配额 + 创建文章任务
        String taskId = articleService.createArticleTaskWithQuotaCheck(
                request.getTopic(),
                request.getStyle(),
                request.getEnabledImageMethods(),
                loginUser
        );

        // 投递阶段1任务：生成标题方案（由 ai 服务异步消费执行）
        taskProducer.sendPhase(taskId, TaskMessage.PHASE_TITLE);

        return ResultUtils.success(taskId);
    }

    /**
     * 获取文章详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取文章详情")
    public BaseResponse<ArticleVO> getArticle(@PathVariable String taskId) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        LoginUser loginUser = LoginUserHolder.getLoginUser();
        ArticleVO articleVO = articleService.getArticleDetail(taskId, loginUser);

        return ResultUtils.success(articleVO);
    }

    /**
     * 分页查询文章列表
     */
    @PostMapping("/list")
    @Operation(summary = "分页查询文章列表")
    public BaseResponse<Page<ArticleVO>> listArticle(@RequestBody ArticleQueryRequest request) {
        LoginUser loginUser = LoginUserHolder.getLoginUser();
        Page<ArticleVO> articleVOPage = articleService.listArticleByPage(request, loginUser);

        return ResultUtils.success(articleVOPage);
    }

    /**
     * 删除文章
     */
    @PostMapping("/delete")
    @Operation(summary = "删除文章")
    public BaseResponse<Boolean> deleteArticle(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);

        LoginUser loginUser = LoginUserHolder.getLoginUser();
        boolean result = articleService.deleteArticle(deleteRequest.getId(), loginUser);

        return ResultUtils.success(result);
    }

    /**
     * 确认标题并输入补充描述
     */
    @PostMapping("/confirm-title")
    @Operation(summary = "确认标题并输入补充描述")
    public BaseResponse<Void> confirmTitle(@RequestBody ArticleConfirmTitleRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getSelectedMainTitle() == null || request.getSelectedMainTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "主标题不能为空");
        ThrowUtils.throwIf(request.getSelectedSubTitle() == null || request.getSelectedSubTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "副标题不能为空");

        LoginUser loginUser = LoginUserHolder.getLoginUser();

        // 确认标题
        articleService.confirmTitle(
                request.getTaskId(),
                request.getSelectedMainTitle(),
                request.getSelectedSubTitle(),
                request.getUserDescription(),
                loginUser
        );

        log.info("标题已确认，投递阶段2任务生成大纲, taskId={}", request.getTaskId());
        taskProducer.sendPhase(request.getTaskId(), TaskMessage.PHASE_OUTLINE);

        return ResultUtils.success(null);
    }

    /**
     * 确认大纲
     */
    @PostMapping("/confirm-outline")
    @Operation(summary = "确认大纲")
    public BaseResponse<Void> confirmOutline(@RequestBody ArticleConfirmOutlineRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getOutline() == null || request.getOutline().isEmpty(),
                ErrorCode.PARAMS_ERROR, "大纲不能为空");

        LoginUser loginUser = LoginUserHolder.getLoginUser();

        // 确认大纲
        articleService.confirmOutline(
                request.getTaskId(),
                request.getOutline(),
                loginUser
        );

        // 投递阶段3任务：生成正文+配图
        taskProducer.sendPhase(request.getTaskId(), TaskMessage.PHASE_CONTENT);

        return ResultUtils.success(null);
    }

    /**
     * AI 修改大纲
     */
    @PostMapping("/ai-modify-outline")
    @Operation(summary = "AI 修改大纲")
    public BaseResponse<List<ArticleState.OutlineSection>> aiModifyOutline(
            @RequestBody ArticleAiModifyOutlineRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getModifySuggestion() == null || request.getModifySuggestion().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "修改建议不能为空");

        LoginUser loginUser = LoginUserHolder.getLoginUser();

        // AI 修改大纲（ai 服务执行）
        List<ArticleState.OutlineSection> modifiedOutline = articleService.aiModifyOutline(
                request.getTaskId(),
                request.getModifySuggestion(),
                loginUser
        );

        return ResultUtils.success(modifiedOutline);
    }


    /**
     * 获取任务执行日志
     */
    @GetMapping("/execution-logs/{taskId}")
    @Operation(summary = "获取任务执行日志")
    public BaseResponse<AgentExecutionStats> getExecutionLogs(@PathVariable String taskId) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        // 执行日志在 ai 服务的 agent_log 库中
        AgentExecutionStats stats = FeignResponseUtils.unwrap(aiClient.getExecutionStats(taskId));
        return ResultUtils.success(stats);
    }
}
