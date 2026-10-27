package com.example.rag.service;

import com.example.rag.domain.UserAccount;
import com.example.rag.repository.UserAccountRepository;
import com.example.rag.support.ForbiddenException;
import com.example.rag.support.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 登录令牌 + Redis 会话。
 *
 * <p>JWT 本身无状态，签名与过期时间由服务端密钥校验；Redis 只保存「令牌 ID → 用户 ID」，
 * 用来实现注销、停用账号后令牌立即失效，以及仪表盘在线人数统计。
 * Redis 中只有令牌 ID，不保存密码或用户隐私信息。</p>
 */
@Service
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);

    private static final String SESSION_PREFIX = "rag:auth:session:";
    private static final String USER_SESSIONS_PREFIX = "rag:auth:user:";
    private static final String ONLINE_KEY = "rag:auth:online";

    private final StringRedisTemplate redis;
    private final UserAccountRepository userAccountRepository;
    private final SecretKey signingKey;
    private final Duration tokenTtl;
    private final Duration onlineWindow;

    public AuthTokenService(StringRedisTemplate redis,
                            UserAccountRepository userAccountRepository,
                            @Value("${rag.auth.jwt-secret}") String jwtSecret,
                            @Value("${rag.auth.token-ttl:7d}") Duration tokenTtl,
                            @Value("${rag.auth.online-window:15m}") Duration onlineWindow) {
        this.redis = redis;
        this.userAccountRepository = userAccountRepository;
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("rag.auth.jwt-secret 至少需要 32 字节，请通过 JWT_SECRET 配置");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.tokenTtl = tokenTtl;
        this.onlineWindow = onlineWindow;
    }

    /** 签发 JWT，并在 Redis 登记会话与在线状态。 */
    public String issue(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenTtl);
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(tokenId)
                .claim("account", user.getAccount())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        redis.opsForValue().set(sessionKey(tokenId), String.valueOf(user.getId()), tokenTtl);
        redis.opsForZSet().add(ONLINE_KEY, tokenId, (double) now.toEpochMilli());
        redis.expire(ONLINE_KEY, tokenTtl.plus(onlineWindow));

        String userSessionsKey = userSessionsKey(user.getId());
        redis.opsForSet().add(userSessionsKey, tokenId);
        redis.expire(userSessionsKey, tokenTtl);
        return token;
    }

    /** 注销单个令牌：JWT 本身无法撤回，删除 Redis 会话后立即失效。 */
    public void revoke(String token) {
        Claims claims = parse(token);
        if (claims == null) {
            return;
        }
        revokeTokenId(parseUserId(claims.getSubject()), claims.getId());
    }

    /** 停用账号、修改密码或删除账号时，让该用户所有已登录设备退出。 */
    public void revokeAllForUser(Long userId) {
        revokeAllForUserExcept(userId, null);
    }

    /** 修改密码时保留当前设备的登录态，只让其他设备退出。 */
    public void revokeOtherSessions(Long userId, String authorizationHeader) {
        revokeAllForUserExcept(userId, extractToken(authorizationHeader));
    }

    private void revokeAllForUserExcept(Long userId, String keepToken) {
        if (userId == null) {
            return;
        }
        Claims keepClaims = parse(keepToken);
        String keepTokenId = keepClaims != null && userId.equals(parseUserId(keepClaims.getSubject()))
                ? keepClaims.getId()
                : null;

        String userSessionsKey = userSessionsKey(userId);
        Set<String> tokenIds = redis.opsForSet().members(userSessionsKey);
        if (tokenIds != null) {
            for (String tokenId : tokenIds) {
                if (tokenId.equals(keepTokenId)) {
                    continue;
                }
                redis.delete(sessionKey(tokenId));
                redis.opsForZSet().remove(ONLINE_KEY, tokenId);
                redis.opsForSet().remove(userSessionsKey, tokenId);
            }
        }
        if (keepTokenId == null) {
            redis.delete(userSessionsKey);
        }
    }

    /**
     * 从 Authorization 头解析当前用户。签名错误、过期、Redis 会话已被注销、
     * 用户被删除或停用，都返回空。
     */
    public Optional<UserAccount> resolve(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        Claims claims = parse(token);
        if (claims == null) {
            return Optional.empty();
        }

        Long userId = parseUserId(claims.getSubject());
        String tokenId = claims.getId();
        if (userId == null || tokenId == null) {
            return Optional.empty();
        }

        String sessionValue = redis.opsForValue().get(sessionKey(tokenId));
        if (sessionValue == null || !sessionValue.equals(String.valueOf(userId))) {
            return Optional.empty();
        }

        Optional<UserAccount> user = userAccountRepository.findById(userId);
        if (user.isEmpty() || !user.get().isEnabled()) {
            revokeTokenId(userId, tokenId);
            return Optional.empty();
        }

        // 记录最近一次请求时间，仪表盘在线人数按这个窗口统计
        redis.opsForZSet().add(ONLINE_KEY, tokenId, (double) Instant.now().toEpochMilli());
        return user;
    }

    /** 最近在线窗口内仍有请求的会话数。 */
    public long onlineCount() {
        long cutoff = Instant.now().minus(onlineWindow).toEpochMilli();
        redis.opsForZSet().removeRangeByScore(ONLINE_KEY, 0, (double) cutoff);
        Long count = redis.opsForZSet().count(ONLINE_KEY, (double) cutoff, Double.MAX_VALUE);
        return count == null ? 0 : count;
    }

    public UserAccount require(String authorizationHeader) {
        return resolve(authorizationHeader)
                .orElseThrow(() -> new UnauthorizedException("登录状态已失效，请重新登录"));
    }

    public UserAccount requireBack(String authorizationHeader) {
        UserAccount user = require(authorizationHeader);
        if (!UserAccount.ROLE_BACK.equals(user.getRole())) {
            throw new ForbiddenException("当前账号没有后台访问权限");
        }
        return user;
    }

    private Claims parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 校验失败: {}", ex.getMessage());
            return null;
        }
    }

    private void revokeTokenId(Long userId, String tokenId) {
        if (tokenId == null) {
            return;
        }
        redis.delete(sessionKey(tokenId));
        redis.opsForZSet().remove(ONLINE_KEY, tokenId);
        if (userId != null) {
            redis.opsForSet().remove(userSessionsKey(userId), tokenId);
        }
    }

    private Long parseUserId(String subject) {
        try {
            return subject == null ? null : Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        return value.isEmpty() ? null : value;
    }

    private String sessionKey(String tokenId) {
        return SESSION_PREFIX + tokenId;
    }

    private String userSessionsKey(Long userId) {
        return USER_SESSIONS_PREFIX + userId;
    }
}
