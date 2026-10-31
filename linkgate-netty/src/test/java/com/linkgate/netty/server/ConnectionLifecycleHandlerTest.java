package com.linkgate.netty.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.linkgate.core.session.Session;
import com.linkgate.core.session.SessionState;
import com.linkgate.netty.session.ChannelSessionConnection;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class ConnectionLifecycleHandlerTest {

    @Test
    void registersOnActiveAndEntersGraceOnDisconnect() {
        GatewayComponents components = new GatewayComponents();
        EmbeddedChannel ch = new EmbeddedChannel(new ConnectionLifecycleHandler(components));

        assertEquals(1, components.registry().connectionCount(), "连接激活时应登记");

        Session session = components.sessionManager().create("s1", "u1", "d1", new ChannelSessionConnection(ch));
        session.transitionTo(SessionState.AUTHENTICATED);
        components.registry().bindSession(ch, "s1");

        ch.close();
        ch.runPendingTasks();

        assertEquals(0, components.registry().connectionCount(), "断连应移除连接");
        assertEquals(0, components.registry().boundSessionCount(), "断连应解绑会话");
        assertEquals(1, components.sessionManager().size(), "宽限期内会话保留");
        assertEquals(SessionState.RECONNECTING, components.sessionManager().require("s1").state());

        components.close();
        ch.finishAndReleaseAll();
    }
}
