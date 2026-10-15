package com.foodie.menu.strategy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.foodie.menu.MenuStrategy;
import com.foodie.menu.MenuStrategyType;
import com.foodie.menu.WeightedSampler;
import com.foodie.recipe.RecipeCandidate;

/**
 * 快手策略：权重向耗时短的菜倾斜。
 *
 * <p>用 {@code 1 / (1 + 分钟/30)} 而不是直接按耗时升序取前 N：
 * 后者会退化成「永远只推那几道最快的菜」，菜单一成不变，
 * 反而失去了「帮我决定」的意义。加权保留了多样性。
 */
@Component
public class QuickStrategy implements MenuStrategy {

    /** 超过这个时间就不太算「快手」了，用于给出提示。 */
    private static final int QUICK_THRESHOLD_MINUTES = 30;

    @Override
    public MenuStrategyType type() {
        return MenuStrategyType.QUICK;
    }

    @Override
    public Selection pick(List<RecipeCandidate> dishPool, List<RecipeCandidate> soupPool,
                          int dishCount, int soupCount) {
        List<String> warnings = new ArrayList<>();
        List<Long> dishIds = WeightedSampler.sample(dishPool, dishCount, WeightedSampler::quickWeight);
        List<Long> soupIds = WeightedSampler.sample(soupPool, soupCount, WeightedSampler::quickWeight);

        if (dishIds.size() < dishCount) {
            warnings.add("菜池当前只有 %d 道可配（需要 %d 道），已按实际数量给出"
                    .formatted(dishIds.size(), dishCount));
        }
        if (soupIds.size() < soupCount) {
            warnings.add("汤池当前只有 %d 道可配（需要 %d 道），已按实际数量给出"
                    .formatted(soupIds.size(), soupCount));
        }

        boolean hasSlow = dishPool.stream()
                .filter(candidate -> dishIds.contains(candidate.id()))
                .anyMatch(candidate -> candidate.cookMinutes() != null
                        && candidate.cookMinutes() > QUICK_THRESHOLD_MINUTES);
        if (hasSlow) {
            warnings.add("快手菜池偏少，本次仍混入了耗时超过 %d 分钟的菜".formatted(QUICK_THRESHOLD_MINUTES));
        }
        return new Selection(dishIds, soupIds, warnings);
    }
}
