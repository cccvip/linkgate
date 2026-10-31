package com.linkgate.core.testing;

import com.linkgate.core.message.Message;
import com.linkgate.core.message.MessageSender;
import com.linkgate.core.session.Session;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 测试用 MessageSender：记录被投递的 sessionId。 */
public final class RecordingSender implements MessageSender {

    private final List<String> sessionIds = Collections.synchronizedList(new ArrayList<>());

    @Override
    public boolean send(Session session, Message message) {
        sessionIds.add(session.sessionId());
        return true;
    }

    public List<String> sessionIds() {
        return List.copyOf(sessionIds);
    }
}
