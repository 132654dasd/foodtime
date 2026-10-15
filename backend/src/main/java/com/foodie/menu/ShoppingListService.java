package com.foodie.menu;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.common.BizException;
import com.foodie.menu.dto.ShoppingItem;
import com.foodie.recipe.Recipe;
import com.foodie.recipe.RecipeIngredient;
import com.foodie.recipe.RecipeRepository;

/**
 * 购物清单：把一桌菜的食材合并去重。
 *
 * <p>同一份食材在「番茄炒蛋」写 2 个、「番茄炖牛腩」写 3 个，
 * 采购时应该看到「番茄 5 个」。这就是这个服务的全部价值——
 * 把用户从「对着四五道菜手动加食材」里解放出来。
 *
 * <p>合并键是 (食材名, 单位)：同名但单位不同（「番茄 2 个」vs「番茄 200 g」）
 * 不能相加，只能并列展示，否则会算出一个没有意义的数字。
 */
@Service
public class ShoppingListService {

    /** 单次最多合并多少道菜，防止有人塞 1000 个 id 把服务拖垮。 */
    private static final int MAX_RECIPES = 20;

    private final RecipeRepository recipeRepository;

    public ShoppingListService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @Transactional(readOnly = true)
    public List<ShoppingItem> build(List<Long> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            throw BizException.badRequest("请先选择菜品再生成采购清单");
        }
        List<Long> ids = recipeIds.stream().filter(java.util.Objects::nonNull).distinct().limit(MAX_RECIPES).toList();

        // key = 名称 + '\u0001' + 单位（用不可见分隔符，避免食材名里出现空格导致拼接歧义）
        Map<String, Accumulator> merged = new LinkedHashMap<>();
        for (Recipe recipe : recipeRepository.findByIdIn(ids)) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                String unit = ingredient.getUnit() == null ? "" : ingredient.getUnit();
                String key = ingredient.getName() + '\u0001' + unit;
                merged.computeIfAbsent(key, ignored -> new Accumulator(ingredient.getName(), unit))
                        .add(ingredient.getAmount(), recipe.getTitle());
            }
        }

        List<ShoppingItem> items = new ArrayList<>(merged.size());
        for (Accumulator accumulator : merged.values()) {
            items.add(accumulator.toItem());
        }
        // 主料优先（用量可累加且数值大的排前面），再按名称，方便按清单采购
        items.sort(Comparator
                .comparing(ShoppingItem::mergeable).reversed()
                .thenComparing(item -> item.usedIn().size(), Comparator.reverseOrder())
                .thenComparing(ShoppingItem::name));
        return items;
    }

    /** 累积器：可累加时求和，不可累加时收集去重后的写法。 */
    private static final class Accumulator {

        private final String name;
        private final String unit;
        private final Set<String> rawAmounts = new LinkedHashSet<>();
        private final Set<String> usedIn = new LinkedHashSet<>();
        private BigDecimal sum = BigDecimal.ZERO;
        private boolean deprecatedNumeric = true;

        private Accumulator(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }

        private void add(String amount, String recipeTitle) {
            if (recipeTitle != null) {
                usedIn.add(recipeTitle);
            }
            if (amount == null || amount.isBlank()) {
                deprecatedNumeric = false;
                return;
            }
            String trimmed = amount.trim();
            try {
                sum = sum.add(new BigDecimal(trimmed));
            } catch (NumberFormatException ex) {
                // 「适量」「少许」这类无法参与计算，保留原文
                deprecatedNumeric = false;
                rawAmounts.add(trimmed);
            }
        }

        private ShoppingItem toItem() {
            if (deprecatedNumeric && sum.signum() > 0) {
                return new ShoppingItem(name, sum.stripTrailingZeros().toPlainString(),
                        unit.isEmpty() ? null : unit, true, List.copyOf(usedIn));
            }
            String amount = rawAmounts.isEmpty() ? "适量" : String.join("、", rawAmounts);
            return new ShoppingItem(name, amount, unit.isEmpty() ? null : unit, false, List.copyOf(usedIn));
        }
    }
}
