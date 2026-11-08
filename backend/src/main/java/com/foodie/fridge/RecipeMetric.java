package com.foodie.fridge;

import java.math.BigDecimal;

/**
 * 排序用的菜谱标量。
 *
 * <p>为什么把它单独提出来：匹配算法本身只看食材，不该知道菜谱评分；
 * 但最终排序又需要「缺的样数一样多时，评分高的排前面」。
 * 把评分/浏览量作为外部度量传入，匹配器就能保持纯函数、可单元测试，
 * 而排序依然完整。
 *
 * @param ratingAvg 平均评分，可为 0
 * @param viewCount 浏览量，可为 0
 */
public record RecipeMetric(BigDecimal ratingAvg, Integer viewCount) {

    public BigDecimal rating() {
        return ratingAvg == null ? BigDecimal.ZERO : ratingAvg;
    }

    public int views() {
        return viewCount == null ? 0 : viewCount;
    }
}
