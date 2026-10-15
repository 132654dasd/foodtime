package com.foodie.recipe;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 浏览量内存缓冲。
 *
 * <p>问题：菜谱详情是最高频的读接口，如果每次访问都 {@code UPDATE recipe SET view_count = view_count + 1}，
 * 数据库就要承受与页面浏览等量的写压力，热点菜谱那一行还会成为行锁争用点。
 *
 * <p>手段：在内存里用 {@link LongAdder} 累加，每 30 秒批量刷一次库。
 * 写压力从「每次浏览一行」降到「每 30 秒最多 N 行」，
 * 而 LongAdder 在高并发下比 AtomicLong 的争用小得多（分段累加）。
 *
 * <p>取舍：服务进程崩溃会丢掉最近 30 秒的计数。
 * 对浏览量这种非关键指标，这是完全可以接受的代价——这一点必须写清楚，
 * 因为它是有意为之，而不是疏忽。
 */
@Component
public class ViewCountBuffer {

    private static final Logger log = LoggerFactory.getLogger(ViewCountBuffer.class);

    private final Map<Long, LongAdder> counters = new ConcurrentHashMap<>();

    private final RecipeRepository recipeRepository;

    public ViewCountBuffer(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    /** 记一次浏览，纯内存操作，不碰数据库。 */
    public void increment(Long recipeId) {
        if (recipeId == null) {
            return;
        }
        counters.computeIfAbsent(recipeId, key -> new LongAdder()).increment();
    }

    /** 每 30 秒把累积量落库；用 sumThenReset 保证「取走并清零」是原子的。 */
    @Scheduled(fixedDelayString = "PT30S")
    @Transactional
    public void flush() {
        if (counters.isEmpty()) {
            return;
        }
        Set<Long> ids = Set.copyOf(counters.keySet());
        int flushed = 0;
        for (Long id : ids) {
            LongAdder adder = counters.get(id);
            if (adder == null) {
                continue;
            }
            long delta = adder.sumThenReset();
            if (delta <= 0) {
                continue;
            }
            try {
                flushed += recipeRepository.addViewCount(id, (int) delta);
            } catch (Exception ex) {
                // 菜谱可能已被删除；把量加回去会让计数永远还不上，直接丢弃并记日志
                log.warn("浏览量落库失败 recipeId={} delta={}", id, delta, ex);
            }
            // 已归零的计数器移除，避免 map 无限增长
            counters.computeIfPresent(id, (key, adder2) -> adder2.longValue() == 0 ? null : adder2);
        }
        if (flushed > 0) {
            log.debug("浏览量批量落库：{} 条菜谱", flushed);
        }
    }
}
