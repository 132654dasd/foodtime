package com.foodie.fridge;

import java.util.List;

import jakarta.validation.Valid;

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
import com.foodie.common.Result;
import com.foodie.fridge.dto.CatalogItemVO;
import com.foodie.fridge.dto.FridgeItemRequest;
import com.foodie.fridge.dto.FridgeItemVO;
import com.foodie.fridge.dto.FridgeSuggestionVO;
import com.foodie.fridge.dto.FridgeUpdateRequest;
import com.foodie.ratelimit.RateLimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 我的冰箱。
 *
 * <p>权限完全靠现有规则兜住：{@code SecurityConfig} 里
 * {@code GET /api/recipes/**} 与 {@code /api/prices/**} 是公开的，
 * 其余 {@code /api/**} 一律要求登录，而 {@code /api/fridge/**} 不匹配任何公开前缀，
 * 因此这里<b>不需要改一行安全配置</b>。
 *
 * <p>越权靠数据层兜住：所有查询与删除都带 {@code user_id} 条件，
 * 访问别人的条目只会得到 404，也不泄漏该资源是否存在。
 */
@RestController
@RequestMapping("/api/fridge")
@Tag(name = "我的冰箱", description = "登记冰箱里的食材，并按现有菜谱推荐能做的菜")
public class FridgeController {

    private final FridgeService fridgeService;
    private final FridgeRecommendService recommendService;

    public FridgeController(FridgeService fridgeService, FridgeRecommendService recommendService) {
        this.fridgeService = fridgeService;
        this.recommendService = recommendService;
    }

    @GetMapping
    @Operation(summary = "我的冰箱", description = "按过期日升序；unknown 状态由匹配器判断，不是字符串相等")
    public Result<List<FridgeItemVO>> list(@AuthenticationPrincipal SecurityUser user) {
        return Result.ok(fridgeService.list(user.id()));
    }

    @GetMapping("/catalog")
    @Operation(summary = "食材字典",
            description = "菜谱里出现过的全部食材名 + 各自能被多少道菜用到，按用量降序；用于录入时的自动补全")
    public Result<List<CatalogItemVO>> catalog() {
        return Result.ok(fridgeService.catalog());
    }

    @PostMapping
    @RateLimit("fridge")
    @Operation(summary = "添加食材",
            description = "数量与单位都可省略（表示「有就行」）；同名同单位重复添加返回 409，请直接改数量")
    public Result<FridgeItemVO> add(@Valid @RequestBody FridgeItemRequest request,
                                    @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(fridgeService.add(user.id(), request));
    }

    @PutMapping("/{id}")
    @RateLimit("fridge")
    @Operation(summary = "修改食材",
            description = "字段全部可选；要清空过期日请传 clearExpire=true（null 表示「不修改」）")
    public Result<FridgeItemVO> update(@PathVariable Long id,
                                       @Valid @RequestBody FridgeUpdateRequest request,
                                       @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(fridgeService.update(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    @RateLimit("fridge")
    @Operation(summary = "删除食材")
    public Result<Void> delete(@PathVariable Long id,
                               @AuthenticationPrincipal SecurityUser user) {
        fridgeService.delete(user.id(), id);
        return Result.ok();
    }

    @GetMapping("/recipes")
    @Operation(summary = "按冰箱推荐菜谱",
            description = "按「还差几样主料」升序；同时叠加忌口过滤与快过期优先。"
                    + "cookable=true 表示主料齐全、现在就能做")
    public Result<FridgeSuggestionVO> recommend(
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(defaultValue = "false") boolean onlyCookable,
            @AuthenticationPrincipal SecurityUser user) {
        return Result.ok(recommendService.suggest(user.id(), limit, onlyCookable));
    }
}
