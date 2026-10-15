package com.foodie.common;

/**
 * 全局错误码。
 *
 * <p>约定：0 表示成功，其余复用 HTTP 语义，便于前端统一拦截处理。
 */
public enum ErrorCode {

    OK(0, "ok"),

    BAD_REQUEST(400, "请求参数有误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源状态冲突"),

    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试"),
    PAYLOAD_TOO_LARGE(413, "上传内容超过大小限制"),

    INTERNAL(500, "服务器开小差了，请稍后再试");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
