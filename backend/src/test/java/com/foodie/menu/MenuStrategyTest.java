package com.foodie.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.foodie.menu.strategy.BalancedStrategy;
import com.foodie.menu.strategy.QuickStrategy;
import com.foodie.menu.strategy.RandomStrategy;
import com.foodie.recipe.DishCategory;
import com.foodie.recipe.RecipeCandidate;

/**
 * 随机配餐算法的单元测试。
 *
 * <p>全部是纯逻辑测试，不启动 Spring：策略只依赖传入的候选池，
 * 候选项加载与忌口过滤由 MenuPlannerService 负责，那部分另有测试。
 */
class MenuStrategyTest {

    private static RecipeCandidate candidate(long id, DishCategory category, int minutes) {
        return new RecipeCandidate(id, category, minutes, 1, 0, 0);
    }

    /** 13 道荤菜 / 6 道素菜 / 3 道凉菜 / 1 主食 / 1 甜品，贴近真实库容。 */
    private static List<RecipeCandidate> realisticDishPool() {
        List<RecipeCandidate> pool = new ArrayList<>();
        long id = 1;
        for (int i = 0; i < 13; i++) {
            pool.add(candidate(id++, DishCategory.MEAT, 30 + i * 5));
        }
        for (int i = 0; i < 6; i++) {
            pool.add(candidate(id++, DishCategory.VEGETABLE, 15 + i * 3));
        }
        for (int i = 0; i < 3; i++) {
            pool.add(candidate(id++, DishCategory.COLD, 10 + i));
        }
        pool.add(candidate(id++, DishCategory.STAPLE, 15));
        pool.add(candidate(id, DishCategory.DESSERT, 240));
        return pool;
    }

    // ============================================================ 通用不变量

    @Nested
    @DisplayName("所有策略都必须满足的不变量")
    class Invariants {

        @Test
        @DisplayName("抽出的菜不重复，且数量不超过请求数")
        void noDuplicatesAndRespectsCount() {
            List<RecipeCandidate> pool = realisticDishPool();
            List<MenuStrategy> strategies = List.of(
                    new RandomStrategy(), new BalancedStrategy(), new QuickStrategy());

            for (int round = 0; round < 50; round++) {
                for (MenuStrategy strategy : strategies) {
                    MenuStrategy.Selection selection = strategy.pick(pool, List.of(), 10, 0);
                    Set<Long> unique = new HashSet<>(selection.dishIds());
                    assertThat(unique)
                            .as("%s 抽出的菜不能重复", strategy.type())
                            .hasSize(selection.dishIds().size());
                    assertThat(selection.dishIds()).hasSizeLessThanOrEqualTo(10);
                }
            }
        }

        @Test
        @DisplayName("池子不足时降级到实际数量，并给出 warning，而不是报错或静默补齐")
        void degradesWithWarning() {
            List<RecipeCandidate> tiny = List.of(
                    candidate(1, DishCategory.SOUP, 20),
                    candidate(2, DishCategory.SOUP, 30));

            MenuStrategy.Selection selection = new BalancedStrategy().pick(List.of(), tiny, 0, 3);

            assertThat(selection.soupIds()).hasSize(2);
            assertThat(selection.warnings())
                    .as("必须明确告诉用户汤不够，不能假装配齐")
                    .isNotEmpty();
            assertThat(selection.warnings().get(0)).contains("汤池");
        }

        @Test
        @DisplayName("空池子返回空结果且不抛异常")
        void emptyPoolIsSafe() {
            MenuStrategy.Selection selection = new RandomStrategy().pick(List.of(), List.of(), 3, 1);
            assertThat(selection.dishIds()).isEmpty();
            assertThat(selection.soupIds()).isEmpty();
            assertThat(selection.warnings()).hasSize(2);
        }
    }

    // ============================================================ 均衡策略

    @Nested
    @DisplayName("荤素均衡策略")
    class Balanced {

        private final BalancedStrategy strategy = new BalancedStrategy();

