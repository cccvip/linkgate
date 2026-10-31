package com.linkgate.netty.client;

import com.linkgate.netty.protocol.Frame;
import com.linkgate.netty.protocol.GatewayMessageDecoder;
import com.linkgate.netty.protocol.GatewayMessageEncoder;
import com.linkgate.netty.protocol.ProtocolDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 简易可脚本化客户端：连接网关、发送 {@link Frame}、阻塞等待回帧。
 *
 * <p>用于集成测试与手工调试；压测用客户端见 {@code linkgate-bench}。</p>
 */
public final class TestClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final NioEventLoopGroup group = new NioEventLoopGroup(1);
    private final BlockingQueue<Frame> inbound = new LinkedBlockingQueue<>();

    private Channel channel;

    public TestClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public TestClient connect() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast("frameDecoder", new ProtocolDecoder());
                        ch.pipeline().addLast("messageDecoder", new GatewayMessageDecoder());
                        ch.pipeline().addLast("messageEncoder", new GatewayMessageEncoder());
                        ch.pipeline().addLast("capture", new SimpleChannelInboundHandler<Frame>() {
                            @Override
                            protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, Frame frame) {
                                inbound.add(frame);
                            }
                        });
                    }
                });
        channel = bootstrap.connect(host, port).sync().channel();
        return this;
    }

    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    public void send(Frame frame) {
        channel.writeAndFlush(frame);
    }

    /** 阻塞等待下一帧，超时返回 {@code null}。 */
    public Frame awaitFrame(long timeoutMillis) throws InterruptedException {
        return inbound.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
        group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    }
}
