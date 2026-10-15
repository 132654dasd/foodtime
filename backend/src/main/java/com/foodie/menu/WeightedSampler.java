package com.foodie.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;

import com.foodie.recipe.RecipeCandidate;

/**
 * 加权随机抽样（不放回）。
 *
 * <p>实现方式是「前缀和 + 二分查找」：
 * 先把权重累加成单调递增数组，再取一个 {@code [0, total)} 的随机数，
 * 二分定位它落在哪个区间。单轮 O(n)，比反复重排整个池子稳定得多。
 *
 * <p>所有候选的权重会被抬到至少 {@link #MIN_WEIGHT}：
 * 否则权重算出来恰好为 0 的菜会永远抽不到，这不符合「随机」的预期。
 */
public final class WeightedSampler {

    private static final double MIN_WEIGHT = 0.05;

    private WeightedSampler() {
    }

    /** 热度权重：用对数压缩，避免一道爆款菜长期霸占菜单。 */
    public static double heatWeight(RecipeCandidate candidate) {
        int views = candidate.viewCount() == null ? 0 : candidate.viewCount();
        int favorites = candidate.favoriteCount() == null ? 0 : candidate.favoriteCount();
        return 1.0 + Math.log1p(views) / 5.0 + Math.log1p(favorites) / 3.0;
    }

    /** 快手权重：耗时越短权重越高；用柔和的反比而非直接取倒数，避免极端倾斜。 */
    public static double quickWeight(RecipeCandidate candidate) {
        int minutes = candidate.cookMinutes() == null ? 60 : Math.max(candidate.cookMinutes(), 1);
        return 1.0 / (1.0 + minutes / 30.0);
    }

    /** 等概率权重。 */
    public static double uniformWeight(RecipeCandidate candidate) {
        return 1.0;
    }

    /**
     * 从候选池中不放回地抽 k 个。
     *
     * @param pool    候选池；方法内部会复制，不会修改入参
     * @param k       目标数量；池子不足时返回池中全部
     * @param weigher 权重函数
     * @return 被选中的 id，按抽取顺序
     */
    public static List<Long> sample(List<RecipeCandidate> pool, int k, ToDoubleFunction<RecipeCandidate> weigher) {
        List<RecipeCandidate> remaining = new ArrayList<>(pool);
        List<Long> picked = new ArrayList<>(Math.min(k, remaining.size()));

        for (int round = 0; round < k && !remaining.isEmpty(); round++) {
            double total = 0.0;
            double[] prefix = new double[remaining.size()];
            for (int i = 0; i < remaining.size(); i++) {
                total += Math.max(weigher.applyAsDouble(remaining.get(i)), MIN_WEIGHT);
                prefix[i] = total;
            }
            double target = ThreadLocalRandom.current().nextDouble(total);
            picked.add(remaining.remove(lowerBound(prefix, target)).id());
        }
        return picked;
    }

    /** 找到第一个 {@code prefix[i] > target} 的下标，即随机数落入的区间。 */
    private static int lowerBound(double[] prefix, double target) {
        int low = 0;
        int high = prefix.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (prefix[mid] <= target) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
