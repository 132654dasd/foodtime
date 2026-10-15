package com.foodie.recipe;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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
import com.foodie.recipe.dto.RecipeCardVO;
import com.foodie.recipe.dto.RecipeDetailVO;
import com.foodie.recipe.dto.RecipeMetaVO;
import com.foodie.recipe.dto.RecipeQuery;
import com.foodie.recipe.dto.RecipeSaveRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/recipes")
@Tag(name = "菜谱库", description = "检索、详情、收藏、评分；写操作仅管理员")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    @Operation(summary = "菜谱列表", description = "支持关键词（走 MySQL ngram 全文索引）、菜系、分类、难度、耗时与多种排序；无需登录")
    public Result<PageResult<RecipeCardVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cuisine,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) @Min(1) @Max(2880) Integer maxMinutes,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(60) int size,
            @AuthenticationPrincipal SecurityUser currentUser) {

        RecipeQuery query = new RecipeQuery(
                keyword,
                cuisine,
                category == null ? null : DishCategory.fromLabel(category),
                difficulty == null ? null : Difficulty.valueOf(difficulty.trim().toUpperCase()),
                maxMinutes,
                sort);

        return Result.ok(recipeService.browse(query, page, size, currentUser == null ? null : currentUser.id()));
    }

    @GetMapping("/hot")
    @Operation(summary = "热门菜谱", description = "首页用，走缓存")
    public Result<List<RecipeCardVO>> hot(@RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return Result.ok(recipeService.hot(limit));
    }

    @GetMapping("/meta")
    @Operation(summary = "筛选字典", description = "菜系、分类、难度、标签与忌口建议，长缓存")
    public Result<RecipeMetaVO> meta() {
        return Result.ok(recipeService.meta());
    }

    @GetMapping("/{id}")
    @Operation(summary = "菜谱详情", description = "含食材与步骤；命中缓存时不会查库")
    public Result<RecipeDetailVO> detail(@PathVariable Long id,
                                         @AuthenticationPrincipal SecurityUser currentUser) {
        return Result.ok(recipeService.detailFor(id, currentUser == null ? null : currentUser.id()));
    }

    // ------------------------------------------------------------ 收藏

    @PostMapping("/{id}/favorite")
    @Operation(summary = "收藏菜谱", description = "幂等：重复收藏不会重复计数")
    public Result<Boolean> favorite(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(recipeService.addFavorite(user.id(), id));
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "取消收藏")
    public Result<Boolean> unfavorite(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(recipeService.removeFavorite(user.id(), id));
    }

    // ------------------------------------------------------------ 评分

    @PostMapping("/{id}/rating")
    @Operation(summary = "评分", description = "1-5 星；同一用户重复评分是改分")
    public Result<RecipeDetailVO> rate(@PathVariable Long id,
                                       @Valid @RequestBody RatingRequest request,
                                       @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(recipeService.rate(user.id(), id, request.score()));
    }

    // ------------------------------------------------------------ 管理员

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "新增菜谱（仅管理员）")
    public Result<RecipeDetailVO> create(@Valid @RequestBody RecipeSaveRequest request,
                                         @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(recipeService.create(request, user.id()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "修改菜谱（仅管理员）")
    public Result<RecipeDetailVO> update(@PathVariable Long id,
                                         @Valid @RequestBody RecipeSaveRequest request) {
        return Result.ok(recipeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除菜谱（仅管理员）")
    public Result<Void> delete(@PathVariable Long id) {
        recipeService.delete(id);
        return Result.ok();
    }

    /** 评分入参。 */
    public record RatingRequest(
            @Min(value = 1, message = "评分最低 1 星")
            @Max(value = 5, message = "评分最高 5 星")
            int score) {
    }
}
