package com.linkgate.admin.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关启动配置：绑定 {@code application.yml} 的 {@code linkgate.*}。
 */
@ConfigurationProperties(prefix = "linkgate")
public class LinkgateProperties {

    private int port = 8080;
    private String nodeId = "node-local";

    private final Redis redis = new Redis();
    private final Mysql mysql = new Mysql();
    private final Llm llm = new Llm();

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Redis getRedis() {
        return redis;
    }

    public Mysql getMysql() {
        return mysql;
    }

    public Llm getLlm() {
        return llm;
    }

    public static class Redis {

        private boolean enabled = false;
        private String uri = "redis://127.0.0.1:6379";
        private Duration ttl = Duration.ofSeconds(60);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    public static class Mysql {

        private boolean enabled = false;
        private String url = "jdbc:mysql://127.0.0.1:3306/linkgate?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        private String username = "linkgate";
        private String password = "linkgate";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Llm {

        private boolean enabled = false;
        private String baseUrl;
        private String apiKey = "";
        private String model = "gpt-4o-mini";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
