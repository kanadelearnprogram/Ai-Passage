package com.kanade.aipassage.common.feign;

import cn.dev33.satoken.same.SaSameUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 内部接口防护：/internal/** 只接受携带合法 same-token 的服务间调用
 */
@Slf4j
public class InternalAuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String token = req.getHeader(SaSameUtil.SAME_TOKEN);
        if (token == null || !SaSameUtil.isValid(token)) {
            log.warn("内部接口校验失败, uri={}, remote={}", req.getRequestURI(), req.getRemoteAddr());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":40101,\"data\":null,\"message\":\"无权限访问内部接口\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
