package com.foodie.menu.strategy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.foodie.menu.MenuStrategy;
import com.foodie.menu.MenuStrategyType;
import com.foodie.menu.WeightedSampler;
import com.foodie.recipe.RecipeCandidate;

/** 纯随机：等概率抽取，不做任何平衡约束。 */
@Component
public class RandomStrategy implements MenuStrategy {

    @Override
    public MenuStrategyType type() {
        return MenuStrategyType.RANDOM;
    }

    @Override
    public Selection pick(List<RecipeCandidate> dishPool, List<RecipeCandidate> soupPool,
                          int dishCount, int soupCount) {
        List<String> warnings = new ArrayList<>();
        List<Long> dishIds = WeightedSampler.sample(dishPool, dishCount, WeightedSampler::uniformWeight);
        List<Long> soupIds = WeightedSampler.sample(soupPool, soupCount, WeightedSampler::uniformWeight);

        if (dishIds.size() < dishCount) {
            warnings.add("菜池当前只有 %d 道可配（需要 %d 道），已按实际数量给出"
                    .formatted(dishIds.size(), dishCount));
        }
        if (soupIds.size() < soupCount) {
            warnings.add("汤池当前只有 %d 道可配（需要 %d 道），已按实际数量给出"
                    .formatted(soupIds.size(), soupCount));
        }
        return new Selection(dishIds, soupIds, warnings);
    }
}
