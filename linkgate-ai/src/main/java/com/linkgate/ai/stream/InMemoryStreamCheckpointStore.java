package com.linkgate.ai.stream;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版流式检查点存储：每条流保留有界分片，超出容量丢弃最旧（绝对 index 不回退）。
 */
public final class InMemoryStreamCheckpointStore implements StreamCheckpointStore {

    private final int maxTokensPerStream;
    private final Map<String, Entry> streams = new ConcurrentHashMap<>();

    public InMemoryStreamCheckpointStore(int maxTokensPerStream) {
        if (maxTokensPerStream <= 0) {
            throw new IllegalArgumentException("maxTokensPerStream must be > 0");
        }
        this.maxTokensPerStream = maxTokensPerStream;
    }

    @Override
    public int append(String streamId, String userId, String token) {
        return streams.computeIfAbsent(streamId, id -> new Entry(userId)).append(token);
    }

    @Override
    public List<String> since(String streamId, int fromIndexExclusive) {
        Entry entry = streams.get(streamId);
        return entry == null ? List.of() : entry.since(fromIndexExclusive);
    }

    @Override
    public void complete(String streamId, int totalTokens) {
        Entry entry = streams.get(streamId);
        if (entry != null) {
            entry.complete(totalTokens);
        }
    }

    @Override
    public Optional<StreamCheckpoint> find(String streamId) {
        Entry entry = streams.get(streamId);
        return entry == null ? Optional.empty() : Optional.of(entry.checkpoint(streamId));
    }

    @Override
    public void remove(String streamId) {
        streams.remove(streamId);
    }

    private final class Entry {

        private final String userId;
        private final List<String> tokens = new ArrayList<>();
        private long baseIndex;
        private long nextIndex;
        private boolean completed;
        private int totalTokens = -1;

        private Entry(String userId) {
            this.userId = userId;
        }

        private synchronized int append(String token) {
            if (tokens.size() >= maxTokensPerStream) {
                tokens.remove(0);
                baseIndex++;
            }
            long index = nextIndex++;
            tokens.add(token);
            return (int) index;
        }

        private synchronized List<String> since(int fromIndexExclusive) {
            long from = fromIndexExclusive + 1L;
            int start = (int) Math.max(0, from - baseIndex);
            if (start >= tokens.size()) {
                return List.of();
            }
            return new ArrayList<>(tokens.subList(start, tokens.size()));
        }

        private synchronized void complete(int total) {
            this.completed = true;
            this.totalTokens = total;
        }

        private synchronized StreamCheckpoint checkpoint(String streamId) {
            return new StreamCheckpoint(streamId, userId, (int) (nextIndex - 1), totalTokens, completed, Instant.now());
        }
    }
}
