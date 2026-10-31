package com.linkgate.store.mysql;

import com.linkgate.core.message.Message;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * 消息元数据 DAO（MySQL）。
 */
public final class MessageDao {

    private final DataSource dataSource;

    public MessageDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 落库一条消息（按 message_id 幂等）。 */
    public void insert(Message message) {
        String sql = "INSERT INTO lg_message (message_id, from_user, to_user, seq, payload) "
                + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE seq = VALUES(seq), payload = VALUES(payload)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, message.messageId());
            statement.setString(2, message.fromUserId());
            statement.setString(3, message.toUserId());
            statement.setLong(4, message.seq());
            statement.setBytes(5, message.payload());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("insert message failed: " + message.messageId(), e);
        }
    }

    /** 取某收件人 {@code seq > seqExclusive} 的消息，按 seq 升序。 */
    public List<Message> findSince(String toUser, long seqExclusive) {
        String sql = "SELECT message_id, from_user, to_user, seq, payload FROM lg_message "
                + "WHERE to_user = ? AND seq > ? ORDER BY seq ASC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toUser);
            statement.setLong(2, seqExclusive);
            try (ResultSet rs = statement.executeQuery()) {
                List<Message> messages = new ArrayList<>();
                while (rs.next()) {
                    messages.add(new Message(
                            rs.getString("message_id"),
                            rs.getString("from_user"),
                            rs.getString("to_user"),
                            rs.getLong("seq"),
                            rs.getBytes("payload")));
                }
                return messages;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("query messages failed for " + toUser, e);
        }
    }

    public long countTo(String toUser) {
        String sql = "SELECT COUNT(*) FROM lg_message WHERE to_user = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toUser);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("count messages failed", e);
        }
    }

    /** 某收件人当前最大 seq（无消息返回 0）。 */
    public long latestSeq(String toUser) {
        String sql = "SELECT COALESCE(MAX(seq), 0) FROM lg_message WHERE to_user = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toUser);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("latest seq failed for " + toUser, e);
        }
    }
}
