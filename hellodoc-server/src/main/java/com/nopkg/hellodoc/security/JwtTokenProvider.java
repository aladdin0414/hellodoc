package com.nopkg.hellodoc.security;

import com.nopkg.hellodoc.config.SecurityConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration; // access token 过期时间（ms）

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration; // refresh token 过期时间（ms）

    private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS512;

    @PostConstruct
    public void validateSecret() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("jwt.secret 不能为空");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalStateException("jwt.secret 长度不足，HS512 至少需要 64 字节");
        }
    }

    private JwtParser getParser() {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    // ====================== 生成 Token ======================
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), signatureAlgorithm)
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateRefreshToken(userDetails, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public String generateRefreshToken(UserDetails userDetails, String jti, String familyId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("type", "refresh")
                .id(jti)
                .claim("family", familyId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), signatureAlgorithm)
                .compact();
    }

    // ====================== 解析与验证（核心）======================

    /** 你原来的方法，保持不变（严格模式：过期会抛异常） */
    public Claims parseClaims(String token) {
        return getParser().parseSignedClaims(token).getPayload();
    }

    /** 即使 Token 已过期也能拿到用户名（关键！） */
    public String getUsernameFromToken(String token) {
        try {
            return getParser().parseSignedClaims(token).getPayload().getSubject();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /** 判断 Token 是否已过期（不过滤签名错误） */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getParser().parseSignedClaims(token).getPayload();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true; // 签名错误等也视为过期/无效
        }
    }

    /** 严格验证 Token 是否完全有效（签名 + 未过期） */
    public boolean validateTokenStrict(String token) {
        try {
            getParser().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 判断是否为 refresh token（严格模式：签名和过期均需有效） */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = getParser().parseSignedClaims(token).getPayload();
            return "refresh".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public String getJtiFromToken(String token) {
        try {
            return getParser().parseSignedClaims(token).getPayload().getId();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getId();
        } catch (Exception e) {
            return null;
        }
    }

    public String getFamilyIdFromToken(String token) {
        try {
            return getParser().parseSignedClaims(token).getPayload().get("family", String.class);
        } catch (ExpiredJwtException e) {
            return e.getClaims().get("family", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 即使过期也能拿到完整 Claims（推荐保留） */
    public Claims getAllClaimsEvenExpired(String token) {
        try {
            return getParser().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        } catch (Exception e) {
            throw new RuntimeException("无效的 JWT token", e);
        }
    }

    /** 方便获取角色（即使过期） */
    public List<String> getRolesFromToken(String token) {
        return getAllClaimsEvenExpired(token).get("roles", List.class);
    }

    /** 获取过期时间（即使已过期） */
    public Date getExpirationDateFromToken(String token) {
        return getAllClaimsEvenExpired(token).getExpiration();
    }
}
