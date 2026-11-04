package com.nopkg.hellodoc.mcp;

import com.nopkg.hellodoc.repositories.UserAuthRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

@Service
public class McpAccessTokenService {
    private final JdbcTemplate jdbc;
    private final UserAuthRepository identities;
    private final SecureRandom random = new SecureRandom();
    public McpAccessTokenService(JdbcTemplate jdbc, UserAuthRepository identities) { this.jdbc = jdbc; this.identities = identities; }
    public record IssuedToken(String id, String name, String token) {}
    public record TokenInfo(String id, String name, String createdAt) {}
    public IssuedToken create(long userId, String username, String name) {
        if (name == null || name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Invalid name");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = "hdmcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO mcp_access_token (id, user_id, username, name, token_hash) VALUES (?, ?, ?, ?, ?)", id, userId, username, name.trim(), hash(token));
        return new IssuedToken(id, name.trim(), token);
    }
    public List<TokenInfo> list(long userId) {
        return jdbc.query("SELECT id, name, created_at FROM mcp_access_token WHERE user_id = ? ORDER BY created_at DESC", (rs, row) -> new TokenInfo(rs.getString(1), rs.getString(2), rs.getString(3)), userId);
    }
    public boolean revoke(long userId, String id) { return jdbc.update("DELETE FROM mcp_access_token WHERE user_id = ? AND id = ?", userId, id) == 1; }
    public Optional<String> authenticate(String token) {
        if (token == null || !token.matches("hdmcp_[A-Za-z0-9_-]{43}")) return Optional.empty();
        return jdbc.query("SELECT user_id, username FROM mcp_access_token WHERE token_hash = ?", (rs, row) -> Map.entry(rs.getLong(1), rs.getString(2)), hash(token)).stream().findFirst().filter(entry ->
                identities.findByIdentifierAndIdentityType(entry.getValue(), "PASSWORD")
                        .map(identity -> identity.getUser().getId().equals(entry.getKey())).orElse(false)
        ).map(Map.Entry::getValue);
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
