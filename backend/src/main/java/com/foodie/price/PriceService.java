package com.foodie.price;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.common.BizException;
import com.foodie.common.PageResult;
import com.foodie.config.CacheConfig;
import com.foodie.price.dto.IngredientPriceVO;
import com.foodie.price.dto.PriceCreateRequest;
import com.foodie.price.dto.PriceUpdateRequest;

/**
 * 食材价格的读写。
 *
 * <p>价格是<b>全局共享</b>的数据，所以所有写操作都要让价格字典缓存整体失效——
 * 不像菜谱详情可以按 id 精确失效。价格条目只有一百多条、
 * 改价又是低频操作，整体失效的代价远小于维护精确失效的复杂度。
 *
 * <p>权限不在这里判断：{@code /api/admin/**} 由 SecurityConfig 拦成 ADMIN，
 * 这里再挂方法级注解是双保险（见 PriceAdminController）。
 */
@Service
public class PriceService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final IngredientPriceRepository repository;

    public PriceService(IngredientPriceRepository repository) {
        this.repository = repository;
    }

    // ============================================================ 查询

    /** 后台价格列表：支持按分类与关键词筛选，走数据库分页。 */
    @Transactional(readOnly = true)
    public PageResult<IngredientPriceVO> search(String keyword, String categoryRaw, int page, int size) {
        IngredientCategory category = parseCategory(categoryRaw);
        String kw = blankToNull(keyword);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size));

        Page<IngredientPrice> result;
        if (kw == null && category == null) {
            result = repository.findAllByOrderByCategoryAscNameAsc(pageable);
        } else if (kw == null) {
            result = repository.findByCategoryOrderByNameAsc(category, pageable);
        } else if (category == null) {
            result = repository.findByNameContainingOrderByCategoryAscNameAsc(kw, pageable);
        } else {
            result = repository.findByCategoryAndNameContainingOrderByNameAsc(category, kw, pageable);
        }
        return PageResult.of(result, IngredientPriceVO::of);
    }

    /**
     * 公开价格看板：只返回启用中的价目。
     *
     * <p>刻意在内存里筛选与分页，而不是再写一套带 {@code enabled} 条件的派生查询：
     * 价目字典总共一百多条、又不常变，一次全量取回（走覆盖索引）
     * 比为了分页多维护四个 Repository 方法划算得多。
     */
    @Transactional(readOnly = true)
    public PageResult<IngredientPriceVO> listPublic(String keyword, String categoryRaw, int page, int size) {
        IngredientCategory category = parseCategory(categoryRaw);
        String kw = blankToNull(keyword);

        List<IngredientPrice> filtered = repository.findByEnabledOrderByCategoryAscNameAsc(1).stream()
                .filter(price -> category == null || price.getCategory() == category)
                .filter(price -> kw == null || price.getName().contains(kw))
                .toList();

        int safeSize = clampSize(size);
        int safePage = Math.max(page, 1);
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        List<IngredientPriceVO> content = filtered.subList(from, to).stream()
                .map(IngredientPriceVO::of)
                .toList();
        int pages = (int) Math.ceil((double) filtered.size() / safeSize);
        return new PageResult<>(content, filtered.size(), safePage, safeSize, pages);
    }

    // ============================================================ 写（管理员）

    @Transactional
    @CacheEvict(value = CacheConfig.PRICE_CATALOG, allEntries = true)
    public IngredientPriceVO update(Long id, PriceUpdateRequest request, Long operatorId) {
        IngredientPrice price = repository.findById(id)
                .orElseThrow(() -> BizException.notFound("价格条目不存在"));

        if (request.unitPrice() != null) {
            price.setUnitPrice(request.unitPrice());
        }
        if (blankToNull(request.priceUnit()) != null) {
            price.setPriceUnit(request.priceUnit().trim());
        }
        if (blankToNull(request.category()) != null) {
            price.setCategory(parseCategory(request.category()));
        }
        if (request.gramsPerUnit() != null) {
            price.setGramsPerUnit(request.gramsPerUnit());
        }
        if (request.note() != null) {
            price.setNote(blankToNull(request.note()));
        }
        if (request.enabled() != null) {
            price.setEnabled(Boolean.TRUE.equals(request.enabled()) ? 1 : 0);
        }
        price.setUpdatedBy(operatorId);
        return IngredientPriceVO.of(repository.saveAndFlush(price));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.PRICE_CATALOG, allEntries = true)
    public IngredientPriceVO create(PriceCreateRequest request, Long operatorId) {
        String name = request.name().trim();
        if (repository.existsByName(name)) {
            throw BizException.badRequest("食材「%s」已经有价格了，请直接修改".formatted(name));
        }
        IngredientPrice price = new IngredientPrice();
        price.setName(name);
        price.setUnitPrice(request.unitPrice());
        price.setPriceUnit(request.priceUnit().trim());
        price.setCategory(request.category() == null
                ? IngredientCategory.OTHER
                : parseCategory(request.category()));
        price.setGramsPerUnit(request.gramsPerUnit());
        price.setNote(blankToNull(request.note()));
        price.setEnabled(1);
        price.setUpdatedBy(operatorId);
        return IngredientPriceVO.of(repository.saveAndFlush(price));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.PRICE_CATALOG, allEntries = true)
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw BizException.notFound("价格条目不存在");
        }
        repository.deleteById(id);
    }

    // ============================================================ 内部

    private IngredientCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return IngredientCategory.parse(raw);
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest(ex.getMessage());
        }
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** 供前端渲染分类筛选：含中文标签与枚举名。 */
    public List<CategoryOption> categories() {
        return java.util.Arrays.stream(IngredientCategory.values())
                .map(category -> new CategoryOption(category.name(), category.label()))
                .toList();
    }

    /** 分类选项。 */
    public record CategoryOption(String value, String label) {
    }

    /** 保留给将来的批量改价：一次性把多个食材调到同一价格。 */
    public BigDecimal currentPriceOf(String name) {
        return repository.findByName(name).map(IngredientPrice::getUnitPrice).orElse(null);
    }
}
