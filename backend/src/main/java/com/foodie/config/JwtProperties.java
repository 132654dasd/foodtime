package com.foodie.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置。
 *
 * @param issuer       签发者，解析时会强制校验，防止别处签的 token 混进来
 * @param secret       HS256 密钥（Base64），长度必须 ≥ 256 bit
 * @param accessTokenTtl 普通登录的令牌有效期
 * @param rememberMeTtl  「记住我」的令牌有效期
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        Duration accessTokenTtl,
        Duration rememberMeTtl) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    app.jwt.secret 未配置。
                    请通过环境变量 APP_JWT_SECRET 提供至少 32 字节的密钥：
                      PowerShell : $env:APP_JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))
                      Linux/macOS: export APP_JWT_SECRET=$(openssl rand -base64 48)
                    """);
        }
        if (secret.startsWith("${")) {
            // 占位符没被解析（典型场景：prod profile 下忘了导出环境变量）时，
            // Spring 会把字面量原样传进来。不特判的话，报错会变成让人一头雾水的
            // 「密钥太短（当前 17）」——而 17 恰好就是 "${APP_JWT_SECRET}" 的长度。
            throw new IllegalStateException(
                    "app.jwt.secret 仍是未解析的占位符：" + secret
                            + "。这通常意味着环境变量 APP_JWT_SECRET 没有设置成功。");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "foodie";
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            accessTokenTtl = Duration.ofHours(2);
        }
        if (rememberMeTtl == null || rememberMeTtl.isZero() || rememberMeTtl.isNegative()) {
            rememberMeTtl = Duration.ofDays(7);
        }
    }
}
