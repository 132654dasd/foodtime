package com.foodie.config;

import java.time.Duration;
import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置（Caffeine，进程内）。
 *
 * <p>本机没有 Redis，因此选择 Caffeine：
 * <ul>
 *   <li>零外部依赖，{@code mvnw spring-boot:run} 直接可用；</li>
 *   <li>命中延迟在纳秒级，比一次网络往返快几个数量级；</li>
 *   <li>代码层用的是 Spring Cache 抽象，将来换 Redis 只需换 CacheManager，
 *       业务代码里的 {@code @Cacheable} 一行都不用改。</li>
 * </ul>
 *
 * <p>每个缓存单独设 TTL，而不是全局一刀切：
 * 字典几乎不变可以放 1 小时，菜谱详情会被人改所以只放 10 分钟。
 */
@Configuration
public class CacheConfig {

    /** 菜谱详情：读多写少，写时精确失效。 */
    public static final String RECIPE_DETAIL = "recipeDetail";

    /** 菜系/分类/难度/标签等字典，几乎不变。 */
    public static final String RECIPE_META = "recipeMeta";

    /** 热门榜：短 TTL，允许短暂陈旧。 */
    public static final String HOT_RECIPES = "hotRecipes";

    /** 忌口关键词 → 被排除的菜谱 id 集合。 */
    public static final String AVOID_BLOCKED = "avoidBlocked";

    /** 用户认证状态：短 TTL，让「禁用账号」最快 5 分钟生效。 */
    public static final String USER_AUTH = "userAuth";

    /**
     * 食材价目字典。
     *
     * <p>与 {@link #RECIPE_META} 的区别：价目会被管理员随时改，
     * 所以 TTL 不能给到 1 小时；真正保证一致性的手段是改价时整体失效，
     * 这里的 TTL 只是兜底（比如直接改数据库时也能在 10 分钟内生效）。
     */
    public static final String PRICE_CATALOG = "ingredientPrice";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build(RECIPE_DETAIL, 2_000, Duration.ofMinutes(10)),
                build(RECIPE_META, 32, Duration.ofHours(1)),
                build(HOT_RECIPES, 16, Duration.ofMinutes(5)),
                build(AVOID_BLOCKED, 500, Duration.ofMinutes(30)),
                build(USER_AUTH, 5_000, Duration.ofMinutes(5)),
                // 价目只有一份全量列表，容量给 4 就够（'enabled' 一个 key + 余量）
                build(PRICE_CATALOG, 4, Duration.ofMinutes(10))));
        return manager;
    }

    private Cache build(String name, long maximumSize, Duration ttl) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                // 打开统计，Micrometer 会把命中率暴露到 /actuator/metrics/cache.gets
                .recordStats()
                .build());
    }
}
