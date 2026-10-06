package com.kanade.aipassage.common.feign;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.messaging.ArticleContextDTO;
import com.kanade.aipassage.common.messaging.SaveResultRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 文章服务内部客户端（ai → article）
 */
@FeignClient(name = "aipassage-article", path = "/api/internal/article")
public interface ArticleClient {

    /**
     * 获取 Agent 执行所需的文章上下文
     */
    @GetMapping("/{taskId}/context")
    BaseResponse<ArticleContextDTO> getContext(@PathVariable("taskId") String taskId);

    /**
     * 回写阶段结果并推进状态机
     */
    @PostMapping("/{taskId}/result")
    BaseResponse<Boolean> saveResult(@PathVariable("taskId") String taskId, @RequestBody SaveResultRequest request);

    /**
     * 标记任务失败（article 侧负责退还配额并推送错误）
     */
    @PostMapping("/{taskId}/fail")
    BaseResponse<Boolean> fail(@PathVariable("taskId") String taskId, @RequestBody String message);
}
