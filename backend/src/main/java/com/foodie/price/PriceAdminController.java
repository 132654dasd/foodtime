package com.foodie.price;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodie.auth.SecurityUser;
import com.foodie.common.PageResult;
import com.foodie.common.Result;
import com.foodie.price.dto.IngredientPriceVO;
import com.foodie.price.dto.PriceCreateRequest;
import com.foodie.price.dto.PriceUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 食材价格管理（仅管理员）。
 *
 * <p>需求原文是「菜的价格只能让管理员修改」，落到代码上是三重保证：
 * <ol>
 *   <li>URL 前缀 {@code /api/admin/**} 在 SecurityConfig 里整体限定 ADMIN；</li>
 *   <li>类上再挂 {@code @PreAuthorize}，即使将来 URL 规则被改错也仍然拦得住；</li>
 *   <li>普通用户走的是另一个只读控制器 {@link PriceController}，物理上没有写入口。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/ingredient-prices")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "食材价格管理", description = "仅管理员可维护价格")
public class PriceAdminController {

    private final PriceService priceService;

    public PriceAdminController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping
    @Operation(summary = "价格列表", description = "含停用条目，供管理员核对")
    public Result<PageResult<IngredientPriceVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(priceService.search(keyword, category, page, size));
    }

    @GetMapping("/categories")
    @Operation(summary = "分类字典")
    public Result<List<PriceService.CategoryOption>> categories() {
        return Result.ok(priceService.categories());
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改价格", description = "只更新传了值的字段；修改后价格字典缓存整体失效")
    public Result<IngredientPriceVO> update(@PathVariable Long id,
                                            @Valid @RequestBody PriceUpdateRequest request,
                                            @AuthenticationPrincipal SecurityUser operator) {
        return Result.ok(priceService.update(id, request, operator == null ? null : operator.id()));
    }

    @PostMapping
    @Operation(summary = "新增价格", description = "为还没定价的新食材补一条价目")
    public Result<IngredientPriceVO> create(@Valid @RequestBody PriceCreateRequest request,
                                            @AuthenticationPrincipal SecurityUser operator) {
        return Result.ok(priceService.create(request, operator == null ? null : operator.id()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除价格", description = "删除后该食材将视为未定价，不参与成本计算")
    public Result<Void> delete(@PathVariable Long id) {
        priceService.delete(id);
        return Result.ok();
    }
}
