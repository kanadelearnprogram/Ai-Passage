package com.kanade.aipassage.common.feign;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 内部接口防护过滤器注册（context-path 之下的 /internal/* 路径）
 */
@Configuration
public class InternalFilterConfigure {

    @Bean
    public FilterRegistrationBean<InternalAuthFilter> internalAuthFilter() {
        FilterRegistrationBean<InternalAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InternalAuthFilter());
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("internalAuthFilter");
        return registration;
    }
}
