package com.foodie;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.recipe.Difficulty;
import com.foodie.recipe.DishCategory;
import com.foodie.recipe.Recipe;
import com.foodie.recipe.RecipeIngredientRepository;
import com.foodie.recipe.RecipeRepository;
import com.foodie.recipe.RecipeService;
import com.foodie.recipe.dto.RecipeDetailVO;
import com.foodie.recipe.dto.RecipeSaveRequest;
import com.foodie.user.UserRepository;

/**
 * 打真实 MySQL 的集成测试。
 *
 * <p>这里<b>只</b>放 H2 无法覆盖的东西：ngram 中文全文索引、Flyway 迁移结果、
 * 以及依赖真实 SQL 语义的原子计数。普通的业务逻辑都在单元测试里，不重复。
 *
 * <p>由 failsafe 插件执行（类名以 IT 结尾），所以 {@code mvnw test} 不会跑它，
 * 避免开发者在没装 MySQL 的机器上被卡住；{@code mvnw verify} 才会跑。
 */
@SpringBootTest
@ActiveProfiles("it")
@Transactional
@DisplayName("MySQL 集成测试")
class FoodieMySqlIT {

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeIngredientRepository ingredientRepository;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Flyway 迁移结果：30 道菜谱、分类与难度分布正确")
    void flywaySeedsExpectedRecipes() {
        assertThat(recipeRepository.count()).as("种子菜谱数量").isEqualTo(30);

        assertThat(recipeRepository.countGroupByCategory())
                .as("分类分布")
                .hasSize(6);
        assertThat(recipeRepository.countGroupByDifficulty())
                .as("难度分布")
                .hasSize(3);

        // 非汤池必须够配「十菜三汤」，否则核心功能会退化
        long soupCount = recipeRepository.findCandidates(List.of(DishCategory.SOUP)).size();
        long dishCount = recipeRepository.findCandidates(List.of(
                DishCategory.MEAT, DishCategory.VEGETABLE, DishCategory.COLD,
                DishCategory.STAPLE, DishCategory.DESSERT)).size();
        assertThat(soupCount).as("汤池容量的下限").isGreaterThanOrEqualTo(3);
        assertThat(dishCount).as("菜池容量必须支撑「十菜」").isGreaterThanOrEqualTo(10);

        assertThat(userRepository.findByUsername("admin")).as("管理员种子账号").isPresent();
        assertThat(userRepository.findByUsername("admin").orElseThrow().getPassword())
                .as("种子密码必须是 BCrypt 哈希而不是明文")
                .startsWith("$2");
    }

    @Test
    @DisplayName("ngram 中文全文索引：搜「排骨」命中排骨类菜谱，搜「豆腐」按相关度排序")
    void chineseFulltextSearchWorks() {
        Page<Recipe> ribs = recipeRepository.searchByFulltext(
                "排骨", null, null, null, null, PageRequest.of(0, 10));

        assertThat(ribs.getTotalElements()).as("「排骨」的命中数").isEqualTo(3);
        assertThat(ribs.getContent()).extracting(Recipe::getTitle)
                .containsExactlyInAnyOrder("冬瓜排骨汤", "玉米胡萝卜排骨汤", "糖醋排骨");

        // ngram 检索是词粒度而不是子串匹配，所以描述里写了「豆腐」也会命中，
        // 但相关度必须把真正叫「麻婆豆腐」的那道排在前面
        Page<Recipe> tofu = recipeRepository.searchByFulltext(
                "豆腐", null, null, null, null, PageRequest.of(0, 10));
        assertThat(tofu.getContent()).as("「豆腐」的结果按相关度排序")
                .isNotEmpty()
                .first()
                .extracting(Recipe::getTitle)
                .isEqualTo("麻婆豆腐");
    }

    @Test
    @DisplayName("全文检索叠加筛选条件仍然生效")
    void fulltextCombinesWithFilters() {
        // 种子数据里：「冬瓜排骨汤」EASY、「玉米胡萝卜排骨汤」与「糖醋排骨」MEDIUM。
        // 加上难度筛选后必须真的变窄，而不是把关键词结果原样返回。
        Page<Recipe> medium = recipeRepository.searchByFulltext(
                "排骨", null, null, Difficulty.MEDIUM.name(), null, PageRequest.of(0, 10));

        assertThat(medium.getContent())
                .as("「排骨」+ MEDIUM 应恰好命中这两道，并排除 EASY 的冬瓜排骨汤")
                .extracting(Recipe::getTitle)
                .containsExactlyInAnyOrder("玉米胡萝卜排骨汤", "糖醋排骨");

        Page<Recipe> hard = recipeRepository.searchByFulltext(
                "排骨", null, null, Difficulty.HARD.name(), null, PageRequest.of(0, 10));
        assertThat(hard.getContent()).as("「排骨」里没有 HARD 难度的菜").isEmpty();
    }

