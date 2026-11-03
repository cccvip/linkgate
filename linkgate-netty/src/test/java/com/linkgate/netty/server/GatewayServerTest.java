package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class GatewayServerTest {

    @Test
    void rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> new GatewayServer(-1));
        assertThrows(IllegalArgumentException.class, () -> new GatewayServer(70000));
    }

    @Test
    @Timeout(20)
    void startsBindsAndAcceptsConnection() throws Exception {
        GatewayServer server = new GatewayServer(0);
        server.start();
        try {
            assertTrue(server.isRunning());
            assertTrue(server.port() > 0, "应绑定到系统分配的端口");

            assertThrows(IllegalStateException.class, server::start);

            EventLoopGroup group = new NioEventLoopGroup(1);
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group).channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                // 空 pipeline，仅验证 TCP 建连
                            }
                        });
                Channel channel = bootstrap.connect("127.0.0.1", server.port()).sync().channel();
                assertTrue(channel.isActive(), "客户端应成功建连");
                channel.close().sync();
            } finally {
                group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            }
        } finally {
            server.stop();
        }
        assertFalse(server.isRunning());
    }
}
