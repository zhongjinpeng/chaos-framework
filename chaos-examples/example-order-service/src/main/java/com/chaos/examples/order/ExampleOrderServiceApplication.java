package com.chaos.examples.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 订单服务示例应用入口。
 */
@MapperScan("com.chaos.examples.order.infra.persistence")
@SpringBootApplication
public class ExampleOrderServiceApplication {

    /**
     * 启动订单服务示例。
     */
    public static void main(String[] args) {
        SpringApplication.run(ExampleOrderServiceApplication.class, args);
    }
}
