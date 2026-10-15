package com.foodie.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.foodie.common.BizException;
import com.foodie.config.JwtProperties;
import com.foodie.user.SysUser;
import com.foodie.user.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 签发与校验。
 *
 * <p>用 HS256 对称签名：单机部署下不需要非对称密钥的额外复杂度，
 * 密钥至少 256 bit（HS256 的硬性要求），启动时即校验，避免运行期才炸。
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** HS256 要求的最小密钥字节数。 */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = buildKey(properties.secret());
    }

    private static SecretKey buildKey(String rawSecret) {
        byte[] material;
        try {
            material = Decoders.BASE64.decode(rawSecret);
        } catch (Exception ex) {
            // 允许直接写普通字符串，但长度仍要够
            material = rawSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (material.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret 太短：HS256 至少需要 " + MIN_SECRET_BYTES + " 字节（当前 " + material.length + "）");
        }
        return Keys.hmacShaKeyFor(material);
    }

    /**
     * 签发令牌。
     *
     * @param rememberMe true 走长效 TTL，false 走普通 TTL
     */
    public String issue(SysUser user, boolean rememberMe) {
        Instant now = Instant.now();
        Instant expiry = now.plus(rememberMe ? properties.rememberMeTtl() : properties.accessTokenTtl());
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .signWith(signingKey)
                .compact();
    }

    /** 令牌有效期（秒），返回给前端用于提前续期提示。 */
    public long ttlSeconds(boolean rememberMe) {
        return (rememberMe ? properties.rememberMeTtl() : properties.accessTokenTtl()).toSeconds();
    }

    /**
     * 校验并解析令牌。
     *
     * @throws BizException 令牌无效或已过期（统一翻译成 401）
     */
    public JwtPayload parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = claims.get(CLAIM_USER_ID, Long.class);
            if (userId == null) {
                userId = Long.valueOf(claims.getSubject());
            }
            String roleName = claims.get(CLAIM_ROLE, String.class);
            UserRole role = roleName == null ? UserRole.USER : UserRole.valueOf(roleName);
            return new JwtPayload(userId, role);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 校验失败：{}", ex.getMessage());
            throw BizException.unauthorized("登录状态已失效，请重新登录");
        }
    }

    /**
     * 令牌中携带的最小身份信息。
     *
     * <p>刻意只放 id 与 role：用户名可能在库里被改，
     * 每次请求以数据库为准（见 UserAuthService 的短 TTL 缓存）。
     */
    public record JwtPayload(Long userId, UserRole role) {
    }
}
