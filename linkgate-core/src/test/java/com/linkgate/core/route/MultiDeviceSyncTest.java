package com.linkgate.core.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkgate.core.message.Message;
import com.linkgate.core.session.SessionManager;
import com.linkgate.core.testing.FakeConnection;
import com.linkgate.core.testing.RecordingSender;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultiDeviceSyncTest {

    @Test
    void syncsToOtherDevicesOnly() {
        SessionManager sessionManager = new SessionManager();
        sessionManager.create("s1", "u1", "d1", new FakeConnection("c1"));
        sessionManager.create("s2", "u1", "d2", new FakeConnection("c2"));
        RecordingSender sender = new RecordingSender();
        MultiDeviceSync sync = new MultiDeviceSync(sessionManager, sender);

        int delivered = sync.syncToOtherDevices("u1", "s1",
                new Message("m1", "u1", "u1", 1L, new byte[] {1}));

        assertEquals(1, delivered);
        assertEquals(List.of("s2"), sender.sessionIds(), "只同步给其它设备");
    }

    @Test
    void noOtherDevicesMeansNoDelivery() {
        SessionManager sessionManager = new SessionManager();
        sessionManager.create("s1", "u1", "d1", new FakeConnection("c1"));
        RecordingSender sender = new RecordingSender();
        MultiDeviceSync sync = new MultiDeviceSync(sessionManager, sender);

        int delivered = sync.syncToOtherDevices("u1", "s1",
                new Message("m1", "u1", "u1", 1L, new byte[] {1}));

        assertEquals(0, delivered);
        assertTrue(sender.sessionIds().isEmpty());
    }
}
