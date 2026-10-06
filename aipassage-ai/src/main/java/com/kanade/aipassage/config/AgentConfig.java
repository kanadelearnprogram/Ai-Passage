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
    @Value("${article.agent.max-iterations:10}")
    private int maxIterations;

    /**
     * 提供内存状态保存器（单例）
     * 用于 Agent 对话记忆管理
     */
    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }
}