        @Test
        @DisplayName("两菜时应该荤素各一，而不是两道荤菜")
        void twoDishesShouldBeOneMeatOneVegetable() {
            List<RecipeCandidate> pool = realisticDishPool();

            for (int round = 0; round < 30; round++) {
                MenuStrategy.Selection selection = strategy.pick(pool, List.of(), 2, 0);
                Set<Long> picked = new HashSet<>(selection.dishIds());
                boolean hasMeat = pool.stream()
                        .anyMatch(c -> picked.contains(c.id()) && c.category() == DishCategory.MEAT);
                boolean hasVegetable = pool.stream()
                        .anyMatch(c -> picked.contains(c.id()) && c.category() == DishCategory.VEGETABLE);

                assertThat(hasMeat && hasVegetable)
                        .as("第 %d 轮：均衡策略下 2 道菜应荤素搭配", round)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("十菜三汤时覆盖尽量多的分类")
        void coversManyCategories() {
            List<RecipeCandidate> pool = realisticDishPool();
            MenuStrategy.Selection selection = strategy.pick(pool, List.of(), 10, 0);

            java.util.Map<Long, DishCategory> byId = new java.util.HashMap<>();
            pool.forEach(c -> byId.put(c.id(), c.category()));

            Set<DishCategory> categories = new HashSet<>();
            selection.dishIds().forEach(id -> categories.add(byId.get(id)));

            assertThat(categories)
                    .as("10 道菜应该铺开到荤/素/凉/主/甜 5 个分类")
                    .containsExactlyInAnyOrder(DishCategory.MEAT, DishCategory.VEGETABLE,
                            DishCategory.COLD, DishCategory.STAPLE, DishCategory.DESSERT);
        }

        @Test
        @DisplayName("池里完全没有素菜时给出明确告警")
        void warnsWhenNoVegetable() {
            List<RecipeCandidate> meatOnly = List.of(
                    candidate(1, DishCategory.MEAT, 30),
                    candidate(2, DishCategory.MEAT, 40));

            MenuStrategy.Selection selection = strategy.pick(meatOnly, List.of(), 2, 0);

            assertThat(selection.dishIds()).hasSize(2);
            assertThat(selection.warnings())
                    .anyMatch(w -> w.contains("没有素菜"));
        }
    }

    // ============================================================ 快手策略

    @Nested
    @DisplayName("快手策略")
    class Quick {

        @Test
        @DisplayName("抽出的菜平均耗时显著低于纯随机")
        void prefersFasterDishes() {
            List<RecipeCandidate> pool = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                pool.add(candidate(100 + i, DishCategory.MEAT, 10));   // 快手
            }
            for (int i = 0; i < 10; i++) {
                pool.add(candidate(200 + i, DishCategory.MEAT, 120));  // 慢
            }
            var byId = new java.util.HashMap<Long, Integer>();
            pool.forEach(c -> byId.put(c.id(), c.cookMinutes()));

            double quickTotal = 0;
            double randomTotal = 0;
            int rounds = 300;
            QuickStrategy quick = new QuickStrategy();
            RandomStrategy random = new RandomStrategy();

            for (int i = 0; i < rounds; i++) {
                quickTotal += averageMinutes(quick.pick(pool, List.of(), 4, 0).dishIds(), byId);
                randomTotal += averageMinutes(random.pick(pool, List.of(), 4, 0).dishIds(), byId);
            }

            assertThat(quickTotal / rounds)
                    .as("快手策略的平均耗时（%.1f）应明显低于随机（%.1f）", quickTotal / rounds, randomTotal / rounds)
                    .isLessThan(randomTotal / rounds);
        }

        private double averageMinutes(List<Long> ids, java.util.Map<Long, Integer> byId) {
            return ids.stream().mapToInt(byId::get).average().orElse(0);
        }
    }

    // ============================================================ 加权抽样

    @Nested
    @DisplayName("加权抽样")
    class Sampler {

        @Test
        @DisplayName("不放回：k 大于池子时只返回池中全部")
        void doesNotOverdraw() {
            List<RecipeCandidate> pool = List.of(
                    candidate(1, DishCategory.MEAT, 10),
                    candidate(2, DishCategory.MEAT, 20));

            List<Long> picked = WeightedSampler.sample(pool, 5, WeightedSampler::uniformWeight);

            assertThat(picked).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("权重为 0 的候选依然有概率被抽中（权重被抬到下限）")
        void zeroWeightStillReachable() {
            RecipeCandidate zeroWeight = new RecipeCandidate(7L, DishCategory.MEAT, 60, 1, null, null);
            List<RecipeCandidate> pool = List.of(zeroWeight);

            List<Long> picked = WeightedSampler.sample(pool, 1, c -> 0.0);

            assertThat(picked).containsExactly(7L);
        }

        @Test
        @DisplayName("热度权重对浏览量单调递增")
        void heatWeightIsMonotonic() {
            RecipeCandidate low = new RecipeCandidate(1L, DishCategory.MEAT, 30, 1, 0, 0);
            RecipeCandidate high = new RecipeCandidate(2L, DishCategory.MEAT, 30, 1, 10_000, 500);

            assertThat(WeightedSampler.heatWeight(high))
                    .isGreaterThan(WeightedSampler.heatWeight(low));
        }
    }
}
