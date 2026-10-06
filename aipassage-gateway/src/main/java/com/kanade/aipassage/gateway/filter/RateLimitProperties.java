package com.kanade.aipassage.gateway.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关限流规则配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    /**
     * 全局兜底规则
     */
    private Rule defaultRule = new Rule(null, 100, 60);

    /**
     * 路径级规则（Ant 风格，先匹配先生效）
     */
    private List<Rule> rules = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rule {
        /** Ant 路径模式，如 /api/article/create */
        private String pattern;
        /** 时间窗口内允许的请求数 */
        private int rate;
        /** 时间窗口（秒） */
        private int intervalSeconds;
    }
}
