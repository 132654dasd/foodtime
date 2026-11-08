package com.foodie.fridge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.common.BizException;
import com.foodie.fridge.dto.CatalogItemVO;
import com.foodie.fridge.dto.FridgeItemRequest;
import com.foodie.fridge.dto.FridgeItemVO;
import com.foodie.fridge.dto.FridgeUpdateRequest;
import com.foodie.price.PriceCatalogService;
import com.foodie.price.PriceSnapshot;
import com.foodie.price.UnitConverter;
import com.foodie.recipe.IngredientUsage;
import com.foodie.recipe.RecipeIngredientRepository;

/**
 * 冰箱的增删改查 + 食材字典。
 *
 * <p>三个刻意的决定：
 * <ol>
 *   <li><b>单位在入口处就归一化</b>（复用 {@link UnitConverter#normalize}）：
 *       「克」「G」「g」存进库都是 {@code g}，否则唯一键和数量换算都会被同一食材的
 *       不同写法搞乱。</li>
 *   <li><b>重复录入直接报 409，而不是静默覆盖或累加</b>：
 *       「再买了一次番茄」和「上次填错了」在语义上无法区分，
 *       猜错的代价是库存数量长期失真。让用户明确改数量，比自作聪明可靠。</li>
 *   <li><b>{@code known} 字段用匹配器判断，不是字符串相等</b>：
 *       用户填「五花肉」而菜谱写「带皮五花肉」，这属于「能用到」，
 *       若按相等判断会显示成「没有菜谱用到」，用户会以为功能坏了。</li>
 * </ol>
 */
@Service
public class FridgeService {

    /** 单个用户的冰箱条目上限，防止脚本灌数据把推荐的循环拖慢。 */
    private static final int MAX_ITEMS = 200;

    /** 过期日升序；没有过期日的排最后（MySQL 默认把 NULL 排最前，这里要显式纠正）。 */
    private static final Comparator<FridgeItem> EXPIRE_ORDER = Comparator
            .comparing(FridgeItem::getExpireAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FridgeItem::getName);

    private final FridgeItemRepository repository;
    private final RecipeIngredientRepository ingredientRepository;
    private final PriceCatalogService priceCatalog;

    public FridgeService(FridgeItemRepository repository,
                         RecipeIngredientRepository ingredientRepository,
                         PriceCatalogService priceCatalog) {
        this.repository = repository;
        this.ingredientRepository = ingredientRepository;
        this.priceCatalog = priceCatalog;
    }

    // ============================================================ 查询

    @Transactional(readOnly = true)
    public List<FridgeItemVO> list(Long userId) {
        List<FridgeItem> items = repository.findByUserIdOrderByNameAsc(userId);
        return toVOs(items);
    }

    /**
     * 食材字典：菜谱里出现过的全部食材名 + 各自被多少道菜用到。
     *
     * <p>不缓存：一次 {@code group by} 扫描约 1200 行，毫秒级；
     * 而缓存需要在「管理员改菜谱」的三处写操作上做失效，收益远小于维护成本。
     */
    @Transactional(readOnly = true)
    public List<CatalogItemVO> catalog() {
        Map<String, PriceSnapshot> prices = priceCatalog.enabledByName();
        return ingredientRepository.findUsage().stream()
                .sorted(Comparator.comparing(IngredientUsage::recipeCount).reversed()
                        .thenComparing(IngredientUsage::name))
                .map(usage -> new CatalogItemVO(
                        usage.name(),
                        usage.recipeCount(),
                        categoryLabel(prices, usage.name())))
                .toList();
    }

    // ============================================================ 写入

