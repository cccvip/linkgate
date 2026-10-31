package com.linkgate.netty.server;

/**
 * 启动配置：端口 / 节点 / Redis / MySQL / LLM。任一项为 {@code null} 表示不启用该能力。
 */
public record GatewayConfig(int port, String nodeId,
                            String redisUri,
                            String mysqlUrl, String mysqlUser, String mysqlPassword,
                            String llmBaseUrl, String llmApiKey) {

    /** 从环境变量读取（未设置则用默认 / 关闭）。 */
    public static GatewayConfig fromEnv() {
        return new GatewayConfig(
                intEnv("LINKGATE_PORT", 8080),
                envOr("LINKGATE_NODE_ID", GatewayComponents.DEFAULT_NODE_ID),
                envOrNull("LINKGATE_REDIS_URI"),
                envOrNull("LINKGATE_MYSQL_URL"),
                envOr("LINKGATE_MYSQL_USER", "linkgate"),
                envOr("LINKGATE_MYSQL_PASSWORD", "linkgate"),
                envOrNull("LINKGATE_LLM_BASE_URL"),
                envOrNull("LINKGATE_LLM_API_KEY"));
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String envOrNull(String key) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? null : value;
    }

    private static int intEnv(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }
}
