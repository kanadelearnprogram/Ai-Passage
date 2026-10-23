package com.kanade.aipassage.gateway.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关统一鉴权：token 存于共享 Redis（user 服务登录签发），网关直查即可完成校验
 */
@Configuration
public class SaTokenGatewayConfig {

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(
                        "/api/user/login",
                        "/api/user/register",
                        "/api/health/**",
                        "/api/webhook/**",
                        "/favicon.ico",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/doc.html",
                        "/webjars/**")
                .setAuth(obj -> StpUtil.checkLogin())
                .setError(e -> "{\"code\":40100,\"data\":null,\"message\":\"请先登录\"}");
    }
}
