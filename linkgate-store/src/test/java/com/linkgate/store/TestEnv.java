package com.linkgate.store;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 集成测试环境探测：有真实服务就跑，没有就由测试用 {@code assumeTrue} 跳过。
 */
public final class TestEnv {

    public static final String REDIS_HOST = "127.0.0.1";
    public static final int REDIS_PORT = 6379;
    public static final String REDIS_URI = "redis://" + REDIS_HOST + ":" + REDIS_PORT;

    public static final String MYSQL_HOST = "127.0.0.1";
    public static final int MYSQL_PORT = 3306;
    public static final String MYSQL_URL =
            "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/linkgate?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    public static final String MYSQL_USER = "linkgate";
    public static final String MYSQL_PASSWORD = "linkgate";

    private TestEnv() {
    }

    public static boolean redisAvailable() {
        return portOpen(REDIS_HOST, REDIS_PORT);
    }

    public static boolean mysqlAvailable() {
        return portOpen(MYSQL_HOST, MYSQL_PORT);
    }

    private static boolean portOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
