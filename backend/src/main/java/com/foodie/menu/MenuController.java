package com.foodie.menu;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodie.auth.SecurityUser;
import com.foodie.common.Result;
import com.foodie.menu.dto.MenuRequest;
import com.foodie.menu.dto.MenuResponse;
import com.foodie.menu.dto.ShoppingItem;
import com.foodie.menu.dto.ShoppingListRequest;
import com.foodie.price.DishCostService;
import com.foodie.price.dto.MenuCostRequest;
import com.foodie.price.dto.MenuCostVO;
import com.foodie.ratelimit.RateLimit;
import com.foodie.recipe.dto.RecipeCardVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/menu")
@Tag(name = "随机配餐", description = "按忌口与规格（最多十菜三汤）随机配一桌菜")
public class MenuController {

    private final MenuPlannerService menuPlannerService;
    private final ShoppingListService shoppingListService;
    private final DishCostService dishCostService;

    public MenuController(MenuPlannerService menuPlannerService,
                          ShoppingListService shoppingListService,
                          DishCostService dishCostService) {
        this.menuPlannerService = menuPlannerService;
        this.shoppingListService = shoppingListService;
        this.dishCostService = dishCostService;
    }

    @PostMapping("/random")
    @RateLimit("menu")
    @Operation(summary = "随机配餐",
            description = "dishCount 0-10、soupCount 0-3，两者不能同时为 0；"
                    + "avoid 会与账号里保存的忌口取并集；池子不足时通过 warnings 明确告知而不是报错")
    public Result<MenuResponse> random(@Valid @RequestBody MenuRequest request,
                                       @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(menuPlannerService.plan(user == null ? null : user.id(), request));
    }

    @PostMapping("/reroll")
    @RateLimit("menu")
    @Operation(summary = "换一道",
            description = "排除当前菜单里已有的菜，重新抽取；excludeIds 用逗号分隔")
    public Result<List<RecipeCardVO>> reroll(@Valid @RequestBody MenuRequest request,
                                             @RequestParam List<Long> excludeIds,
                                             @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(menuPlannerService.reroll(user == null ? null : user.id(), request, excludeIds));
    }

    @PostMapping("/shopping-list")
    @Operation(summary = "生成采购清单", description = "把整桌菜的食材按 名称+单位 合并去重，可累加的给出总量")
    public Result<List<ShoppingItem>> shoppingList(@Valid @RequestBody ShoppingListRequest request) {
        return Result.ok(shoppingListService.build(request.recipeIds()));
    }

    @PostMapping("/cost")
    @Operation(summary = "计算所选菜品的价格",
            description = "用户勾选要做的菜之后调用：返回每道菜的成本明细与总费用。"
                    + "未定价的食材不会瞎猜价格，而是通过 unpricedCount 明确报出来")
    public Result<MenuCostVO> cost(@Valid @RequestBody MenuCostRequest request) {
        return Result.ok(dishCostService.menuCost(request.recipeIds()));
    }
}
