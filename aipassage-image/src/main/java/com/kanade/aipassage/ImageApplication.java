package com.kanade.aipassage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 图片服务：多图源策略、两级缓存、COS 转存（仅内部调用）
 */
@SpringBootApplication
@MapperScan("com.kanade.aipassage.mapper")
public class ImageApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageApplication.class, args);
    }
}
