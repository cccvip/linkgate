package com.linkgate.bench;

import com.linkgate.core.codec.JsonCodec;
import com.linkgate.core.protocol.LoginRequest;
import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.MsgType;
import com.linkgate.netty.protocol.WebSocketFrameDecoder;
import com.linkgate.netty.protocol.WebSocketFrameEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自研 Netty 压测客户端（WebSocket）：可建大量长连接，并按场景（空闲 / 活跃）施压。
 */
public final class BenchClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final int eventLoopThreads;

    private final List<Channel> channels = new CopyOnWriteArrayList<>();
    private final Map<Channel, PingPongHandler> handlers = new ConcurrentHashMap<>();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicInteger idSequence = new AtomicInteger();
    private final LatencyRecorder latency = new LatencyRecorder(1 << 21);

    private volatile boolean running;
    private volatile BenchScenario scenario = BenchScenario.IDLE;
    private EventLoopGroup group;

    public BenchClient(String host, int port) {
        this(host, port, Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public BenchClient(String host, int port, int eventLoopThreads) {
        this.host = host;
        this.port = port;
        this.eventLoopThreads = eventLoopThreads;
    }

    /** 建立 {@code count} 条 WebSocket 连接（每条以唯一 userId 登录）。 */
    public void connect(int count) throws InterruptedException {
        group = new NioEventLoopGroup(eventLoopThreads);
        URI uri = URI.create("ws://" + host + ":" + port + "/ws");
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        PingPongHandler handler = new PingPongHandler(
                                ch, "bench-" + idSequence.getAndIncrement(),
                                latency, sent, received, () -> running, () -> scenario);
                        handlers.put(ch, handler);
                        ch.closeFuture().addListener(future -> handlers.remove(ch));
                        ch.pipeline().addLast("httpCodec", new HttpClientCodec());
                        ch.pipeline().addLast("httpAggregator", new HttpObjectAggregator(65536));
                        ch.pipeline().addLast("wsProtocol", new WebSocketClientProtocolHandler(
                                uri, WebSocketVersion.V13, null, true, EmptyHttpHeaders.INSTANCE, 65536));
                        ch.pipeline().addLast("wsDecoder", new WebSocketFrameDecoder());
                        ch.pipeline().addLast("wsEncoder", new WebSocketFrameEncoder());
                        ch.pipeline().addLast("bench", handler);
                    }
                });

        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    channels.add(future.channel());
                }
                latch.countDown();
            });
        }
        latch.await(120, TimeUnit.SECONDS);
    }

    /** 按场景施压固定时长。 */
    public BenchResult run(BenchScenario scenario, long durationMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        this.scenario = scenario;
        running = true;
        if (scenario == BenchScenario.ACTIVE) {
            for (PingPongHandler handler : handlers.values()) {
                handler.resume();
            }
        }
        Thread.sleep(durationMillis);
        running = false;
        long elapsed = Math.max(1, System.currentTimeMillis() - start);

        long receivedCount = received.get();
        return new BenchResult(
                connectedCount(),
                scenario,
                elapsed,
                sent.get(),
                receivedCount,
                receivedCount * 1000.0 / elapsed,
                latency.percentileMillis(0.50),
                latency.percentileMillis(0.99));
    }

    public int connectedCount() {
        return channels.size();
    }

    @Override
    public void close() {
        running = false;
        for (Channel channel : channels) {
            channel.close();
        }
        channels.clear();
        handlers.clear();
        if (group != null) {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            group = null;
        }
    }

    /** 登录后按场景收发：ACTIVE 持续 ping-pong，IDLE 只保持连接。 */
    private static final class PingPongHandler extends ChannelInboundHandlerAdapter {

        private final Channel channel;
        private final String userId;
        private final LatencyRecorder latency;
        private final AtomicLong sent;
        private final AtomicLong received;
        private final java.util.function.BooleanSupplier running;
        private final java.util.function.Supplier<BenchScenario> scenario;

        private volatile boolean loggedIn;
        private volatile long lastSendNanos;

        private PingPongHandler(Channel channel, String userId, LatencyRecorder latency,
                                AtomicLong sent, AtomicLong received, java.util.function.BooleanSupplier running,
                                java.util.function.Supplier<BenchScenario> scenario) {
            this.channel = channel;
            this.userId = userId;
            this.latency = latency;
            this.sent = sent;
            this.received = received;
            this.running = running;
            this.scenario = scenario;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt == ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                ctx.writeAndFlush(Frame.of(MsgType.LOGIN.code(), 1L,
                        JsonCodec.encode(new LoginRequest(userId, "bench"))));
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            Frame frame = (Frame) msg;
            if (frame.type() == MsgType.LOGIN_ACK.code()) {
                loggedIn = true;
                pingIfRunning();
            } else if (frame.type() == MsgType.HEARTBEAT_ACK.code()) {
                latency.record(System.nanoTime() - lastSendNanos);
                received.incrementAndGet();
                pingIfRunning();
            }
        }

        /** 由 {@link #run} 触发：场景开始时踢动 ping-pong。 */
        void resume() {
            pingIfRunning();
        }

        private void pingIfRunning() {
            if (!running.getAsBoolean() || scenario.get() != BenchScenario.ACTIVE
                    || !loggedIn || !channel.isActive()) {
                return;
            }
            lastSendNanos = System.nanoTime();
            sent.incrementAndGet();
            channel.writeAndFlush(Frame.of(MsgType.HEARTBEAT.code(), 2L));
        }
    }
}
