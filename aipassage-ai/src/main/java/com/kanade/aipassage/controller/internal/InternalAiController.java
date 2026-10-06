package com.kanade.aipassage.controller.internal;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.common.ResultUtils;
import com.kanade.aipassage.common.feign.AiClient;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.vo.AgentExecutionStats;
import com.kanade.aipassage.service.AgentLogService;
import com.kanade.aipassage.service.ArticleAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 服务内部接口（仅服务间调用，same-token 防护，不暴露到网关）
 */
@RestController
@RequestMapping("/internal/ai")
@Tag(name = "InternalAiController", description = "AI 服务内部接口")
public class InternalAiController {

    @Resource
    private ArticleAgentService articleAgentService;

    @Resource
    private AgentLogService agentLogService;

    /**
     * AI 辅助修改大纲
     */
    @PostMapping("/outline/modify")
    @Operation(summary = "AI 修改大纲")
    public BaseResponse<List<ArticleState.OutlineSection>> modifyOutline(
            @RequestBody AiClient.OutlineModifyRequest request) {
        List<ArticleState.OutlineSection> modified = articleAgentService.aiModifyOutline(
                request.getMainTitle(),
                request.getSubTitle(),
                request.getCurrentOutline(),
                request.getModifySuggestion());
        return ResultUtils.success(modified);
    }

    /**
     * 任务 Agent 执行日志统计
     */
    @GetMapping("/execution-logs/{taskId}")
    @Operation(summary = "获取任务执行日志")
    public BaseResponse<AgentExecutionStats> getExecutionStats(@PathVariable String taskId) {
        return ResultUtils.success(agentLogService.getExecutionStats(taskId));
    }
}
