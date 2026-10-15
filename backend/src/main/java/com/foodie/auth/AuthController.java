package com.foodie.auth;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.foodie.auth.dto.ChangePasswordRequest;
import com.foodie.auth.dto.LoginRequest;
import com.foodie.auth.dto.LoginResponse;
import com.foodie.auth.dto.RegisterRequest;
import com.foodie.auth.dto.UserVO;
import com.foodie.common.Result;
import com.foodie.ratelimit.RateLimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "注册、登录、改密")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @RateLimit(value = "register", scope = RateLimit.Scope.IP)
    @Operation(summary = "注册", description = "注册成功直接返回令牌，无需再登录一次；按 IP 限流防批量注册")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    @RateLimit(value = "login", scope = RateLimit.Scope.IP)
    @Operation(summary = "登录", description = "按 IP 限流，防止在线暴力破解")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 登出。
     *
     * <p>JWT 是无状态的，服务端不持有会话，因此这里不做事——
     * 真正的「登出」是前端把令牌丢掉。
     * 保留该接口是为了给前端一个语义明确的调用点，
     * 将来若引入令牌黑名单，只需改这里。
     */
    @PostMapping("/logout")
    @Operation(summary = "登出", description = "无状态 JWT，由前端丢弃令牌；该接口保留为将来接入黑名单的扩展点")
    public Result<Void> logout() {
        return Result.ok();
    }

    @GetMapping("/me")
    @Operation(summary = "当前登录用户")
    public Result<UserVO> me(@AuthenticationPrincipal SecurityUser user) {
        return Result.ok(authService.currentUser(user.id()));
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "必须提供旧密码，防止会话被盗后直接改密")
    public Result<Void> changePassword(@AuthenticationPrincipal SecurityUser user,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.id(), request);
        return Result.ok();
    }

    @PutMapping("/avoidances")
    @Operation(summary = "保存忌口偏好", description = "全量覆盖；随机配餐会自动带上这些关键词做过滤")
    public Result<List<String>> saveAvoidances(@AuthenticationPrincipal SecurityUser user,
                                               @RequestBody @Size(max = 20, message = "忌口最多 20 项")
                                               List<String> keywords) {
        return Result.ok(authService.replaceAvoidances(user.id(), keywords));
    }
}
