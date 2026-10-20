package com.kanade.aipassage.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

/**
 * 分布式限流过滤器：Redisson 令牌桶，登录用户按令牌限流，匿名按 IP 限流
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitProperties properties;

    private final RedissonClient redissonClient;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(RateLimitProperties properties, RedissonClient redissonClient) {
        this.properties = properties;
        this.redissonClient = redissonClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        RateLimitProperties.Rule rule = matchRule(path);
        if (rule == null) {
            return chain.filter(exchange);
        }

        String key = limitKey(exchange);
        String redisKey = "gateway:rate-limit:" + Integer.toHexString(rule.hashCode()) + ":" + key;
        RRateLimiter limiter = redissonClient.getRateLimiter(redisKey);

        // RRateLimiter 为阻塞 API，放到弹性线程池执行
        return Mono.fromCallable(() -> { //订阅时调用 执行lambda
                    limiter.trySetRate(RateType.OVERALL, rule.getRate(), rule.getIntervalSeconds(), RateIntervalUnit.SECONDS);
                    return limiter.tryAcquire();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }
                    log.warn("限流生效, path={}, key={}", path, key);
                    return reject(exchange);
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":42900,\"data\":null,\"message\":\"请求过于频繁，请稍后再试\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private RateLimitProperties.Rule matchRule(String path) {
        for (RateLimitProperties.Rule rule : properties.getRules()) {
            if (pathMatcher.match(rule.getPattern(), path)) {
                return rule;
            }
        }
        return properties.getDefaultRule();
    }

    /**
     * 登录用户以令牌为键（防共享 IP 误伤），匿名用户以 IP 为键
     */
    private String limitKey(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        var cookie = request.getCookies().getFirst("satoken");
        String token = cookie != null ? cookie.getValue()
                : request.getHeaders().getFirst("satoken");
        if (token != null && !token.isBlank()) {
            return "u:" + token;
        }
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        String ip = xff != null && !xff.isBlank()
                ? xff.split(",")[0].trim()
                : (request.getRemoteAddress() != null
                        ? request.getRemoteAddress().getAddress().getHostAddress()
                        : "unknown");
        return "ip:" + ip;
    }

    @Override
    public int getOrder() {
        // 尽早限流；鉴权由 SaReactorFilter（WebFilter，先于 GlobalFilter）完成
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
