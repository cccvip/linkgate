package com.linkgate.ai.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式补全请求（OpenAI 兼容）。
 *
 * @param model       模型名
 * @param messages    对话消息
 * @param temperature 采样温度
 */
public record LlmRequest(String model, List<LlmMessage> messages, double temperature) {

    /** 便捷构造：单条 user 提示。 */
    public static LlmRequest of(String model, String prompt) {
        return new LlmRequest(model, List.of(new LlmMessage("user", prompt)), 0.7);
    }

    /** 组装 OpenAI Chat Completions 请求体（含 {@code stream: true}）。 */
    public Map<String, Object> toWire() {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("model", model);
        wire.put("messages", messages);
        wire.put("temperature", temperature);
        wire.put("stream", true);
        return wire;
    }
}
