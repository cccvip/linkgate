package com.linkgate.netty.server;

import com.linkgate.ai.llm.LlmClient;
import com.linkgate.ai.llm.TokenStreamProducer;
import com.linkgate.ai.stream.AiStreamBridge;
import com.linkgate.ai.stream.InMemoryStreamCheckpointStore;
import com.linkgate.ai.stream.StreamCheckpointStore;
import com.linkgate.core.message.AckTracker;
import com.linkgate.core.message.Deduplicator;
import com.linkgate.core.message.DeliveryService;
import com.linkgate.core.message.InMemoryMessageLog;
import com.linkgate.core.message.InMemoryOfflineStore;
import com.linkgate.core.message.MessageJournal;
import com.linkgate.core.message.MessageLog;
import com.linkgate.core.message.OfflineStore;
import com.linkgate.core.message.RetryQueue;
import com.linkgate.core.message.Sequencer;
import com.linkgate.core.route.GroupFanout;
import com.linkgate.core.route.MultiDeviceSync;
import com.linkgate.core.session.ReconnectPolicy;
import com.linkgate.core.session.SessionManager;
import com.linkgate.netty.session.ChannelMessageSender;
import com.linkgate.store.mysql.MessageDao;
import com.linkgate.store.mysql.MysqlMessageLog;
import com.linkgate.store.mysql.MysqlStore;
import com.linkgate.store.mysql.SessionDao;
import com.linkgate.store.redis.ConnectionRouter;
import com.linkgate.store.redis.PresenceService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网关共享组件的组装对象：会话/重连、连接注册与限制、可靠性、消息日志/续传、投递、AI 桥接，
 * 以及可选的 Redis（在线/路由）与 MySQL（持久化）。
 *
 * <p>作为组装根；持有定时器/线程池，关闭时需 {@link #close()}。</p>
 */
public final class GatewayComponents implements AutoCloseable {

    public static final int DEFAULT_MAX_CONNECTIONS = 100_000;
    public static final double DEFAULT_CONNECTIONS_PER_SECOND = 20_000;
    public static final int DEFAULT_OFFLINE_MAX_PER_USER = 1000;
    public static final int DEFAULT_MESSAGE_LOG_PER_STREAM = 1000;
    public static final int DEFAULT_STREAM_CHECKPOINT_PER_STREAM = 4096;
    public static final int DEFAULT_DEDUP_CAPACITY = 10_000;
    public static final long DEFAULT_ACK_TIMEOUT_MILLIS = 5_000;
    public static final int DEFAULT_ACK_MAX_ATTEMPTS = 3;
    public static final int DEFAULT_AI_THREADS = 4;
    public static final String DEFAULT_NODE_ID = "node-local";
    public static final long DEFAULT_RECONNECT_GRACE_MILLIS = ReconnectPolicy.DEFAULT_GRACE_MILLIS;

    private final SessionManager sessionManager;
    private final ConnectionRegistry registry;
    private final ConnectionLimiter connectionLimiter;
    private final OfflineStore offlineStore;
    private final ChannelMessageSender messageSender;
    private final DeliveryService deliveryService;
    private final Sequencer sequencer;
    private final RetryQueue retryQueue;
    private final Deduplicator deduplicator;
    private final AckTracker ackTracker;
    private final MessageLog messageLog;
    private final MessageJournal messageJournal;
    private final GroupFanout groupFanout;
    private final MultiDeviceSync multiDeviceSync;
    private final ReconnectPolicy reconnectPolicy;

    private final StreamCheckpointStore streamCheckpoints;
    private final AiStreamBridge aiStreamBridge;
    private final ExecutorService aiExecutor;

    private final PresenceService presence;
    private final ConnectionRouter router;
    private final SessionDao sessionDao;
    private final String nodeId;

    public GatewayComponents() {
        this(new SessionManager(), new ConnectionRegistry(), defaultLimiter(), null, null, null, null, DEFAULT_NODE_ID);
    }

    public GatewayComponents(SessionManager sessionManager, ConnectionRegistry registry) {
        this(sessionManager, registry, defaultLimiter(), null, null, null, null, DEFAULT_NODE_ID);
    }

    public GatewayComponents(SessionManager sessionManager, ConnectionRegistry registry,
                             ConnectionLimiter connectionLimiter) {
        this(sessionManager, registry, connectionLimiter, null, null, null, null, DEFAULT_NODE_ID);
    }

    public GatewayComponents(SessionManager sessionManager, ConnectionRegistry registry,
                             ConnectionLimiter connectionLimiter, LlmClient llmClient) {
        this(sessionManager, registry, connectionLimiter, llmClient, null, null, null, DEFAULT_NODE_ID);
    }

    /**
     * @param llmClient LLM 客户端（可空）
     * @param presence  Redis 在线状态（可空）
     * @param router    Redis 连接路由（可空）
     * @param mysql     MySQL 存储（可空；给了则用 MySQL 持久化消息日志与会话快照）
     */
    public GatewayComponents(SessionManager sessionManager, ConnectionRegistry registry,
                             ConnectionLimiter connectionLimiter, LlmClient llmClient,
                             PresenceService presence, ConnectionRouter router, MysqlStore mysql, String nodeId) {
        this.sessionManager = sessionManager;
        this.registry = registry;
        this.connectionLimiter = connectionLimiter;
        this.offlineStore = new InMemoryOfflineStore(DEFAULT_OFFLINE_MAX_PER_USER);
        this.messageSender = new ChannelMessageSender(registry);
        this.deliveryService = new DeliveryService(sessionManager, messageSender, offlineStore);
        this.sequencer = new Sequencer();
        this.retryQueue = new RetryQueue();
        this.deduplicator = new Deduplicator(DEFAULT_DEDUP_CAPACITY);
        this.ackTracker = new AckTracker(retryQueue, DEFAULT_ACK_TIMEOUT_MILLIS, DEFAULT_ACK_MAX_ATTEMPTS,
                message -> deliveryService.deliver(message));
        this.messageLog = (mysql == null)
                ? new InMemoryMessageLog(DEFAULT_MESSAGE_LOG_PER_STREAM)
                : new MysqlMessageLog(mysql.messages());
        this.messageJournal = new MessageJournal(sequencer, messageLog);
        this.groupFanout = new GroupFanout(deliveryService, messageJournal, ackTracker);
        this.multiDeviceSync = new MultiDeviceSync(sessionManager, messageSender, messageJournal);
        this.reconnectPolicy = new ReconnectPolicy(sessionManager, retryQueue, DEFAULT_RECONNECT_GRACE_MILLIS);
        this.streamCheckpoints = new InMemoryStreamCheckpointStore(DEFAULT_STREAM_CHECKPOINT_PER_STREAM);

        this.presence = presence;
        this.router = router;
        this.sessionDao = mysql == null ? null : mysql.sessions();
        this.nodeId = nodeId;

        if (llmClient != null) {
            this.aiExecutor = Executors.newFixedThreadPool(DEFAULT_AI_THREADS, runnable -> {
                Thread thread = new Thread(runnable, "linkgate-ai");
                thread.setDaemon(true);
                return thread;
            });
            this.aiStreamBridge = new AiStreamBridge(new TokenStreamProducer(llmClient), aiExecutor);
        } else {
            this.aiExecutor = null;
            this.aiStreamBridge = null;
        }
    }

    private static ConnectionLimiter defaultLimiter() {
        return new ConnectionLimiter(DEFAULT_MAX_CONNECTIONS, DEFAULT_CONNECTIONS_PER_SECOND);
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public ConnectionRegistry registry() {
        return registry;
    }

    public ConnectionLimiter connectionLimiter() {
        return connectionLimiter;
    }

    public OfflineStore offlineStore() {
        return offlineStore;
    }

    public ChannelMessageSender messageSender() {
        return messageSender;
    }

    public DeliveryService deliveryService() {
        return deliveryService;
    }

    public MessageJournal messageJournal() {
        return messageJournal;
    }

    public GroupFanout groupFanout() {
        return groupFanout;
    }

    public MultiDeviceSync multiDeviceSync() {
        return multiDeviceSync;
    }

    public RetryQueue retryQueue() {
        return retryQueue;
    }

    public Deduplicator deduplicator() {
        return deduplicator;
    }

    public AckTracker ackTracker() {
        return ackTracker;
    }

    public ReconnectPolicy reconnectPolicy() {
        return reconnectPolicy;
    }

    public StreamCheckpointStore streamCheckpoints() {
        return streamCheckpoints;
    }

    public AiStreamBridge aiStreamBridge() {
        return aiStreamBridge;
    }

    public PresenceService presence() {
        return presence;
    }

    public ConnectionRouter router() {
        return router;
    }

    public SessionDao sessionDao() {
        return sessionDao;
    }

    public String nodeId() {
        return nodeId;
    }

    @Override
    public void close() {
        retryQueue.close();
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
    }
}
