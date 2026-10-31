package com.linkgate.netty.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** netty 模块集成测试的环境探测。 */
final class MiddlewareTestEnv {

    static final String REDIS_URI = "redis://127.0.0.1:6379";
    static final String MYSQL_URL =
            "jdbc:mysql://127.0.0.1:3306/linkgate?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    static final String MYSQL_USER = "linkgate";
    static final String MYSQL_PASSWORD = "linkgate";

    private MiddlewareTestEnv() {
    }

    static boolean redisAvailable() {
        return portOpen(6379);
    }

    static boolean mysqlAvailable() {
        return portOpen(3306);
    }

    private static boolean portOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
