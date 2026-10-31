package com.linkgate.core.protocol;

/**
 * 登录成功响应载荷。
 */
public record LoginResponse(String sessionId, String userId, String deviceId) {
}
