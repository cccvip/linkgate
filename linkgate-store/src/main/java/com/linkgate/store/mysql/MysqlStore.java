package com.linkgate.store.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MySQL 存储入口：持有 Hikari 连接池，负责建表与提供 DAO。
 */
public final class MysqlStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MysqlStore.class);
    private static final String SCHEMA_RESOURCE = "/schema/mysql.sql";

    private final HikariDataSource dataSource;

    public MysqlStore(String jdbcUrl, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(8);
        config.setPoolName("linkgate-mysql");
        this.dataSource = new HikariDataSource(config);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public MessageDao messages() {
        return new MessageDao(dataSource);
    }

    public SessionDao sessions() {
        return new SessionDao(dataSource);
    }

    /** 执行 {@code /schema/mysql.sql} 建表（幂等）。 */
    public void initSchema() throws SQLException {
        String sql = readSchema();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : sql.split(";")) {
                if (!ddl.isBlank()) {
                    statement.execute(ddl.trim());
                }
            }
        }
        log.info("mysql schema initialized");
    }

    private static String readSchema() {
        try (InputStream in = MysqlStore.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + SCHEMA_RESOURCE, e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
