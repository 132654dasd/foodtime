package com.foodie.common;

/**
 * 统一响应体：{@code { code, message, data, traceId }}。
 *
 * <p>HTTP 状态码仍由 Spring 按语义返回（403 就是 403），
 * 这里的 {@code code} 是给前端做细粒度分支用的业务码。
 *
 * @param code    业务码，0 为成功
 * @param message 人类可读的提示，前端可直接展示
 * @param data    业务数据，失败时为 null
 * @param traceId 链路 ID，便于对照服务端日志
 */
public record Result<T>(int code, String message, T data, String traceId) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.OK.code(), ErrorCode.OK.defaultMessage(), data, TraceIds.current());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null, TraceIds.current());
    }
}
