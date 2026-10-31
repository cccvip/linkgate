package com.linkgate.store.mysql;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.linkgate.core.message.Message;
import com.linkgate.store.TestEnv;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MysqlStoreTest {

    private MysqlStore store;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestEnv.mysqlAvailable(), "本地无 MySQL(3306)，跳过");
        store = new MysqlStore(TestEnv.MYSQL_URL, TestEnv.MYSQL_USER, TestEnv.MYSQL_PASSWORD);
        store.initSchema();
        try (Connection connection = store.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM lg_message");
            statement.executeUpdate("DELETE FROM lg_session");
        }
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void messageRoundTripAndSince() {
        MessageDao dao = store.messages();
        dao.insert(new Message("m1", "a", "b", 1L, new byte[] {1, 2}));
        dao.insert(new Message("m2", "a", "b", 2L, new byte[] {3}));
        dao.insert(new Message("m3", "c", "b", 3L, new byte[] {4}));

        assertEquals(3, dao.countTo("b"));

        List<Message> since = dao.findSince("b", 1L);
        assertEquals(List.of("m2", "m3"), since.stream().map(Message::messageId).toList());
        assertArrayEquals(new byte[] {3}, since.get(0).payload());
    }

    @Test
    void messageInsertIsIdempotent() {
        MessageDao dao = store.messages();
        Message message = new Message("m1", "a", "b", 1L, new byte[] {1});

        dao.insert(message);
        dao.insert(message);

        assertEquals(1, dao.countTo("b"));
    }

    @Test
    void sessionUpsertFindDelete() {
        SessionDao dao = store.sessions();

        dao.upsert(new SessionRow("s1", "u1", "d1", "AUTHENTICATED"));
        assertEquals("AUTHENTICATED", dao.find("s1").orElseThrow().state());

        dao.upsert(new SessionRow("s1", "u1", "d1", "RECONNECTING"));
        assertEquals("RECONNECTING", dao.find("s1").orElseThrow().state());
        assertEquals(1, dao.countForUser("u1"));

        dao.delete("s1");
        assertTrue(dao.find("s1").isEmpty());
    }
}
