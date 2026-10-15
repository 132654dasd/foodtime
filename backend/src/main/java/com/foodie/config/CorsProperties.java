package com.foodie.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 跨域配置。
 *
 * <p>开发期前端跑在 5173，后端在 8080，需要放行；
 * 生产环境前后端同域，这个列表可以是空的。
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