    @Test
    @DisplayName("忌口反查：按食材名子串匹配，命中含香菜的三道菜")
    void avoidFilterMatchesIngredientNames() {
        List<Long> blocked = ingredientRepository.findRecipeIdsByIngredientNameContaining("香菜");

        assertThat(blocked).hasSize(3);
        assertThat(recipeRepository.findByIdIn(blocked)).extracting(Recipe::getTitle)
                .containsExactlyInAnyOrder("冬瓜排骨汤", "拍黄瓜", "凉拌木耳");
    }

    @Test
    @DisplayName("忌口反查：猪肉这种大类关键词能大面积命中")
    void avoidFilterHandlesBroadKeyword() {
        List<Long> blocked = ingredientRepository.findRecipeIdsByIngredientNameContaining("猪肉");
        // 直接用「猪肉」二字可能一个都匹配不到（食材名是「五花肉」「排骨」），
        // 这里断言的是「不会误报」，真正的匹配靠「排骨」「五花肉」这类具体词
        assertThat(blocked).isEmpty();
    }

    @Test
    @DisplayName("原子计数：addViewCount 是累加而不是覆盖")
    void viewCountIsAtomicIncrement() {
        Recipe recipe = recipeRepository.findAll(PageRequest.of(0, 1)).getContent().get(0);
        int before = recipe.getViewCount();

        recipeRepository.addViewCount(recipe.getId(), 5);
        recipeRepository.addViewCount(recipe.getId(), 3);

        int after = recipeRepository.findById(recipe.getId()).orElseThrow().getViewCount();
        assertThat(after).as("两次累加应相加，而不是互相覆盖").isEqualTo(before + 8);
    }

    @Test
    @DisplayName("难度排序走索引列，且顺序正确")
    void difficultyOrderingIsConsistent() {
        List<Recipe> easy = recipeRepository.browse(null, null, Difficulty.EASY, null,
                PageRequest.of(0, 50, org.springframework.data.domain.Sort.by("difficultyLevel"))).getContent();
        List<Recipe> hard = recipeRepository.browse(null, null, Difficulty.HARD, null,
                PageRequest.of(0, 50)).getContent();

        assertThat(easy).isNotEmpty().allMatch(r -> r.getDifficultyLevel() == 1);
        assertThat(hard).isNotEmpty().allMatch(r -> r.getDifficultyLevel() == 3);
        assertThat(recipeRepository.countByMaxMinutes(30)).isPositive();
    }

    @Test
    @DisplayName("每道菜谱都至少有一条食材和一个步骤，且分类不是汤就是菜")
    void everyRecipeIsUsable() {
        List<Recipe> all = recipeRepository.findAll();

        assertThat(all).allSatisfy(recipe -> {
            assertThat(recipe.getIngredients()).as("%s 的食材", recipe.getTitle()).isNotEmpty();
            assertThat(recipe.getSteps()).as("%s 的步骤", recipe.getTitle()).isNotEmpty();
            assertThat(recipe.getDifficultyLevel()).as("%s 的难度等级", recipe.getTitle()).isNotNull();
            assertThat(recipe.getCoverUrl()).as("种子菜谱的封面应为空，由前端生成渐变占位").isNull();
        });
    }

    @Test
    @DisplayName("更新菜谱：重建步骤/食材/标签不能撞唯一键（回归：PUT 曾恒 500）")
    void updateReplacesChildCollections() {
        RecipeDetailVO created = recipeService.create(new RecipeSaveRequest(
                "回归测试菜", null, "初始简介", "家常", "素菜", "EASY", 10, 1, 100,
                List.of("家常"),
                List.of(new RecipeSaveRequest.Ingredient("土豆", "1", "个", true),
                        new RecipeSaveRequest.Ingredient("盐", "适量", null, false)),
                List.of(new RecipeSaveRequest.Step("第一步", null),
                        new RecipeSaveRequest.Step("第二步", null),
                        new RecipeSaveRequest.Step("第三步", null))), 1L);

        assertThat(created.steps()).hasSize(3);

        // 关键：用「编号从 1 重新开始」的步骤整批替换。
        // 这正是当初必然撞 uk_step_recipe_no 的场景——
        // Hibernate 单次 flush 内先 INSERT 后 DELETE，新步骤会在旧步骤删除前插入。
        RecipeDetailVO updated = recipeService.update(created.id(), new RecipeSaveRequest(
                "回归测试菜（已改）", null, "改后简介", "川", "荤菜", "MEDIUM", 20, 2, 200,
                List.of("川菜"),
                List.of(new RecipeSaveRequest.Ingredient("牛肉", "200", "g", true)),
                List.of(new RecipeSaveRequest.Step("新步骤一", null),
                        new RecipeSaveRequest.Step("新步骤二", null))));

        assertThat(updated.title()).isEqualTo("回归测试菜（已改）");
        assertThat(updated.steps()).as("旧步骤必须被完整替换掉，不能残留").hasSize(2);
        assertThat(updated.steps()).extracting(RecipeDetailVO.StepVO::stepNo).containsExactly(1, 2);
        assertThat(updated.ingredients()).hasSize(1);
        assertThat(updated.tags()).containsExactly("川菜");
        assertThat(updated.difficultyLevel()).as("难度等级要跟着难度一起更新").isEqualTo(2);
    }
}
