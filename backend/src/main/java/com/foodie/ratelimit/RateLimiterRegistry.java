package com.foodie.ratelimit;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.foodie.config.RateLimitProperties;

import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * 令牌桶仓库。
 *
 * <p>用 Caffeine 承载桶而不是裸的 {@code ConcurrentHashMap}：
 * 每个用户/IP 一个桶，如果只放不删，长期运行下来就是一次内存泄漏
 * （而且是很隐蔽的那种——只有在用户量上来之后才暴露）。
 * 这里设了上限与空闲过期，桶会被自动回收。
 */
@Component
public class RateLimiterRegistry {

    private static final int MAX_BUCKETS = 10_000;
    private static final Duration IDLE_EXPIRY = Duration.ofHours(1);

    private final RateLimitProperties properties;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_BUCKETS)
            .expireAfterAccess(IDLE_EXPIRY)
            .build();

    public RateLimiterRegistry(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * 尝试消费一个令牌。
     *
     * @return true 表示放行，false 表示已触发限流
     */
    public boolean tryConsume(String ruleName, String identity) {
        RateLimitProperties.Rule rule = properties.ruleOf(ruleName);
        Bucket bucket = buckets.get(ruleName + ":" + identity, key -> newBucket(rule));
        return bucket != null && bucket.tryConsume(1);
    }

    private Bucket newBucket(RateLimitProperties.Rule rule) {
        // refillGreedy：把周期均摊成平滑补充，而不是「整点一次性回满」。
        // 后者会出现「前 1 秒用光、之后 59 秒完全不可用」的锯齿，体验很差。
        Bandwidth limit = Bandwidth.builder()
                .capacity(rule.capacity())
                .refillGreedy(rule.capacity(), Duration.ofSeconds(rule.periodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
