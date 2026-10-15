package com.foodie.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流规则配置。
 *
 * <p>按「规则名」定义令牌桶：容量 capacity + 补充周期 periodSeconds。
 * 业务侧用 {@code @RateLimit("login")} 引用，规则值集中在 yml 里调，不用改代码。
 */
@ConfigurationProperties(prefix = "app.ratelimit")
public record RateLimitProperties(Map<String, Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    /**
     * 取规则；未配置时给一个保守的默认值，
     * 避免因为漏配导致接口完全没有限流保护。
     */
    public Rule ruleOf(String name) {
        Rule rule = rules.get(name);
        if (rule == null) {
            return new Rule(20, 60);
        }
        return rule;
    }

    /**
     * @param capacity      桶容量，即周期内允许的最大请求数
     * @param periodSeconds 令牌补满一轮的秒数
     */
    public record Rule(int capacity, int periodSeconds) {
        public Rule {
            capacity = capacity <= 0 ? 20 : capacity;
            periodSeconds = periodSeconds <= 0 ? 60 : periodSeconds;
        }
    }
}
