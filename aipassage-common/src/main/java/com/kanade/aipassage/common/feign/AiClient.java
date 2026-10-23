package com.kanade.aipassage.common.feign;

import com.kanade.aipassage.common.BaseResponse;
import com.kanade.aipassage.model.dto.ArticleState;
import com.kanade.aipassage.model.vo.AgentExecutionStats;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.Serializable;
import java.util.List;

/**
 * AI 服务内部客户端（article → ai）
 */
@FeignClient(name = "aipassage-ai", path = "/api/internal/ai")
public interface AiClient {

    /**
     * AI 辅助修改大纲（同步 LLM 调用）
     */
    @PostMapping("/outline/modify")
    BaseResponse<List<ArticleState.OutlineSection>> modifyOutline(@RequestBody OutlineModifyRequest request);

    /**
     * 查询任务 Agent 执行日志统计
     */
    @GetMapping("/execution-logs/{taskId}")
    BaseResponse<AgentExecutionStats> getExecutionStats(@PathVariable("taskId") String taskId);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class OutlineModifyRequest implements Serializable {
        private String mainTitle;
        private String subTitle;
        private List<ArticleState.OutlineSection> currentOutline;
        private String modifySuggestion;
    }
}
