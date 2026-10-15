package com.foodie.auth;

import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodie.user.UserRepository;

/**
 * JWT 请求路径上的用户状态校验。
 *
 * <p>这里解决一个真实的安全缺口：纯「只信 token 声明」的做法，
 * 在用户被禁用或删除后，旧 token 到期前依然畅通无阻。
 *
 * <p>但每个请求都查库又太贵。折中方案是把用户状态放进 Caffeine 短 TTL 缓存
 * （5 分钟）：绝大多数请求零数据库开销，而禁用操作最多 5 分钟后生效。
 * 改密码/禁用等写操作会主动 {@code @CacheEvict}，立即失效。
 */
@Service
public class UserAuthService {

    private final UserRepository userRepository;

    public UserAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(value = "userAuth", key = "#userId", unless = "#result == null")
    @Transactional(readOnly = true)
    public SecurityUser loadById(Long userId) {
        return userRepository.findById(userId)
                .map(user -> new SecurityUser(
                        user.getId(),
                        user.getUsername(),
                        "",
                        user.getRole(),
                        user.isEnabled()))
                .orElse(null);
    }

    /** 登录成功时读取完整用户（带密码哈希），供密码校验使用，不走缓存。 */
    @Transactional(readOnly = true)
    public Optional<com.foodie.user.SysUser> loadForLogin(String username) {
        return userRepository.findByUsername(username);
    }

    @CacheEvict(value = "userAuth", key = "#userId")
    public void evict(Long userId) {
        // 仅用于触发缓存失效；注解已经做完事情了
    }
}
