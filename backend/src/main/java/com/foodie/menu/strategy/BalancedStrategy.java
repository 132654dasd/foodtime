package com.foodie.menu.strategy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.foodie.menu.MenuStrategy;
import com.foodie.menu.MenuStrategyType;
import com.foodie.menu.WeightedSampler;
import com.foodie.recipe.DishCategory;
import com.foodie.recipe.RecipeCandidate;

/**
 * 荤素均衡策略。
 *
 * <p>做法是「按分类轮转」：每轮依次从荤菜、素菜、凉菜、主食、甜品各取一道，
 * 再进入下一轮，直到凑够数量。只要池子里有素菜，就不会出现「十道全是肉」的菜单。
 *
 * <p>同一分类内部用热度加权抽样：保留随机性，但让常见菜更容易被选中。
 */
@Component
public class BalancedStrategy implements MenuStrategy {

    /**
     * 轮转顺序即优先级：先把荤菜和素菜铺开，再考虑凉菜/主食/甜品。
     * 一桌饭没有甜品可以接受，一道素的都没有不行。
     */
    private static final List<DishCategory> ROTATION = List.of(
            DishCategory.MEAT,
            DishCategory.VEGETABLE,
            DishCategory.COLD,
            DishCategory.STAPLE,
            DishCategory.DESSERT);

    @Override
    public MenuStrategyType type() {
        return MenuStrategyType.BALANCED;
    }

    @Override
    public Selection pick(List<RecipeCandidate> dishPool, List<RecipeCandidate> soupPool,
                          int dishCount, int soupCount) {
        List<String> warnings = new ArrayList<>();
        List<Long> dishIds = pickBalanced(dishPool, dishCount, warnings);

        // 汤池不再细分品类，直接热度加权
        List<Long> soupIds = WeightedSampler.sample(soupPool, soupCount, WeightedSampler::heatWeight);
        if (soupIds.size() < soupCount) {
            warnings.add("汤池当前只有 %d 道可配（需要 %d 道），已按实际数量给出"
                    .formatted(soupIds.size(), soupCount));
        }
        return new Selection(dishIds, soupIds, warnings);
    }

    private List<Long> pickBalanced(List<RecipeCandidate> dishPool, int dishCount, List<String> warnings) {
        Map<DishCategory, List<RecipeCandidate>> byCategory = new EnumMap<>(DishCategory.class);
        for (RecipeCandidate candidate : dishPool) {
            byCategory.computeIfAbsent(candidate.category(), key -> new ArrayList<>()).add(candidate);
        }

        List<Long> picked = new ArrayList<>(dishCount);
        Set<Long> pickedIds = new HashSet<>();

        boolean progressed = true;
        while (picked.size() < dishCount && progressed) {
            progressed = false;
            for (DishCategory category : ROTATION) {
                if (picked.size() >= dishCount) {
                    break;
                }
                List<RecipeCandidate> available = byCategory.getOrDefault(category, List.of()).stream()
                        .filter(candidate -> !pickedIds.contains(candidate.id()))
                        .toList();
                if (available.isEmpty()) {
                    continue;
                }
                List<Long> one = WeightedSampler.sample(available, 1, WeightedSampler::heatWeight);
                picked.addAll(one);
                pickedIds.addAll(one);
                progressed = true;
            }
        }

        if (picked.size() < dishCount) {
            warnings.add("菜池当前只有 %d 道可配（需要 %d 道），已按实际数量给出"
                    .formatted(picked.size(), dishCount));
        }
        // 只要汤（dishCount=0）的时候不该抱怨素菜：用户根本没要菜，
        // 这种告警是噪声，还会把真正重要的「汤不够」挤到后面去。
        if (dishCount > 0) {
            appendVegetableWarning(byCategory, picked, pickedIds, warnings);
        }
        return picked;
    }

    /** 明确告诉用户「这桌没配到素菜」以及原因，而不是假装搭配成功了。 */
    private void appendVegetableWarning(Map<DishCategory, List<RecipeCandidate>> byCategory,
                                        List<Long> picked,
                                        Set<Long> pickedIds,
                                        List<String> warnings) {
        List<RecipeCandidate> vegetablePool = byCategory.getOrDefault(DishCategory.VEGETABLE, List.of());
        if (vegetablePool.isEmpty()) {
            warnings.add("菜池里没有素菜可配，本次菜单未能做到荤素搭配");
            return;
        }
        boolean anyVegetablePicked = vegetablePool.stream().anyMatch(candidate -> pickedIds.contains(candidate.id()));
        if (!anyVegetablePicked && !picked.isEmpty()) {
            warnings.add("本次未选出素菜（可能被忌口过滤掉了），荤素搭配不均衡");
        }
    }
}
