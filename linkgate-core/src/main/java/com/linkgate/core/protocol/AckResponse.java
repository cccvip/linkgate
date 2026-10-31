package com.linkgate.core.protocol;

/**
 * 通用确认载荷。
 *
 * @param status 结果状态，如 {@code OK} / {@code DELIVERED} / {@code OFFLINE}
 */
public record AckResponse(String status) {
}
