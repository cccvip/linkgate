package com.linkgate.core.testing;

import com.linkgate.core.session.SessionConnection;

/** 测试用连接句柄。 */
public final class FakeConnection implements SessionConnection {

    private final String id;
    private boolean active = true;
    private boolean closed;

    public FakeConnection(String id) {
        this.id = id;
    }

    @Override
    public String connectionId() {
        return id;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void close() {
        this.closed = true;
        this.active = false;
    }

    public boolean isClosed() {
        return closed;
    }
}
