package com.kanade.aipassage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 服务：多智能体编排、任务消费、进度事件发布（仅内部调用）
 */
@SpringBootApplication
@MapperScan("com.kanade.aipassage.mapper")
@EnableAsync
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableFeignClients(basePackages = "com.kanade.aipassage")
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
