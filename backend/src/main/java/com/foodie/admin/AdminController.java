package com.foodie.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodie.auth.SecurityUser;
import com.foodie.common.PageResult;
import com.foodie.common.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 后台管理接口。
 *
 * <p>{@code /api/admin/**} 在 SecurityConfig 里已整体限定为 ADMIN，
 * 这里再挂一层 {@code @PreAuthorize} 是刻意的双保险：
 * 万一将来有人改动了 URL 规则，方法级注解仍然拦得住。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "后台管理", description = "仅管理员可访问")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    @Operation(summary = "数据看板")
    public Result<StatsVO> stats() {
        return Result.ok(adminService.stats());
    }

    @GetMapping("/users")
    @Operation(summary = "用户列表")
    public Result<PageResult<AdminUserVO>> users(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.users(page, size));
    }

    @PutMapping("/users/{id}/status")
    @Operation(summary = "启用/禁用账号", description = "禁用后会立即清除该用户的状态缓存")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestBody StatusRequest request,
                                     @AuthenticationPrincipal SecurityUser operator) {
        adminService.updateStatus(id, request.status(), operator.id());
        return Result.ok();
    }

    /** 状态入参。 */
    public record StatusRequest(
            @Min(value = 0, message = "状态只能是 0 或 1")
            @Max(value = 1, message = "状态只能是 0 或 1")
            int status) {
    }
}
