package com.linkgate.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * 可靠性场景测试：注入丢包 / 重复 / 乱序，验证「不丢、不乱、不重」。
 */
class ReliabilityScenarioTest {

    private static Message message(String id, long seq) {
        return new Message(id, "alice", "bob", seq, new byte[] {1});
    }

    /** 不丢：首次投递丢包 → ACK 超时 → 重传 → 最终送达并销账。 */
    @Test
    void noLoss_lostPacketIsRetransmittedUntilAcknowledged() throws InterruptedException {
        try (RetryQueue queue = new RetryQueue()) {
            AtomicInteger attempts = new AtomicInteger();
            List<Message> received = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch delivered = new CountDownLatch(1);
            AckTracker[] tracker = new AckTracker[1];

            Consumer<Message> network = m -> {
                if (attempts.incrementAndGet() >= 2) {   // 首次丢包，第二次才送达
                    received.add(m);
                    delivered.countDown();
                    tracker[0].acknowledge(m.messageId());
                }
            };
            tracker[0] = new AckTracker(queue, 50, network);  // 超时 → 重传

            Message m1 = message("m1", 1);
            tracker[0].track(m1);
            network.accept(m1);                               // 首次投递 → 丢

            assertTrue(delivered.await(2, TimeUnit.SECONDS), "丢包应触发重传并最终送达");
            assertEquals(List.of("m1"), received.stream().map(Message::messageId).toList());
            assertEquals(0, tracker[0].pendingCount(), "送达后应销账");
        }
    }

    /** 不重：同一条消息重复到达，去重后只投递一次。 */
    @Test
    void noDuplicates_duplicateDeliveryYieldsOneMessage() {
        Deduplicator dedup = new Deduplicator(100);
        List<Message> received = new ArrayList<>();
        Message m1 = message("m1", 1);

        for (int i = 0; i < 3; i++) {
            if (dedup.firstSeen(m1.messageId())) {
                received.add(m1);
            }
        }

        assertEquals(1, received.size(), "重复消息应被去重");
    }

    /** 不乱：乱序到达后按服务端 seq 重排即为原始顺序。 */
    @Test
    void noReorder_outOfOrderDeliveryIsOrderedBySeq() {
        Sequencer sequencer = new Sequencer();
        String inbox = "inbox:bob";
        Message m1 = message("m1", sequencer.next(inbox));
        Message m2 = message("m2", sequencer.next(inbox));
        Message m3 = message("m3", sequencer.next(inbox));

        List<Message> arrived = new ArrayList<>(List.of(m3, m1, m2));  // 乱序到达
        arrived.sort(Comparator.comparingLong(Message::seq));

        assertEquals(List.of("m1", "m2", "m3"), arrived.stream().map(Message::messageId).toList());
        assertEquals(List.of(1L, 2L, 3L), arrived.stream().map(Message::seq).toList());
    }
}
