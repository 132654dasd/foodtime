package com.foodie.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodie.auth.SecurityUser;
import com.foodie.common.PageResult;
import com.foodie.common.Result;
import com.foodie.recipe.RecipeService;
import com.foodie.recipe.dto.RecipeCardVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 个人中心相关接口。
 *
 * <p>路径刻意放在 {@code /api/users/me/**} 而不是 {@code /api/recipes/favorites}：
 * 后者会与「菜谱读接口对游客开放」的规则（{@code GET /api/recipes/**} → permitAll）撞车，
 * 需要在安全配置里靠规则顺序去救，既脆弱又难维护。
 * 换个路径前缀，权限规则就变回一条直白的事实。
 */
@RestController
@RequestMapping("/api/users/me")
@Tag(name = "个人中心")
public class UserController {

    private final RecipeService recipeService;

    public UserController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/favorites")
    @Operation(summary = "我的收藏")
    public Result<PageResult<RecipeCardVO>> favorites(@AuthenticationPrincipal SecurityUser user,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "12") int size) {
        return Result.ok(recipeService.listFavorites(user.id(), page, size));
    }
}
