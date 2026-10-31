package com.linkgate.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * LinkGate 管理端启动入口：用 Spring Boot 作为组装根与管理面，数据面仍由 netty 模块裸装配。
 *
 * <p>排除 JDBC / Redis 自动配置，因为这两个中间件由 {@code linkgate-store} 按需装配（非 Spring 管理）。</p>
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        RedisAutoConfiguration.class
})
public class LinkgateApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkgateApplication.class, args);
    }
}
