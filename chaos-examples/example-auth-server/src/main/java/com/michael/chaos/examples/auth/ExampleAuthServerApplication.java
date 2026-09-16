package com.michael.chaos.examples.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 授权服务器示例应用入口。
 */
@SpringBootApplication
public class ExampleAuthServerApplication {

    /**
     * 启动授权服务器示例。
     */
    public static void main(String[] args) {
        SpringApplication.run(ExampleAuthServerApplication.class, args);
    }
}
