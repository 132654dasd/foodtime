package com.foodie.price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.common.BizException;
import com.foodie.price.dto.CostIngredient;
import com.foodie.price.dto.DishCostVO;
import com.foodie.price.dto.MenuCostVO;
import com.foodie.recipe.Recipe;
import com.foodie.recipe.RecipeIngredient;
import com.foodie.recipe.RecipeRepository;

/**
 * 做菜成本计算。
 *
 * <p>公式很朴素：<b>单道菜成本 = Σ(食材用量换算成计价单位后 × 单价)</b>，
 * 总费用 = 所选菜品成本之和。真正的难点全在「量纲统一」上，
 * 那部分被隔离在 {@link UnitConverter} 与 {@link AmountParser} 里。
 *
 * <p>设计上刻意接受「算不全」：没有价目的食材不会瞎猜一个价格，
 * 而是把 {@code unpricedCount} 报上去，让前端明确告诉用户
 * 「这道菜还有 2 项未定价」。一个看起来精确、实际漏项的金额比一个诚实的区间更有害。
 */
@Service
public class DishCostService {

    /** 一次最多算 30 道菜，防止有人塞几千个 id。 */
    private static final int MAX_RECIPES = 30;
    private static final int MONEY_SCALE = 2;

    private final PriceCatalogService catalog;
    private final RecipeRepository recipeRepository;

    public DishCostService(PriceCatalogService catalog, RecipeRepository recipeRepository) {
        this.catalog = catalog;
        this.recipeRepository = recipeRepository;
    }

    /**
     * 计算单道菜的成本。
     *
     * <p>不查数据库取食材，而是接受调用方已经拿到的食材行——
     * 这样菜谱详情页（食材来自缓存 VO）和菜单成本（食材来自实体）能复用同一套算法，
     * 也避免为了算钱再查一次食材表。
     */
    public DishCostVO costOf(Long recipeId, String title, Integer servings, List<CostIngredient> items) {
        Map<String, PriceSnapshot> prices = catalog.enabledByName();

        BigDecimal total = BigDecimal.ZERO;
        int priced = 0;
        int unpriced = 0;
        int estimated = 0;
        List<DishCostVO.Line> lines = new ArrayList<>(items.size());

        for (CostIngredient item : items) {
            PriceSnapshot price = prices.get(item.name());
            AmountParser.Parsed parsed = AmountParser.parse(item.amount());

            BigDecimal quantity = null;
            if (price != null) {
                quantity = parsed.estimated()
                        // 「少许/适量」已经被折算成克，直接从克换算到计价单位
                        ? UnitConverter.fromGrams(parsed.vagueGrams(), price.priceUnit(), price.gramsPerUnit())
                        : UnitConverter.convert(parsed.quantity(), item.unit(),
                                price.priceUnit(), price.gramsPerUnit());
            }

            boolean ok = quantity != null;
            BigDecimal cost = null;
            if (ok) {
                cost = quantity.multiply(price.unitPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                total = total.add(cost);
                priced++;
            } else {
                unpriced++;
            }
            if (parsed.estimated()) {
                estimated++;
            }

            lines.add(new DishCostVO.Line(
                    item.name(),
                    item.amount(),
                    item.unit(),
                    price == null ? null : price.category().label(),
                    quantity,
                    price == null ? null : price.priceUnit(),
                    price == null ? null : price.unitPrice(),
                    cost,
                    ok,
                    parsed.estimated()));
        }

        BigDecimal servingCost = null;
        if (servings != null && servings > 0) {
            servingCost = total.divide(BigDecimal.valueOf(servings), MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return new DishCostVO(recipeId, title, servings, total, servingCost,
                priced, unpriced, estimated, unpriced == 0, lines);
    }

    /** 计算用户选中的这一批菜品的总费用。 */
    @Transactional(readOnly = true)
    public MenuCostVO menuCost(List<Long> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            throw BizException.badRequest("请先选择要制作的菜");
        }
        List<Long> ids = recipeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(MAX_RECIPES)
                .toList();

        // 一次批量取回，避免 N 次查询；id → 实体 的映射不受数据库返回顺序影响
        Map<Long, Recipe> byId = new HashMap<>();
        recipeRepository.findByIdIn(ids).forEach(recipe -> byId.put(recipe.getId(), recipe));

        List<DishCostVO> dishes = new ArrayList<>(ids.size());
        BigDecimal total = BigDecimal.ZERO;
        int pricedDishes = 0;
        int unpricedIngredients = 0;
        int totalServings = 0;
        for (Long id : ids) {
            Recipe recipe = byId.get(id);
            if (recipe == null) {
                continue;
            }
            dishes.add(costOf(recipe.getId(), recipe.getTitle(), recipe.getServings(), toItems(recipe)));
        }

        for (DishCostVO dish : dishes) {
            total = total.add(dish.totalCost());
            unpricedIngredients += dish.unpricedCount();
            if (dish.complete()) {
                pricedDishes++;
            }
            if (dish.servings() != null) {
                totalServings += dish.servings();
            }
        }

        BigDecimal perServing = totalServings > 0
                ? total.divide(BigDecimal.valueOf(totalServings), MONEY_SCALE, RoundingMode.HALF_UP)
                : null;

        List<String> warnings = new ArrayList<>();
        if (unpricedIngredients > 0) {
            warnings.add("有 %d 项食材尚未定价，未计入总费用（管理员可在后台补录）".formatted(unpricedIngredients));
        }
        if (dishes.size() < ids.size()) {
            warnings.add("部分菜品不存在或已下架，已跳过");
        }

        return new MenuCostVO(dishes, total, perServing, dishes.size(), pricedDishes,
                unpricedIngredients, List.copyOf(warnings));
    }

    /** 从菜谱实体取出算钱需要的三要素；懒加载在此处一次性展开。 */
    public List<CostIngredient> toItems(Recipe recipe) {
        return recipe.getIngredients().stream()
                .map(this::toItem)
                .toList();
    }

    public CostIngredient toItem(RecipeIngredient ingredient) {
        return new CostIngredient(ingredient.getName(), ingredient.getAmount(), ingredient.getUnit());
    }
}
