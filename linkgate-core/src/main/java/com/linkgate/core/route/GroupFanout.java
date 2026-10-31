package com.linkgate.core.route;

import com.linkgate.core.message.AckTracker;
import com.linkgate.core.message.DeliveryResult;
import com.linkgate.core.message.DeliveryService;
import com.linkgate.core.message.Message;
import com.linkgate.core.message.MessageJournal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群组扩散：维护群成员，并把一条消息扇出给群内所有成员。
 */
public final class GroupFanout {

    private final DeliveryService delivery;
    private final MessageJournal journal;
    private final AckTracker ackTracker;
    private final Map<String, Set<String>> members = new ConcurrentHashMap<>();

    public GroupFanout(DeliveryService delivery) {
        this(delivery, null, null);
    }

    public GroupFanout(DeliveryService delivery, MessageJournal journal, AckTracker ackTracker) {
        this.delivery = delivery;
        this.journal = journal;
        this.ackTracker = ackTracker;
    }

    public void join(String groupId, String userId) {
        members.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public void leave(String groupId, String userId) {
        Set<String> group = members.get(groupId);
        if (group != null) {
            group.remove(userId);
        }
    }

    public Set<String> members(String groupId) {
        Set<String> group = members.get(groupId);
        return group == null ? Set.of() : Set.copyOf(group);
    }

    /**
     * 把消息扇出给群内每个成员（投递到其全部在线会话），返回成功投递的会话数。
     */
    public int fanout(String groupId, Message template) {
        int delivered = 0;
        for (String member : members(groupId)) {
            Message perMember = new Message(
                    template.messageId() + '@' + member,
                    template.fromUserId(),
                    member,
                    template.seq(),
                    template.payload());
            delivered += delivery.deliver(perMember).deliveredCount();
        }
        return delivered;
    }

    /**
     * 群发：为每个成员分配服务端 seq、落日志、投递，并在成功投递时记账等 ACK。
     *
     * @return 成功投递的会话数
     */
    public int broadcast(String groupId, String fromUserId, String messageId, byte[] payload) {
        int delivered = 0;
        for (String member : members(groupId)) {
            String perMemberId = messageId + '@' + member;
            Message message = (journal != null)
                    ? journal.append(perMemberId, fromUserId, member, payload)
                    : new Message(perMemberId, fromUserId, member, 0L, payload);
            DeliveryResult result = delivery.deliver(message);
            if (result.delivered() && ackTracker != null) {
                ackTracker.track(message);
            }
            delivered += result.deliveredCount();
        }
        return delivered;
    }
}
