package com.kanade.aipassage.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 注解支持
 * 注册 SaInterceptor 后 @SaCheckLogin / @SaCheckRole 等注解才会生效；
 * 路由级校验由网关统一负责，这里只启用注解引擎，不拦截路由。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {})).addPathPatterns("/**");
    }
}
