package com.linkgate.netty.server;

import com.linkgate.netty.handler.AckHandler;
import com.linkgate.netty.handler.AiChatHandler;
import com.linkgate.netty.handler.AiResumeHandler;
import com.linkgate.netty.handler.ChatHandler;
import com.linkgate.netty.handler.GroupChatHandler;
import com.linkgate.netty.handler.GroupJoinHandler;
import com.linkgate.netty.handler.GroupLeaveHandler;
import com.linkgate.netty.handler.HeartbeatHandler;
import com.linkgate.netty.handler.LoginHandler;
import com.linkgate.netty.handler.MultiSyncHandler;
import com.linkgate.netty.handler.ResumeHandler;
import com.linkgate.netty.protocol.CommandDispatcher;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.protocol.WebSocketFrameDecoder;
import com.linkgate.netty.protocol.WebSocketFrameEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;

/**
 * 装配 WebSocket 连接的 pipeline：
 * HTTP 升级 → 文本帧编解码 → 空闲检测 → 连接限制 → 连接生命周期 → 命令分发。
 */
public class GatewayChannelInitializer extends ChannelInitializer<SocketChannel> {

    /** 读空闲阈值（秒）：超过即判定失联并关闭。 */
    static final int IDLE_READER_SECONDS = 60;
    /** 写空闲阈值（秒）：超过即主动下发心跳。 */
    static final int IDLE_WRITER_SECONDS = 30;
    /** AI 生成的模型名（可由外部配置覆盖）。 */
    static final String DEFAULT_LLM_MODEL = "gpt-4o-mini";

    private final GatewayComponents components;

    public GatewayChannelInitializer(GatewayComponents components) {
        this.components = components;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        initPipeline(ch.pipeline(), components);
    }

    /** 抽出静态方法，便于脱离真实连接做单测。 */
    static void initPipeline(ChannelPipeline pipeline, GatewayComponents components) {
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(65536));
        pipeline.addLast("wsProtocol", new WebSocketServerProtocolHandler("/ws"));
        pipeline.addLast("wsDecoder", new WebSocketFrameDecoder());
        pipeline.addLast("wsEncoder", new WebSocketFrameEncoder());
        pipeline.addLast("idleState",
                new IdleStateHandler(IDLE_READER_SECONDS, IDLE_WRITER_SECONDS, 0, TimeUnit.SECONDS));
        pipeline.addLast("idleHeartbeat", new IdleHeartbeatHandler());
        pipeline.addLast("connectionLimit", new ConnectionLimitHandler(components.connectionLimiter()));
        pipeline.addLast("lifecycle", new ConnectionLifecycleHandler(components));
        pipeline.addLast("dispatcher", newDispatcher(components));
    }

    private static CommandDispatcher newDispatcher(GatewayComponents c) {
        CommandDispatcher dispatcher = new CommandDispatcher()
                .register(MsgType.LOGIN, new LoginHandler(c.registry(), c.deliveryService(), c.reconnectPolicy(),
                        c.presence(), c.router(), c.sessionDao(), c.nodeId()))
                .register(MsgType.HEARTBEAT, new HeartbeatHandler(c.sessionManager(), c.registry()))
                .register(MsgType.CHAT, new ChatHandler(c.sessionManager(), c.registry(), c.deliveryService(),
                        c.messageJournal(), c.deduplicator(), c.ackTracker()))
                .register(MsgType.ACK, new AckHandler(c.sessionManager(), c.registry(), c.ackTracker()))
                .register(MsgType.RESUME, new ResumeHandler(c.sessionManager(), c.registry(), c.messageJournal(), c.messageSender()))
                .register(MsgType.GROUP_JOIN, new GroupJoinHandler(c.sessionManager(), c.registry(), c.groupFanout()))
                .register(MsgType.GROUP_LEAVE, new GroupLeaveHandler(c.sessionManager(), c.registry(), c.groupFanout()))
                .register(MsgType.GROUP_CHAT, new GroupChatHandler(c.sessionManager(), c.registry(), c.groupFanout(), c.deduplicator()))
                .register(MsgType.MULTI_SYNC, new MultiSyncHandler(c.sessionManager(), c.registry(), c.multiDeviceSync(), c.deduplicator()));
        if (c.aiStreamBridge() != null) {
            dispatcher.register(MsgType.AI_CHAT, new AiChatHandler(
                            c.sessionManager(), c.registry(), c.aiStreamBridge(), c.streamCheckpoints(), DEFAULT_LLM_MODEL))
                    .register(MsgType.AI_RESUME, new AiResumeHandler(
                            c.sessionManager(), c.registry(), c.streamCheckpoints()));
        }
        return dispatcher;
    }
}
