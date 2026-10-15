package com.foodie.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.foodie.auth.dto.ChangePasswordRequest;
import com.foodie.auth.dto.LoginRequest;
import com.foodie.auth.dto.LoginResponse;
import com.foodie.auth.dto.RegisterRequest;
import com.foodie.common.BizException;
import com.foodie.common.ErrorCode;
import com.foodie.user.SysUser;
import com.foodie.user.UserAvoidanceRepository;
import com.foodie.user.UserRepository;
import com.foodie.user.UserRole;

/**
 * 认证服务的单元测试。
 *
 * <p>用 <b>strength=4</b> 的 BCrypt：测试里只关心「哈希没被绕过」这件事，
 * 不需要生产级的 10 轮代价（那是 100ms × 每个用例，纯粹浪费）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAvoidanceRepository avoidanceRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserAuthService userAuthService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, avoidanceRepository, encoder, jwtService, userAuthService);
        lenient().when(jwtService.issue(any(SysUser.class), anyBoolean())).thenReturn("fake-token");
        lenient().when(jwtService.ttlSeconds(anyBoolean())).thenReturn(7200L);
        lenient().when(avoidanceRepository.findByUserId(anyLong())).thenReturn(List.of());
    }

    private SysUser user(String username, String rawPassword, UserRole role, int status) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername(username);
        user.setPassword(encoder.encode(rawPassword));
        user.setNickname("昵称");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    // ============================================================ 注册

    @Test
    @DisplayName("注册：密码必须以 BCrypt 哈希落库，绝不能存明文")
    void registerHashesPassword() {
        when(userRepository.existsByUsername("tom")).thenReturn(false);
        when(userRepository.save(any(SysUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequest("tom", "secret123", "汤姆", "tom@x.com"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userRepository).save(captor.capture());
        String stored = captor.getValue().getPassword();

        assertThat(stored).isNotEqualTo("secret123").startsWith("$2");
        assertThat(encoder.matches("secret123", stored)).isTrue();
    }

    @Test
    @DisplayName("注册：无论请求怎么写，角色只能是 USER（不允许注册出管理员）")
    void registerAlwaysCreatesNormalUser() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(SysUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequest("hacker", "secret123", "黑客", null));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.USER);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("注册：用户名重复返回 409")
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("tom")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("tom", "secret123", "汤姆", null)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(userRepository, never()).save(any());
    }

    // ============================================================ 登录

    @Test
    @DisplayName("登录：用户名不存在与密码错误返回同一个提示（不泄漏用户名是否存在）")
    void loginDoesNotLeakUserExistence() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("tom"))
                .thenReturn(Optional.of(user("tom", "correct", UserRole.USER, 1)));

        String unknownMessage = catchMessage(() -> authService.login(new LoginRequest("nobody", "x", false)));
        String wrongPasswordMessage = catchMessage(() -> authService.login(new LoginRequest("tom", "wrong", false)));

        assertThat(unknownMessage).isEqualTo(wrongPasswordMessage);
    }

    @Test
    @DisplayName("登录：密码正确则签发令牌，并刷新最后登录时间")
    void loginIssuesToken() {
        SysUser existing = user("tom", "correct", UserRole.USER, 1);
        when(userRepository.findByUsername("tom")).thenReturn(Optional.of(existing));

        LoginResponse response = authService.login(new LoginRequest("tom", "correct", true));

        assertThat(response.token()).isEqualTo("fake-token");
        assertThat(response.user().username()).isEqualTo("tom");
        verify(userRepository).touchLastLoginAt(anyLong(), any());
        // rememberMe=true 时应使用长效 TTL
        verify(jwtService).ttlSeconds(true);
    }

    @Test
    @DisplayName("登录：被禁用的账号返回 403，且不签发令牌")
    void loginRejectsDisabledAccount() {
        when(userRepository.findByUsername("banned"))
                .thenReturn(Optional.of(user("banned", "correct", UserRole.USER, 0)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("banned", "correct", false)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(jwtService, never()).issue(any(), anyBoolean());
    }

    // ============================================================ 改密

    @Test
    @DisplayName("改密：旧密码不对直接拒绝")
    void changePasswordRequiresOldPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("tom", "correct", UserRole.USER, 1)));

        assertThatThrownBy(() -> authService.changePassword(1L, new ChangePasswordRequest("wrong", "newpass1")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("改密：新旧密码相同应拒绝（避免用户以为改了其实没改）")
    void changePasswordRejectsSamePassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("tom", "correct", UserRole.USER, 1)));

        assertThatThrownBy(() -> authService.changePassword(1L, new ChangePasswordRequest("correct", "correct")))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("改密成功：新哈希生效，并且主动清除用户状态缓存")
    void changePasswordEvictsCache() {
        SysUser existing = user("tom", "correct", UserRole.USER, 1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(SysUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.changePassword(1L, new ChangePasswordRequest("correct", "brandnew1"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(encoder.matches("brandnew1", captor.getValue().getPassword())).isTrue();
        verify(userAuthService).evict(1L);
    }

    // ============================================================ 忌口

    @Test
    @DisplayName("保存忌口：去重、去空白、全量覆盖")
    void replaceAvoidancesSanitizesInput() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("tom", "correct", UserRole.USER, 1)));

        List<String> saved = authService.replaceAvoidances(1L, List.of(" 香菜 ", "花生", "香菜", "   "));

        assertThat(saved).containsExactlyInAnyOrder("香菜", "花生");
        verify(avoidanceRepository).deleteAllByUserId(1L);
        verify(avoidanceRepository).saveAll(any());
    }

    private String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("本应抛出 BizException");
        } catch (BizException ex) {
            return ex.getMessage();
        }
    }
}
