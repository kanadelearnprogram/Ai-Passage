package com.kanade.aipassage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 文章服务：任务状态机、SSE 进度推送、统计
 */
@SpringBootApplication
@MapperScan("com.kanade.aipassage.mapper")
@EnableFeignClients(basePackages = "com.kanade.aipassage")
public class ArticleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArticleApplication.class, args);
    }
}
