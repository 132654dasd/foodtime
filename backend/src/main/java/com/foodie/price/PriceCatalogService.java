package com.foodie.price;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.config.CacheConfig;

/**
 * 价格字典的只读入口，独立成服务是为了让缓存生效。
 *
 * <p>为什么单独一个 Bean：Spring Cache 基于代理，<b>同一个类内部的自调用不会走缓存</b>。
 * 把「读取字典」放在这里、把「写价格」放在 {@code PriceService}，
 * 读走缓存、写走失效，职责和代理边界都清晰。
 *
 * <p>缓存的是 {@code List<PriceSnapshot>}（不可变 record 列表），不是实体。
 */
@Service
public class PriceCatalogService {

    private final IngredientPriceRepository repository;

    public PriceCatalogService(IngredientPriceRepository repository) {
        this.repository = repository;
    }

    /**
     * 当前所有启用的价目。
     *
     * <p>{@code sync = true}：价格刚失效的瞬间若有一桌菜的多个食材同时来查，
     * 只放一个线程回源，其余等待复用结果，避免击穿。
     */
    @Cacheable(value = CacheConfig.PRICE_CATALOG, key = "'enabled'", sync = true)
    @Transactional(readOnly = true)
    public List<PriceSnapshot> enabledPrices() {
        return repository.findByEnabledOrderByCategoryAscNameAsc(1).stream()
                .map(PriceSnapshot::of)
                .toList();
    }

    /**
     * 以食材名为键的价目表。
     *
     * <p>不单独缓存：从 137 条不可变快照构建一个 Map 是微秒级操作，
     * 而按请求算一次可以让「一次菜单计算里所有菜共用同一份价目」，
     * 避免在循环里反复查缓存。
     */
    public Map<String, PriceSnapshot> enabledByName() {
        return enabledPrices().stream()
                .collect(Collectors.toUnmodifiableMap(
                        PriceSnapshot::name, Function.identity(), (first, second) -> first));
    }
}
