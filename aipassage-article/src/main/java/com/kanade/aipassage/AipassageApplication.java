package com.kanade.aipassage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.kanade.aipassage.mapper")
@EnableAsync
public class AipassageApplication {

    public static void main(String[] args) {
        SpringApplication.run(AipassageApplication.class, args);
    }

}
