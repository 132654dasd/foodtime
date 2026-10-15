package com.foodie.common;

import org.slf4j.MDC;

/**
 * 链路追踪工具。
 *
 * <p>{@link TraceIdFilter} 在请求入口把 traceId 放进 MDC，
 * 日志格式里带上它，响应体里也回填一份，
 * 前端报错时能直接把 traceId 报给后端定位。
 */
public final class TraceIds {

    /** MDC 中的键名，同时用于日志 pattern 与响应体字段。 */
    public static final String MDC_KEY = "traceId";

    /** 允许客户端透传的请求头，便于跨服务串联。 */
    public static final String HEADER = "X-Trace-Id";

    private TraceIds() {
    }

    /**
     * 读取当前请求的 traceId。
     *
     * @return 当前 traceId；不在请求线程中时返回空串而不是 null，避免响应体出现 null 字段
     */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }
}
