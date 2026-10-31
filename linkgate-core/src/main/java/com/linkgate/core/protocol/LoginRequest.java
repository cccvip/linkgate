package com.linkgate.core.protocol;

/**
 * 登录请求载荷。
 *
 * @param userId   用户标识（必填）
 * @param deviceId 设备标识（可选，缺省由服务端补默认值）
 */
public record LoginRequest(String userId, String deviceId) {
}