    @Transactional
    public FridgeItemVO add(Long userId, FridgeItemRequest request) {
        if (repository.countByUserId(userId) >= MAX_ITEMS) {
            throw BizException.badRequest("冰箱最多放 " + MAX_ITEMS + " 样食材，先清理一些吧");
        }
        String name = request.name().trim();
        String unit = normalizeUnit(request.unit());
        if (repository.existsByUserIdAndNameAndUnit(userId, name, unit)) {
            throw BizException.conflict("冰箱里已经有「" + name + "」了，直接改数量就好");
        }

        FridgeItem item = new FridgeItem();
        item.setUserId(userId);
        item.setName(name);
        item.setQuantity(request.quantity());
        item.setUnit(unit);
        item.setExpireAt(request.expireAt());
        repository.save(item);
        return toVOs(List.of(item)).get(0);
    }

    /**
     * 整体替换一条冰箱食材（PUT 语义）。
     *
     * <p>三个字段都以请求为准，null 即清空——这样「把数量改成不限」
     * 与「不改数量」能被明确区分，不需要额外的标志位。
     */
    @Transactional
    public FridgeItemVO update(Long userId, Long id, FridgeUpdateRequest request) {
        FridgeItem item = requireOwned(userId, id);

        String unit = normalizeUnit(request.unit());
        if (repository.existsByUserIdAndNameAndUnitAndIdNot(userId, item.getName(), unit, id)) {
            throw BizException.conflict("冰箱里已经有「" + item.getName() + "」这个单位了，改数量就好");
        }

        item.setQuantity(request.quantity());
        item.setUnit(unit);
        item.setExpireAt(request.expireAt());
        repository.save(item);
        return toVOs(List.of(item)).get(0);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(requireOwned(userId, id));
    }

    // ============================================================ 内部

    private FridgeItem requireOwned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> BizException.notFound("冰箱里没有这样食材"));
    }

    /** 单位在这里就归一化成换算器认识的写法，避免「克 / g / G」在库里变成三种东西。 */
    private static String normalizeUnit(String unit) {
        return UnitConverter.normalize(unit);
    }

    private List<FridgeItemVO> toVOs(List<FridgeItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<String> ingredientNames = ingredientRepository.findUsage().stream()
                .map(IngredientUsage::name)
                .toList();
        LocalDate today = LocalDate.now();
        return items.stream()
                .sorted(EXPIRE_ORDER)
                .map(item -> toVO(item, ingredientNames, today))
                .toList();
    }

    private FridgeItemVO toVO(FridgeItem item, List<String> ingredientNames, LocalDate today) {
        boolean expired = item.getExpireAt() != null && item.getExpireAt().isBefore(today);
        boolean expiringSoon = item.getExpireAt() != null
                && !item.getExpireAt().isAfter(today.plusDays(RecipeMatcher.EXPIRING_DAYS));
        return new FridgeItemVO(
                item.getId(),
                item.getName(),
                item.getQuantity(),
                item.getUnit(),
                item.getExpireAt(),
                expiringSoon,
                expired,
                isKnown(item.getName(), ingredientNames));
    }

    /** 与菜谱食材库的任一食材能匹配上（精确或近似）就算「认识」。 */
    private static boolean isKnown(String fridgeName, List<String> ingredientNames) {
        String canonical = IngredientMatcher.canonical(fridgeName);
        if (canonical.isEmpty()) {
            return false;
        }
        for (String ingredientName : ingredientNames) {
            if (IngredientMatcher.match(fridgeName, ingredientName) != null) {
                return true;
            }
        }
        return false;
    }

    private static String categoryLabel(Map<String, PriceSnapshot> prices, String name) {
        PriceSnapshot snapshot = prices.get(name);
        return snapshot == null || snapshot.category() == null ? null : snapshot.category().label();
    }

    /**
     * 冰箱里「没有任何菜谱用到」的食材名。
     *
     * <p>单独抽出来是因为它在两个地方都要用（列表页提示、推荐结果警告），
     * 而且逻辑必须与匹配器保持一致，不能各写一套。
     */
    static List<String> unusedNames(List<FridgeItem> items, List<String> ingredientNames) {
        Set<String> unused = new LinkedHashSet<>();
        for (FridgeItem item : items) {
            if (!isKnown(item.getName(), ingredientNames)) {
                unused.add(item.getName());
            }
        }
        return new ArrayList<>(unused);
    }
}
