package com.linkgate.ai.llm;

/**
 * 一条对话消息。
 *
 * @param role    角色：{@code system} / {@code user} / {@code assistant}
 * @param content 内容
 */
public record LlmMessage(String role, String content) {
}
