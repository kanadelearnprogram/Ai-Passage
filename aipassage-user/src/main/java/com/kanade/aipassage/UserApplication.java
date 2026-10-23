package com.kanade.aipassage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务：登录注册、用户管理、配额与支付
 */
@SpringBootApplication
@MapperScan("com.kanade.aipassage.mapper")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
