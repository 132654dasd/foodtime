package com.foodie.menu;

import java.util.List;

import com.foodie.recipe.RecipeCandidate;

/**
 * 配餐策略。
 *
 * <p>把「怎么挑」从「挑之前要准备什么」里剥出来：
 * 忌口过滤、候选池加载、汇总统计都由 {@link MenuPlannerService} 负责，
 * 策略只关心从给定的池子里选出哪几个 id。
 * 这样策略是纯函数式的，可以用单元测试直接覆盖，不必起 Spring 容器。
 */
public interface MenuStrategy {

    MenuStrategyType type();

    /**
     * @param dishPool  已剔除忌口的菜池（荤菜/素菜/凉菜/主食/甜品）
     * @param soupPool  已剔除忌口的汤池
     * @param dishCount 需要几道菜
     * @param soupCount 需要几道汤
     */
    Selection pick(List<RecipeCandidate> dishPool, List<RecipeCandidate> soupPool, int dishCount, int soupCount);

    /**
     * 选择结果。
     *
     * @param dishIds  选中的菜 id，按抽取顺序
     * @param soupIds  选中的汤 id
     * @param warnings 池子不足等降级说明，直接透传给用户而不是静默糊弄
     */
    record Selection(List<Long> dishIds, List<Long> soupIds, List<String> warnings) {
    }
}
