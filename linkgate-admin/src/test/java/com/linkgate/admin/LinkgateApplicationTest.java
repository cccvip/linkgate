package com.linkgate.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.netty.server.GatewayServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * 冒烟测试：验证 Spring Boot 能装配网关 Bean，并在上下文启动时把 Netty 网关拉起来。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "linkgate.redis.enabled=false",
                "linkgate.mysql.enabled=false",
                "linkgate.llm.enabled=false",
                "linkgate.port=0"
        })
class LinkgateApplicationTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoadsAndStartsGateway() {
        GatewayServer server = context.getBean(GatewayServer.class);

        assertTrue(server.isRunning(), "网关应随 Spring 上下文启动");
        assertTrue(server.port() > 0, "端口 0 应被分配为随机端口");
    }
}
