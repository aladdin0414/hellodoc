import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

// Standalone JJWT 0.12.6 fixture generator and verifier. This key is public test data.
class JwtInterop {
    private static final byte[] SECRET = "HelloDoc synthetic JWT vector, never deployed: 测试🔑0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05.750Z");
    private static final String SUBJECT = "演示-user";
    private static final String JTI = "742ba0e4-7791-43b4-87e0-44d518f79f58";
    private static final String FAMILY = "3240c09b-8665-4f75-b363-19e95e6666f5";
    private static final List<String> ROLES = List.of("ROLE_ADMIN", "ROLE_USER");

    public static void main(String[] args) throws Exception {
        if (args[0].equals("generate")) {
            emit("access", base(86_400).claim("roles", ROLES));
            emit("refresh", base(604_800).claim("type", "refresh").id(JTI).claim("family", FAMILY));
            emit("expired", base(-1).claim("roles", ROLES));
            emit("expires_now", base(0));
            emit("missing_subject", base(60).subject(null));
            emit("missing_expiration", base(60).expiration(null));
            emit("missing_issued_at", base(60).issuedAt(null));
            emit("unknown_type", base(60).claim("type", "access"));
            emit("missing_jti", base(60).claim("type", "refresh").claim("family", FAMILY));
            emit("missing_family", base(60).claim("type", "refresh").id(JTI));
            emit("bad_jti", base(60).claim("type", "refresh").id("1-1-1-1-1").claim("family", FAMILY));
            emit("bad_family", base(60).claim("type", "refresh").id(JTI).claim("family", "family"));
            emit("future_nbf", base(60).notBefore(Date.from(NOW.plusSeconds(30))));
            System.out.println("hs256 " + base(60)
                    .signWith(new SecretKeySpec(SECRET, "HmacSHA256"), Jwts.SIG.HS256).compact());
            return;
        }

        try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            verify(input.readLine(), false, ROLES);
            verify(input.readLine(), false, List.of());
            verify(input.readLine(), true, null);
        }
        System.out.println("JJWT verified Go access and refresh headers and claims");
    }

    private static JwtBuilder base(long ttlSeconds) {
        return Jwts.builder().subject(SUBJECT).issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(ttlSeconds)));
    }

    private static void emit(String name, JwtBuilder builder) {
        System.out.println(name + " " + builder.signWith(Keys.hmacShaKeyFor(SECRET), Jwts.SIG.HS512).compact());
    }

    private static void verify(String token, boolean refresh, List<String> roles) {
        var parsed = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET))
                .clock(() -> Date.from(NOW)).build().parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        check("HS512".equals(parsed.getHeader().getAlgorithm()), "signing algorithm");
        check(SUBJECT.equals(claims.getSubject()), "sub");
        check(NOW.getEpochSecond() == claims.getIssuedAt().toInstant().getEpochSecond(), "iat");
        long ttl = refresh ? 604_800 : 86_400;
        check(NOW.getEpochSecond() + ttl == claims.getExpiration().toInstant().getEpochSecond(), "exp");
        if (refresh) {
            check(Set.of("sub", "iat", "exp", "type", "jti", "family").equals(claims.keySet()), "refresh keys");
            check("refresh".equals(claims.get("type")), "type");
            check(JTI.equals(claims.getId()), "jti");
            check(FAMILY.equals(claims.get("family")), "family");
        } else {
            check(Set.of("sub", "iat", "exp", "roles").equals(claims.keySet()), "access keys");
            check(roles.equals(claims.get("roles")), "roles");
        }
    }

    private static void check(boolean ok, String claim) {
        if (!ok) {
            throw new AssertionError("Unexpected " + claim);
        }
    }
}
