package com.foodie.menu;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.foodie.common.BizException;

/**
 * 策略注册表。
 *
 * <p>靠 Spring 把实现类全部注入进来，再按 {@link MenuStrategyType} 建索引。
 * 好处是新增策略只要加一个 {@code @Component}，这里一行都不用改。
 */
@Component
public class MenuStrategyRegistry {

    private final Map<MenuStrategyType, MenuStrategy> strategies = new EnumMap<>(MenuStrategyType.class);

    public MenuStrategyRegistry(List<MenuStrategy> discovered) {
        for (MenuStrategy strategy : discovered) {
            strategies.put(strategy.type(), strategy);
        }
    }

    public MenuStrategy get(MenuStrategyType type) {
        MenuStrategy strategy = strategies.get(type == null ? MenuStrategyType.BALANCED : type);
        if (strategy == null) {
            throw BizException.badRequest("不支持的配餐策略");
        }
        return strategy;
    }
}
