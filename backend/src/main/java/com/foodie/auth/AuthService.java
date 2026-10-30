package com.foodie.auth;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.auth.dto.ChangePasswordRequest;
import com.foodie.auth.dto.LoginRequest;
import com.foodie.auth.dto.LoginResponse;
import com.foodie.auth.dto.RegisterRequest;
import com.foodie.auth.dto.UserVO;
import com.foodie.common.BizException;
import com.foodie.user.SysUser;
import com.foodie.user.UserAvoidance;
import com.foodie.user.UserAvoidanceRepository;
import com.foodie.user.UserRepository;
import com.foodie.user.UserRole;

@Service
public class AuthService {

    /**
     * 一个固定的 BCrypt 哈希，用于「用户名不存在」时也走一遍密码校验。
     *
     * <p>目的：抹平「用户存在」与「用户不存在」两条路径的响应耗时差，
     * 让攻击者无法通过计时来枚举系统里有哪些用户名。
     */
    private static final String DUMMY_HASH = "$2a$10$1bMMr/8iU9rrHSNZZUU.VuPqpBDbu4PRZElSPRR5YsJ8Cwf.igT7.";

    private final UserRepository userRepository;
    private final UserAvoidanceRepository avoidanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAuthService userAuthService;

    public AuthService(UserRepository userRepository,
                       UserAvoidanceRepository avoidanceRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserAuthService userAuthService) {
        this.userRepository = userRepository;
        this.avoidanceRepository = avoidanceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userAuthService = userAuthService;
    }

    // ------------------------------------------------------------ 注册

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw BizException.conflict("用户名已被占用，换一个试试");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setEmail(blankToNull(request.email()));
        // 注册一律是普通用户；管理员账号只能由数据库/运维创建，避免越权注册提权
        user.setRole(UserRole.USER);
        user.setStatus(1);
        SysUser saved = userRepository.save(user);

        String token = jwtService.issue(saved, false);
        return new LoginResponse(token, jwtService.ttlSeconds(false), UserVO.of(saved, List.of()));
    }

    // ------------------------------------------------------------ 登录

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        SysUser user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            // 走一次等价开销的哈希校验，避免用响应时间暴露「用户名是否存在」
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw BizException.unauthorized("用户名或密码不正确");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw BizException.unauthorized("用户名或密码不正确");
        }
        if (!user.isEnabled()) {
            throw BizException.forbidden("账号已被禁用，请联系管理员");
        }

        boolean rememberMe = request.rememberMeEnabled();
        String token = jwtService.issue(user, rememberMe);
        userRepository.touchLastLoginAt(user.getId(), LocalDateTime.now());

        List<String> avoidances = avoidanceRepository.findByUserId(user.getId())
                .stream().map(UserAvoidance::getKeyword).toList();
        return new LoginResponse(token, jwtService.ttlSeconds(rememberMe), UserVO.of(user, avoidances));
    }

    // ------------------------------------------------------------ 当前用户

    @Transactional(readOnly = true)
    public UserVO currentUser(Long userId) {
        SysUser user = requireUser(userId);
        List<String> avoidances = avoidanceRepository.findByUserId(userId)
                .stream().map(UserAvoidance::getKeyword).toList();
        return UserVO.of(user, avoidances);
    }

    // ------------------------------------------------------------ 修改密码

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = requireUser(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw BizException.badRequest("当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw BizException.badRequest("新密码不能与当前密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // 密码变了就让用户状态缓存立刻失效（含旧会话的后续请求）
        userAuthService.evict(userId);
    }

    // ------------------------------------------------------------ 忌口偏好

    @Transactional
    public List<String> replaceAvoidances(Long userId, List<String> keywords) {
        requireUser(userId);
        List<String> cleaned = keywords == null ? List.of() : keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();

        avoidanceRepository.deleteAllByUserId(userId);
        if (!cleaned.isEmpty()) {
            List<UserAvoidance> rows = cleaned.stream().map(keyword -> {
                UserAvoidance entity = new UserAvoidance();
                entity.setUserId(userId);
                entity.setKeyword(keyword);
                return entity;
            }).toList();
            avoidanceRepository.saveAll(rows);
        }
        return cleaned;
    }

    // ------------------------------------------------------------ 工具

    private SysUser requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
