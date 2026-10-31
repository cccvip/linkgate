package com.linkgate.store.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * 会话元数据 DAO（MySQL）：记录会话快照，便于审计 / 跨节点查询。
 */
public final class SessionDao {

    private final DataSource dataSource;

    public SessionDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(SessionRow row) {
        String sql = "INSERT INTO lg_session (session_id, user_id, device_id, state) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), device_id = VALUES(device_id), "
                + "state = VALUES(state)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, row.sessionId());
            statement.setString(2, row.userId());
            statement.setString(3, row.deviceId());
            statement.setString(4, row.state());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("upsert session failed: " + row.sessionId(), e);
        }
    }

    public Optional<SessionRow> find(String sessionId) {
        String sql = "SELECT session_id, user_id, device_id, state FROM lg_session WHERE session_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SessionRow(
                        rs.getString("session_id"),
                        rs.getString("user_id"),
                        rs.getString("device_id"),
                        rs.getString("state")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("find session failed: " + sessionId, e);
        }
    }

    public void delete(String sessionId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM lg_session WHERE session_id = ?")) {
            statement.setString(1, sessionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("delete session failed: " + sessionId, e);
        }
    }

    public long countForUser(String userId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM lg_session WHERE user_id = ?")) {
            statement.setString(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("count sessions failed", e);
        }
    }
}
