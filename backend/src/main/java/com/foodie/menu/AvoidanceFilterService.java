package com.foodie.menu;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.config.CacheConfig;
import com.foodie.recipe.RecipeIngredientRepository;
import com.foodie.recipe.RecipeRepository;

/**
 * 忌口过滤。
 *
 * <p>一个关键词会把三类菜谱拉黑：
 * <ol>
 *   <li>食材名包含它（「花生」→ 花生米、花生碎）；</li>
 *   <li>标签包含它（过敏原往往看不出来，靠标签兜底）；</li>
 *   <li>菜名包含它（用户搜「不要香菜」时连「香菜拌牛肉」也要排除）。</li>
 * </ol>
 *
 * <p>结果按关键词缓存 30 分钟（见 {@link CacheConfig#AVOID_BLOCKED}）：
 * 忌口关键词是高度重复的（「香菜」「花生」会被大量用户用到），
 * 缓存后绝大多数配餐请求完全不需要碰 ingredient 表。
 */
@Service
public class AvoidanceFilterService {

    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;

    public AvoidanceFilterService(RecipeIngredientRepository ingredientRepository,
                                  RecipeRepository recipeRepository) {
        this.ingredientRepository = ingredientRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * 单个关键词命中的菜谱 id 集合。
     *
     * <p>返回不可变集合：缓存里放可变对象是跨请求脏数据的经典来源。
     */
    @Cacheable(value = CacheConfig.AVOID_BLOCKED, key = "#keyword", sync = true)
    @Transactional(readOnly = true)
    public Set<Long> blockedBy(String keyword) {
        Set<Long> blocked = new HashSet<>();
        blocked.addAll(ingredientRepository.findRecipeIdsByIngredientNameContaining(keyword));
        blocked.addAll(recipeRepository.findIdsByTagContaining(keyword));
        blocked.addAll(recipeRepository.findIdsByTitleContaining(keyword));
        return Set.copyOf(blocked);
    }

    /** 多个关键词取并集。放在这里但<b>不</b>调用本类的 {@link #blockedBy}，避免自调用绕过缓存代理。 */
    public Set<Long> unionOf(Iterable<Set<Long>> blockedSets) {
        Set<Long> union = new LinkedHashSet<>();
        for (Set<Long> blocked : blockedSets) {
            union.addAll(blocked);
        }
        return union;
    }

    /** 剔除 {@code %} 与 {@code _}：它们是 LIKE 的通配符，一个 "%" 就能让过滤条件失效。 */
    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replace("%", "").replace("_", "").replace("\\", "");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
