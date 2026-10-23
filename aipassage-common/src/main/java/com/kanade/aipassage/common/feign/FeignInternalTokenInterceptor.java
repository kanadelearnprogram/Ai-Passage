package com.kanade.aipassage.common.feign;

import cn.dev33.satoken.same.SaSameUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Feign 内部调用拦截器：为服务间调用附加 same-token，
 * 下游服务凭此识别"请求来自内部服务"而非外部伪造。
 */
@Slf4j
@Component
public class FeignInternalTokenInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        template.header(SaSameUtil.SAME_TOKEN, SaSameUtil.getToken());
    }
}
