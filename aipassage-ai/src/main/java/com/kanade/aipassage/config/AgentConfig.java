package com.kanade.aipassage.config;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AgentConfig {

    /**
     * Agent 最大迭代次数
     */
    @Value("${article.agent.max-iterations:12}")
    private int maxIterations;

    /**
     * 审核开关：关闭后正文生成完直接进入配图分析（恢复无审核链路）
     */
    @Value("${article.agent.review.enabled:true}")
    private boolean reviewEnabled;

    /**
     * 审核不合格重写开关：关闭后审核只出报告不重写
     */
    @Value("${article.agent.review.retry-enabled:true}")
    private boolean reviewRetryEnabled;

    /**
     * 审核最大重写轮次（超轮次强制放行，防烧 token）
     */
    @Value("${article.agent.review.max-round:2}")
    private int reviewMaxRound;

    /**
     * 提供内存状态保存器（单例）
     * 用于 Agent 对话记忆管理
     */
    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }
}
