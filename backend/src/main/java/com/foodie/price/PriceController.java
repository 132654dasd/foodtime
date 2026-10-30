package com.foodie.price;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodie.common.PageResult;
import com.foodie.common.Result;
import com.foodie.price.dto.IngredientPriceVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 食材/蔬菜价格看板（公开只读）。
 *
 * <p>为什么不要求登录：价格是透明信息，让游客也能先看看「这一桌菜大概多少钱」
 * 反而是产品吸引力的一部分。真正的写权限在 {@link PriceAdminController} 里。
 */
@RestController
@RequestMapping("/api/prices")
@Tag(name = "食材价格", description = "公开查看食材与蔬菜价格（只读）")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/categories")
    @Operation(summary = "食材分类字典", description = "公开；前端筛选栏用")
    public Result<List<PriceService.CategoryOption>> categories() {
        return Result.ok(priceService.categories());
    }

    @GetMapping("/ingredients")
    @Operation(summary = "食材价格看板",
            description = "只返回启用中的价目；category 传 VEGETABLE 或「蔬菜」都行；"
                    + "keyword 按食材名模糊匹配")
    public Result<PageResult<IngredientPriceVO>> ingredients(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(priceService.listPublic(keyword, category, page, size));
    }
}
