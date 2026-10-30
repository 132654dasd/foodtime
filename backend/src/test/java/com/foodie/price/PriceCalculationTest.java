package com.foodie.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.foodie.price.dto.CostIngredient;
import com.foodie.price.dto.DishCostVO;

/**
 * 做菜成本的核心算法测试。
 *
 * <p>不带 Spring 上下文：这三块（单位换算 / 用量解析 / 成本累加）都是纯函数，
 * 用真实 MySQL 去测它们纯属浪费——集成测试留给 ngram 检索、Flyway、原子计数那些
 * 只有真实数据库才能验证的东西。
 */
@DisplayName("成本计算：单位换算与用量解析")
class PriceCalculationTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("单位换算")
    class Convert {

        @Test
        @DisplayName("同单位直接返回，不经过克再折回来")
        void sameUnitIsIdentity() {
            assertThat(UnitConverter.convert(bd("2"), "个", "个", null))
                    .isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("重量单位互换：500 g = 1 斤")
        void weightToWeight() {
            assertThat(UnitConverter.convert(bd("500"), "g", "斤", null))
                    .isEqualByComparingTo("1");
            assertThat(UnitConverter.convert(bd("300"), "g", "斤", null))
                    .isEqualByComparingTo("0.6");
            assertThat(UnitConverter.convert(bd("1"), "两", "g", null))
                    .isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("按个买的番茄：2 个 ≈ 300 g = 0.6 斤")
        void countToWeightViaGramEquivalent() {
            assertThat(UnitConverter.convert(bd("2"), "个", "斤", bd("150")))
                    .isEqualByComparingTo("0.6");
        }

        @Test
        @DisplayName("按个计价的鸡蛋：100 g ≈ 1.818 个")
        void weightToCountViaGramEquivalent() {
            assertThat(UnitConverter.convert(bd("100"), "g", "个", bd("55")))
                    .isEqualByComparingTo("1.818182");
        }

        @Test
        @DisplayName("勺的固定容量：1 勺 = 15 ml，1 小勺 = 5 ml")
        void spoonToMilliliter() {
            assertThat(UnitConverter.convert(bd("1"), "勺", "ml", null))
                    .isEqualByComparingTo("15");
            assertThat(UnitConverter.convert(bd("1"), "小勺", "ml", null))
                    .isEqualByComparingTo("5");
            // 别名：汤匙 = 大勺；茶匙 = 小勺
            assertThat(UnitConverter.convert(bd("2"), "汤匙", "ml", null))
                    .isEqualByComparingTo("30");
        }

        @Test
        @DisplayName("勺可以折成重量再折到斤（1 勺盐 ≈ 15 g）")
        void spoonToWeight() {
            assertThat(UnitConverter.convert(bd("1"), "勺", "斤", null))
                    .isEqualByComparingTo("0.03");
        }

        @Test
        @DisplayName("计数单位没标克当量时无法换算，必须返回 null 而不是瞎猜")
        void countWithoutGramEquivalentIsUnconvertible() {
            assertThat(UnitConverter.convert(bd("3"), "粒", "斤", null)).isNull();
        }

        @Test
        @DisplayName("没见过的单位也返回 null")
        void unknownUnitIsUnconvertible() {
            assertThat(UnitConverter.convert(bd("1"), "把", "斤", null)).isNull();
        }
    }

    @Nested
    @DisplayName("用量文本解析")
    class Amount {

        @Test
        @DisplayName("纯数字按数值解析")
        void numeric() {
            AmountParser.Parsed parsed = AmountParser.parse("500");
            assertThat(parsed.quantity()).isEqualByComparingTo("500");
            assertThat(parsed.estimated()).isFalse();
        }

        @Test
        @DisplayName("「500g」这种带单位后缀的写法也能抽出数字")
        void numericWithUnitSuffix() {
            assertThat(AmountParser.parse("500g").quantity()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("「半」按 0.5 处理")
        void half() {
            assertThat(AmountParser.parse("半").quantity()).isEqualByComparingTo("0.5");
        }

        @Test
        @DisplayName("「少许」折 3 g 并标记为估算")
        void vagueTrace() {
            AmountParser.Parsed parsed = AmountParser.parse("少许");
            assertThat(parsed.quantity()).isNull();
            assertThat(parsed.vagueGrams()).isEqualByComparingTo("3");
            assertThat(parsed.estimated()).isTrue();
        }

        @Test
        @DisplayName("「适量」折 5 g；空用量按适量兜底")
        void vagueModerate() {
            assertThat(AmountParser.parse("适量").vagueGrams()).isEqualByComparingTo("5");
            assertThat(AmountParser.parse(null).vagueGrams()).isEqualByComparingTo("5");
            assertThat(AmountParser.parse("  ").vagueGrams()).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("「几滴」折 1 ml")
        void vagueDrops() {
            assertThat(AmountParser.parse("几滴").vagueGrams()).isEqualByComparingTo("1");
        }

        @Test
        @DisplayName("认不出的写法也要给估算值，不能让这道菜变成 0 元")
        void unknownTextFallsBackToEstimate() {
            AmountParser.Parsed parsed = AmountParser.parse("一把");
            assertThat(parsed.vagueGrams()).isEqualByComparingTo("5");
            assertThat(parsed.estimated()).isTrue();
        }
    }

    @Nested
    @DisplayName("整道菜的成本累加")
    class DishCost {

        private DishCostService serviceWith(Map<String, PriceSnapshot> prices) {
            PriceCatalogService catalog = mock(PriceCatalogService.class);
            when(catalog.enabledByName()).thenReturn(prices);
            // recipeRepository 在 costOf() 里用不到，传 null 即可（只有 menuCost 才需要）
            return new DishCostService(catalog, null);
        }

        private PriceSnapshot tomato() {
            return new PriceSnapshot("番茄", IngredientCategory.VEGETABLE, "斤", bd("4.5"), bd("150"));
        }

        private PriceSnapshot salt() {
            return new PriceSnapshot("盐", IngredientCategory.SEASONING, "斤", bd("1.5"), null);
        }

        @Test
        @DisplayName("2 个番茄 + 少许盐 = 2.70 + 0.01")
        void sumsUpPricedLines() {
            DishCostService service = serviceWith(Map.of("番茄", tomato(), "盐", salt()));
            DishCostVO cost = service.costOf(1L, "番茄拌盐", 2,
                    List.of(new CostIngredient("番茄", "2", "个"),
                            new CostIngredient("盐", "少许", null)));

            assertThat(cost.totalCost()).isEqualByComparingTo("2.71");
            assertThat(cost.servingCost()).isEqualByComparingTo("1.36");
            assertThat(cost.pricedCount()).isEqualTo(2);
            assertThat(cost.unpricedCount()).isZero();
            assertThat(cost.complete()).isTrue();
            // 「少许」那一行要标记为估算，UI 才会显示「估算」标签
            assertThat(cost.estimatedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("没有价目的食材不计入总额，但必须如实报出未定价数量")
        void unpricedIngredientsAreReported() {
            DishCostService service = serviceWith(Map.of("番茄", tomato()));
            DishCostVO cost = service.costOf(2L, "未知菜", 1,
                    List.of(new CostIngredient("番茄", "2", "个"),
                            new CostIngredient("神秘香料", "1", "g")));

            assertThat(cost.totalCost()).isEqualByComparingTo("2.70");
            assertThat(cost.pricedCount()).isEqualTo(1);
            assertThat(cost.unpricedCount()).isEqualTo(1);
            assertThat(cost.complete()).isFalse();
            assertThat(cost.lines()).hasSize(2);
            assertThat(cost.lines().get(1).priced()).isFalse();
            assertThat(cost.lines().get(1).cost()).isNull();
        }

        @Test
        @DisplayName("单位换算不了时按未定价处理，绝不当作 0 元混进总额")
        void unconvertibleUnitIsTreatedAsUnpriced() {
            DishCostService service = serviceWith(Map.of("番茄", tomato()));
            // 「3 把」这个单位换算不出斤，即使番茄有价目也不能计价
            DishCostVO cost = service.costOf(3L, "怪菜", 1,
                    List.of(new CostIngredient("番茄", "3", "把")));

            assertThat(cost.totalCost()).isEqualByComparingTo("0");
            assertThat(cost.unpricedCount()).isEqualTo(1);
            assertThat(cost.complete()).isFalse();
        }

        @Test
        @DisplayName("份数缺失时不算人均，不抛异常")
        void nullServingsGivesNoServingCost() {
            DishCostService service = serviceWith(Map.of("番茄", tomato()));
            DishCostVO cost = service.costOf(4L, "番茄", null,
                    List.of(new CostIngredient("番茄", "2", "个")));

            assertThat(cost.totalCost()).isEqualByComparingTo("2.70");
            assertThat(cost.servingCost()).isNull();
        }
    }
}
