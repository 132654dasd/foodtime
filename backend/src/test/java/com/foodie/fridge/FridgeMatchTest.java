package com.foodie.fridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.foodie.recipe.IngredientRow;

/**
 * 冰箱推荐的核心算法测试。
 *
 * <p>不带 Spring 上下文：匹配、数量判定、排序三块都是纯函数，
 * 用真实 MySQL 去测它们纯属浪费——集成测试留给唯一约束、分组统计那些
 * 只有真实数据库才能验证的东西（见 FoodieMySqlIT）。
 *
 * <p>本文件专门盯住两类「静默算错」：
 * <ol>
 *   <li>把不该算的算成命中（有番茄就说能做番茄酱的菜）；</li>
 *   <li>把不该算的算成够（数量换算不了却当成够用）。</li>
 * </ol>
 * 这两类错误都不会抛异常，只会让推荐结果变得不可信，必须靠测试兜住。
 */
@DisplayName("冰箱推荐：食材匹配与排序")
class FridgeMatchTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    // ------------------------------------------------------------ 构造工具

    private static IngredientRow row(long recipeId, String name, boolean main, String amount, String unit) {
        return new IngredientRow(recipeId, name, main ? 1 : 0, amount, unit);
    }

    private static FridgeItem item(String name, String quantity, String unit) {
        return item(name, quantity, unit, null);
    }

    private static FridgeItem item(String name, String quantity, String unit, LocalDate expireAt) {
        FridgeItem item = new FridgeItem();
        item.setUserId(1L);
        item.setName(name);
        item.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        item.setUnit(unit == null ? "" : unit);
        item.setExpireAt(expireAt);
        return item;
    }

    /** 番茄「克当量」：1 个 ≈ 150 g，其余食材没有克当量。 */
    private static final Function<String, BigDecimal> GRAMS = name ->
            "番茄".equals(name) ? new BigDecimal("150") : null;

    private static RecipeMatcher.RecipeMatch onlyMatch(List<IngredientRow> rows,
                                                       List<FridgeItem> items,
                                                       Function<String, BigDecimal> grams) {
        List<RecipeMatcher.RecipeMatch> matches = RecipeMatcher.match(rows, items, grams, TODAY);
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    // ============================================================ 匹配

    @Nested
    @DisplayName("食材名匹配")
    class Match {

        @Test
        @DisplayName("写法一样就是精确匹配")
        void exact() {
            assertThat(IngredientMatcher.match("番茄", "番茄")).isEqualTo(IngredientMatcher.Tier.EXACT);
        }

        @Test
        @DisplayName("同义词收敛：西红柿 = 番茄、蒜 = 大蒜、食盐 = 盐")
        void synonym() {
            assertThat(IngredientMatcher.match("西红柿", "番茄")).isEqualTo(IngredientMatcher.Tier.EXACT);
            assertThat(IngredientMatcher.match("蒜", "大蒜")).isEqualTo(IngredientMatcher.Tier.EXACT);
            assertThat(IngredientMatcher.match("食盐", "盐")).isEqualTo(IngredientMatcher.Tier.EXACT);
        }

        @Test
        @DisplayName("形态后缀剥离：姜片 / 姜末 都算「姜」，蒜末算「大蒜」")
        void formSuffix() {
            assertThat(IngredientMatcher.match("姜片", "姜")).isEqualTo(IngredientMatcher.Tier.EXACT);
            assertThat(IngredientMatcher.match("姜末", "姜")).isEqualTo(IngredientMatcher.Tier.EXACT);
            assertThat(IngredientMatcher.match("蒜末", "大蒜")).isEqualTo(IngredientMatcher.Tier.EXACT);
        }

        @Test
        @DisplayName("包含关系算近似：五花肉 ≈ 带皮五花肉、白菜 ≈ 黄秧白菜心")
        void approximate() {
            assertThat(IngredientMatcher.match("五花肉", "带皮五花肉"))
                    .isEqualTo(IngredientMatcher.Tier.APPROX);
            assertThat(IngredientMatcher.match("白菜", "黄秧白菜心"))
                    .isEqualTo(IngredientMatcher.Tier.APPROX);
        }

        @Test
        @DisplayName("护栏一：单字不做包含匹配，「葱」不能匹配「洋葱」")
        void shortNameGuard() {
            assertThat(IngredientMatcher.match("葱", "洋葱")).isNull();
        }

        @Test
        @DisplayName("护栏二：有番茄不等于有番茄酱，有花生不等于有花生油")
        void processedFormGuard() {
            assertThat(IngredientMatcher.match("番茄", "番茄酱")).isNull();
            assertThat(IngredientMatcher.match("番茄", "番茄膏")).isNull();
            assertThat(IngredientMatcher.match("花生", "花生油")).isNull();
        }

        @Test
        @DisplayName("护栏是单向的：冰箱里有辣椒面，用来做需要辣椒的菜算合理替代")
        void processedGuardIsDirectional() {
            assertThat(IngredientMatcher.match("辣椒面", "辣椒")).isEqualTo(IngredientMatcher.Tier.APPROX);
        }

        @Test
        @DisplayName("毫不相干的名字必须匹配不上")
        void unrelated() {
            assertThat(IngredientMatcher.match("粉丝", "番茄")).isNull();
            assertThat(IngredientMatcher.match("", "番茄")).isNull();
            assertThat(IngredientMatcher.match(null, "番茄")).isNull();
        }
    }

    // ============================================================ 数量

    @Nested
    @DisplayName("数量充足性")
    class Stock {

        private List<IngredientRow> tomatoTwoUnits() {
            return List.of(row(1L, "番茄", true, "2", "个"));
        }

        @Test
        @DisplayName("3 个 ≥ 2 个 → 够")
        void enough() {
            RecipeMatcher.RecipeMatch match =
                    onlyMatch(tomatoTwoUnits(), List.of(item("番茄", "3", "个")), GRAMS);
            assertThat(match.matched().get(0).stock()).isEqualTo(RecipeMatcher.Stock.ENOUGH);
            assertThat(match.notEnoughMainCount()).isZero();
        }

        @Test
        @DisplayName("1 个 < 2 个 → 有但不够（仍然推荐，只是标出来）")
        void notEnough() {
            RecipeMatcher.RecipeMatch match =
                    onlyMatch(tomatoTwoUnits(), List.of(item("番茄", "1", "个")), GRAMS);
            assertThat(match.matched().get(0).stock()).isEqualTo(RecipeMatcher.Stock.NOT_ENOUGH);
            assertThat(match.notEnoughMainCount()).isEqualTo(1);
            assertThat(match.cookable()).isTrue();
        }

        @Test
        @DisplayName("跨单位比较：0.5 斤（250 g）< 2 个（300 g），1 斤（500 g）够")
        void crossUnit() {
            RecipeMatcher.RecipeMatch shortOf =
                    onlyMatch(tomatoTwoUnits(), List.of(item("番茄", "0.5", "斤")), GRAMS);
            assertThat(shortOf.matched().get(0).stock()).isEqualTo(RecipeMatcher.Stock.NOT_ENOUGH);

            RecipeMatcher.RecipeMatch plenty =
                    onlyMatch(tomatoTwoUnits(), List.of(item("番茄", "1", "斤")), GRAMS);
            assertThat(plenty.matched().get(0).stock()).isEqualTo(RecipeMatcher.Stock.ENOUGH);
        }

        @Test
        @DisplayName("菜谱写「适量」时不算够不够，只算有")
        void vagueAmount() {
            List<IngredientRow> rows = List.of(row(1L, "番茄", true, "适量", null));
            RecipeMatcher.RecipeMatch match =
                    onlyMatch(rows, List.of(item("番茄", "1", "个")), GRAMS);
            assertThat(match.matched().get(0).stock()).isEqualTo(RecipeMatcher.Stock.PRESENT);
        }

        @Test
        @DisplayName("没有克当量时无法换算，退化为「有就行」，绝不猜成不够")
        void noGramEquivalent() {
            List<IngredientRow> rows = List.of(row(1L, "土豆", true, "2", "个"));
            RecipeMatcher.RecipeMatch match =
                    onlyMatch(rows, List.of(item("土豆", "1", "个")), GRAMS);
            assertThat(match.matched().get(0).stock()).isEqualTo(RecipeMatcher.Stock.PRESENT);
            assertThat(match.notEnoughMainCount()).isZero();
        }

        @Test
        @DisplayName("冰箱没填数量时只算有")
        void fridgeWithoutQuantity() {
            RecipeMatcher.RecipeMatch match =
                    onlyMatch(tomatoTwoUnits(), List.of(item("番茄", null, "")), GRAMS);
            assertThat(match.matched().get(0).stock()).isEqualTo(RecipeMatcher.Stock.PRESENT);
        }
    }

    // ============================================================ 候选与排序

    @Nested
    @DisplayName("候选资格与排序")
    class Ranking {

        @Test
        @DisplayName("只命中调料的菜谱不算候选——用户要的是菜，不是盐管够")
        void auxOnlyIsNotCandidate() {
            List<IngredientRow> rows = List.of(
                    row(1L, "番茄", true, "2", "个"),
                    row(1L, "盐", false, "适量", null));
            List<RecipeMatcher.RecipeMatch> matches =
                    RecipeMatcher.match(rows, List.of(item("盐", null, "")), GRAMS, TODAY);
            assertThat(matches).isEmpty();
        }

        @Test
        @DisplayName("缺主料少的排前面")
        void fewerMissingMainFirst() {
            List<IngredientRow> rows = List.of(
                    // 1 号：番茄 + 鸡蛋，冰箱有番茄 → 缺 1 样主料
                    row(1L, "番茄", true, "2", "个"),
                    row(1L, "鸡蛋", true, "2", "个"),
                    // 2 号：番茄 + 排骨 + 冬瓜，冰箱有番茄 → 缺 2 样主料
                    row(2L, "番茄", true, "2", "个"),
                    row(2L, "排骨", true, "500", "g"),
                    row(2L, "冬瓜", true, "600", "g"));

            List<RecipeMatcher.RecipeMatch> sorted =
                    RecipeMatcher.match(rows, List.of(item("番茄", null, "")), GRAMS, TODAY)
                            .stream()
                            .sorted(RecipeMatcher.ranking(Map.of()))
                            .toList();

            assertThat(sorted).extracting(RecipeMatcher.RecipeMatch::recipeId)
                    .containsExactly(1L, 2L);
            assertThat(sorted.get(0).missingMain()).containsExactly("鸡蛋");
            assertThat(sorted.get(1).missingMain()).containsExactly("排骨", "冬瓜");
        }

        @Test
        @DisplayName("缺主料一样多时评分高的排前面")
        void ratingBreaksTie() {
            List<IngredientRow> rows = List.of(
                    row(1L, "番茄", true, "2", "个"),
                    row(1L, "鸡蛋", true, "2", "个"),
                    row(2L, "番茄", true, "2", "个"),
                    row(2L, "豆腐", true, "1", "块"));

            Map<Long, RecipeMetric> metrics = Map.of(
                    1L, new RecipeMetric(new BigDecimal("4.90"), 10),
                    2L, new RecipeMetric(new BigDecimal("3.50"), 99));

            List<RecipeMatcher.RecipeMatch> sorted =
                    RecipeMatcher.match(rows, List.of(item("番茄", null, "")), GRAMS, TODAY)
                            .stream()
                            .sorted(RecipeMatcher.ranking(metrics))
                            .toList();

            assertThat(sorted).extracting(RecipeMatcher.RecipeMatch::recipeId)
                    .containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("用到快过期食材的菜排前面——减少浪费")
        void expiringFirst() {
            List<IngredientRow> rows = List.of(
                    row(1L, "番茄", true, "2", "个"),
                    row(1L, "鸡蛋", true, "2", "个"),
                    row(2L, "土豆", true, "2", "个"),
                    row(2L, "排骨", true, "500", "g"));

            List<FridgeItem> fridge = List.of(
                    item("番茄", "3", "个", TODAY.plusDays(1)),
                    item("土豆", "3", "个"));

            List<RecipeMatcher.RecipeMatch> matches =
                    RecipeMatcher.match(rows, fridge, GRAMS, TODAY).stream()
                            .sorted(RecipeMatcher.ranking(Map.of()))
                            .toList();

            assertThat(matches).extracting(RecipeMatcher.RecipeMatch::recipeId)
                    .containsExactly(1L, 2L);
            assertThat(matches.get(0).expiringUsed()).containsExactly("番茄");
        }

        @Test
        @DisplayName("主料全齐才算「现在就能做」")
        void cookableOnlyWhenAllMainMatched() {
            List<IngredientRow> rows = List.of(
                    row(1L, "番茄", true, "2", "个"),
                    row(1L, "鸡蛋", true, "2", "个"),
                    row(1L, "盐", false, "适量", null));

            RecipeMatcher.RecipeMatch match =
                    onlyMatch(rows, List.of(item("番茄", null, ""), item("鸡蛋", null, "")), GRAMS);
            assertThat(match.cookable()).isTrue();
            assertThat(match.missingMain()).isEmpty();
            assertThat(match.missingAux()).containsExactly("盐");
        }

        @Test
        @DisplayName("冰箱为空时没有任何候选，不抛异常")
        void emptyFridge() {
            assertThat(RecipeMatcher.match(List.of(row(1L, "番茄", true, "2", "个")),
                    List.of(), GRAMS, TODAY)).isEmpty();
        }
    }

    // ============================================================ 未用到

    @Test
    @DisplayName("菜谱里根本不存在匹配的食材时，要能识别出来（用于提示「暂无菜谱用到」）")
    void unusedIngredientDetection() {
        List<String> ingredientNames = List.of("番茄", "带皮五花肉");
        assertThat(FridgeService.unusedNames(
                List.of(item("五花肉", "1", "斤"), item("粉丝", "1", "袋")),
                ingredientNames))
                .containsExactly("粉丝");
    }
}
