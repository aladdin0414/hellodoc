package auth

import (
	"bytes"
	_ "embed"
	"encoding/base64"
	"errors"
	"os"
	"os/exec"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	interopSecret  = "HelloDoc synthetic JWT vector, never deployed: 测试🔑0123456789abcdef"
	interopSubject = "演示-user"
	interopJTI     = "742ba0e4-7791-43b4-87e0-44d518f79f58"
	interopFamily  = "3240c09b-8665-4f75-b363-19e95e6666f5"
)

var interopNow = time.Date(2026, time.January, 2, 3, 4, 5, 750_000_000, time.UTC)

//go:embed testdata/java-jwt.txt
var javaJWTs string

func TestJavaJWTInterop(t *testing.T) {
	vectors := make(map[string]string)
	for _, line := range strings.Split(strings.TrimSpace(javaJWTs), "\n") {
		label, token, _ := strings.Cut(line, " ")
		vectors[label] = token
	}

	now := interopNow
	tokens, err := NewTokens([]byte(interopSecret), 24*time.Hour, 7*24*time.Hour, func() time.Time { return now })
	if err != nil {
		t.Fatal(err)
	}
	roles := []string{"ROLE_ADMIN", "ROLE_USER"}
	issuedAt := interopNow.Truncate(time.Second)
	for _, name := range []string{"access", "refresh"} {
		t.Run("Java_"+name, func(t *testing.T) {
			claims, err := tokens.Verify(vectors[name])
			if err != nil {
				t.Fatal(err)
			}
			if claims.Subject != interopSubject || !claims.IssuedAt.Equal(issuedAt) {
				t.Fatalf("unexpected identity or issued-at: %+v", claims)
			}
			switch name {
			case "access":
				if claims.Type != "" || claims.ID != "" || claims.Family != "" ||
					!slices.Equal(claims.Roles, roles) || !claims.ExpiresAt.Equal(issuedAt.Add(24*time.Hour)) {
					t.Fatalf("unexpected access claims: %+v", claims)
				}
			case "refresh":
				if claims.Type != "refresh" || claims.ID != interopJTI || claims.Family != interopFamily ||
					len(claims.Roles) != 0 || !claims.ExpiresAt.Equal(issuedAt.Add(7*24*time.Hour)) {
					t.Fatalf("unexpected refresh claims: %+v", claims)
				}
			}
		})
	}

	for _, name := range []string{
		"missing_subject", "missing_expiration", "unknown_type", "missing_jti", "missing_family",
		"bad_jti", "bad_family", "future_nbf", "hs256",
	} {
		t.Run(name, func(t *testing.T) {
			if vectors[name] == "" {
				t.Fatal("missing Java fixture")
			}
			if claims, err := tokens.Verify(vectors[name]); !errors.Is(err, ErrTokenInvalid) || claims.Subject != "" {
				t.Fatalf("Verify returned subject %q and error %v", claims.Subject, err)
			}
		})
	}
	if _, err := tokens.Verify(vectors["missing_issued_at"]); err != nil {
		t.Fatalf("iat is optional when verifying: %v", err)
	}
	if claims, err := tokens.Verify(vectors["expired"]); !errors.Is(err, ErrTokenExpired) || claims.Subject != interopSubject {
		t.Fatalf("expired token returned subject %q and error %v", claims.Subject, err)
	}
	expiredParts := strings.Split(vectors["expired"], ".")
	signature := expiredParts[2]
	replacement := "A"
	if signature[0] == 'A' {
		replacement = "B"
	}
	expiredParts[2] = replacement + signature[1:]
	if claims, err := tokens.Verify(strings.Join(expiredParts, ".")); !errors.Is(err, ErrTokenInvalid) || claims.Subject != "" {
		t.Fatalf("invalid signature returned subject %q and error %v", claims.Subject, err)
	}

	wrongKey, err := NewTokens([]byte(strings.Repeat("q", 64)), time.Hour, time.Hour, func() time.Time { return now })
	if err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"access", "expired"} {
		if claims, err := wrongKey.Verify(vectors[name]); !errors.Is(err, ErrTokenInvalid) || claims.Subject != "" {
			t.Fatalf("wrong key returned subject %q and error %v", claims.Subject, err)
		}
	}

	now = issuedAt
	if _, err := tokens.Verify(vectors["expires_now"]); err != nil {
		t.Fatalf("JJWT accepts exact numeric date equality: %v", err)
	}
	now = interopNow
	if _, err := tokens.Verify(vectors["expires_now"]); !errors.Is(err, ErrTokenExpired) {
		t.Fatalf("token should be expired past its numeric date: %v", err)
	}

	t.Run("Go_to_Java", func(t *testing.T) {
		classpath := os.Getenv("HELLODOC_JJWT_TEST_CLASSPATH")
		if classpath == "" {
			t.Skip("set HELLODOC_JJWT_TEST_CLASSPATH to JJWT and Jackson jars for live cross-verification")
		}
		access, err := tokens.Access(interopSubject, roles)
		if err != nil {
			t.Fatal(err)
		}
		emptyRoles, err := tokens.Access(interopSubject, nil)
		if err != nil {
			t.Fatal(err)
		}
		refresh, err := tokens.Refresh(interopSubject, interopJTI, interopFamily)
		if err != nil {
			t.Fatal(err)
		}
		source := "testdata/JwtInterop.java"
		vectors, err := exec.Command("java", "-cp", classpath, source, "generate").Output()
		if err != nil {
			t.Fatal(err)
		}
		if !bytes.Equal(vectors, []byte(javaJWTs)) {
			t.Fatal("Java generated different fixture tokens")
		}
		cmd := exec.Command("java", "-cp", classpath, source, "verify")
		cmd.Stdin = strings.NewReader(access + "\n" + emptyRoles + "\n" + refresh + "\n")
		output, err := cmd.CombinedOutput()
		if err != nil {
			t.Fatalf("Java verification: %v\n%s", err, output)
		}
		t.Log(strings.TrimSpace(string(output)))
	})
}

func TestTokensSigning(t *testing.T) {
	if _, err := NewTokens([]byte(strings.Repeat("x", 63)), time.Hour, time.Hour, nil); err == nil {
		t.Fatal("accepted a secret with fewer than 64 bytes")
	}
	if _, err := NewTokens([]byte(strings.Repeat(" ", 64)), time.Hour, time.Hour, nil); err == nil {
		t.Fatal("accepted an all-blank secret")
	}
	tokens, err := NewTokens([]byte(strings.Repeat("密", 22)), time.Hour, 2*time.Hour, func() time.Time { return interopNow })
	if err != nil {
		t.Fatal(err)
	}
	access, err := tokens.Access(interopSubject, nil)
	if err != nil {
		t.Fatal(err)
	}
	claims, err := tokens.Verify(access)
	if err != nil || claims.Subject != interopSubject || claims.Type != "" || claims.Roles == nil || len(claims.Roles) != 0 ||
		!claims.ExpiresAt.Equal(interopNow.Truncate(time.Second).Add(time.Hour)) {
		t.Fatalf("unexpected Go access claims: %+v, %v", claims, err)
	}
	refresh, err := tokens.Refresh(interopSubject, interopJTI, interopFamily)
	if err != nil {
		t.Fatal(err)
	}
	claims, err = tokens.Verify(refresh)
	if err != nil || claims.Subject != interopSubject || claims.Type != "refresh" ||
		claims.ID != interopJTI || claims.Family != interopFamily ||
		!claims.ExpiresAt.Equal(interopNow.Truncate(time.Second).Add(2*time.Hour)) {
		t.Fatalf("unexpected Go refresh claims: %+v, %v", claims, err)
	}
	if _, err := tokens.Access(" ", nil); err == nil {
		t.Fatal("signed an access token with blank subject")
	}
	if _, err := tokens.Refresh(interopSubject, "1-1-1-1-1", interopFamily); err == nil {
		t.Fatal("signed a refresh token with invalid jti")
	}
}

func TestNewTokensFromEnv(t *testing.T) {
	t.Setenv("JWT_SECRET", interopSecret)
	for _, key := range []string{"JWT_EXPIRATION_MS", "JWT_REFRESH_EXPIRATION_MS"} {
		t.Setenv(key, "")
		if err := os.Unsetenv(key); err != nil {
			t.Fatal(err)
		}
	}
	now := func() time.Time { return interopNow }
	check := func(accessTTL, refreshTTL time.Duration) {
		t.Helper()
		tokens, err := NewTokensFromEnv(now)
		if err != nil {
			t.Fatal(err)
		}
		access, err := tokens.Access(interopSubject, nil)
		if err != nil {
			t.Fatal(err)
		}
		refresh, err := tokens.Refresh(interopSubject, interopJTI, interopFamily)
		if err != nil {
			t.Fatal(err)
		}
		for _, tt := range []struct {
			raw string
			ttl time.Duration
		}{{access, accessTTL}, {refresh, refreshTTL}} {
			claims, err := tokens.Verify(tt.raw)
			if err != nil || claims.ExpiresAt == nil ||
				!claims.ExpiresAt.Equal(interopNow.Add(tt.ttl).Truncate(time.Second)) {
				t.Fatalf("unexpected expiration for %s: %+v, %v", tt.ttl, claims.ExpiresAt, err)
			}
		}
	}
	check(24*time.Hour, 7*24*time.Hour)
	t.Setenv("JWT_EXPIRATION_MS", "7250")
	t.Setenv("JWT_REFRESH_EXPIRATION_MS", "12010")
	check(7250*time.Millisecond, 12010*time.Millisecond)

	for _, value := range []string{"", "0", "-1", "forever", "9223372036854775807"} {
		t.Setenv("JWT_EXPIRATION_MS", value)
		if _, err := NewTokensFromEnv(now); err == nil {
			t.Fatalf("accepted invalid JWT_EXPIRATION_MS %q", value)
		}
	}
	t.Setenv("JWT_EXPIRATION_MS", "1000")
	t.Setenv("JWT_SECRET", "")
	if _, err := NewTokensFromEnv(now); err == nil {
		t.Fatal("accepted an empty JWT_SECRET")
	}
}

func TestVerifyExactClaimNames(t *testing.T) {
	tokens, err := NewTokens([]byte(interopSecret), time.Hour, time.Hour, func() time.Time { return interopNow })
	if err != nil {
		t.Fatal(err)
	}

	for _, tc := range []struct {
		name    string
		payload string
	}{
		{"subject", `{"SUB":"user","exp":4102444800}`},
		{"expiration", `{"sub":"user","EXP":4102444800}`},
		{"refresh_jti", `{"sub":"user","exp":4102444800,"type":"refresh","JTI":"` + interopJTI + `","family":"` + interopFamily + `"}`},
		{"refresh_family", `{"sub":"user","exp":4102444800,"type":"refresh","jti":"` + interopJTI + `","FAMILY":"` + interopFamily + `"}`},
	} {
		t.Run("missing_"+tc.name, func(t *testing.T) {
			raw := signJWTJSON(t, `{"alg":"HS512"}`, tc.payload)
			if claims, err := tokens.Verify(raw); !errors.Is(err, ErrTokenInvalid) || claims.Subject != "" {
				t.Fatalf("case variant supplied the missing %s: %+v, %v", tc.name, claims, err)
			}
		})
	}

	t.Run("shadowed", func(t *testing.T) {
		raw := signJWTJSON(t, `{"alg":"HS512"}`, `{
			"sub":"user","SUB":"shadow",
			"exp":4102444800,"EXP":1,
			"iat":1,"IAT":2,
			"nbf":1,"NBF":4102444800,
			"iss":"issuer","ISS":"shadow",
			"aud":["audience"],"AUD":["shadow"],
			"roles":["ROLE_USER"],"ROLES":["ROLE_ADMIN"],
			"type":"refresh","TYPE":"shadow",
			"jti":"`+interopJTI+`","JTI":"shadow",
			"family":"`+interopFamily+`","FAMILY":"shadow"
		}`)
		claims, err := tokens.Verify(raw)
		if err != nil {
			t.Fatal(err)
		}
		if claims.Subject != "user" || claims.ExpiresAt.Unix() != 4102444800 ||
			claims.IssuedAt.Unix() != 1 || claims.NotBefore.Unix() != 1 ||
			claims.Issuer != "issuer" || !slices.Equal(claims.Audience, []string{"audience"}) ||
			!slices.Equal(claims.Roles, []string{"ROLE_USER"}) || claims.Type != "refresh" ||
			claims.ID != interopJTI || claims.Family != interopFamily {
			t.Fatalf("case variant shadowed an exact claim: %+v", claims)
		}
	})

	t.Run("variant_type_does_not_select_refresh", func(t *testing.T) {
		raw := signJWTJSON(t, `{"alg":"HS512"}`, `{"sub":"user","exp":4102444800,"TYPE":"refresh"}`)
		if claims, err := tokens.Verify(raw); err != nil || claims.Type != "" {
			t.Fatalf("unknown claim changed access semantics: %+v, %v", claims, err)
		}
	})
}

func TestVerifyRejectsUnsupportedCriticalHeaders(t *testing.T) {
	tokens, err := NewTokens([]byte(interopSecret), time.Hour, time.Hour, func() time.Time { return interopNow })
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		name   string
		header string
	}{
		{"unknown", `{"alg":"HS512","crit":["extension"],"extension":true}`},
		{"missing_extension", `{"alg":"HS512","crit":["extension"]}`},
		{"malformed", `{"alg":"HS512","crit":"extension","extension":true}`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			raw := signJWTJSON(t, tc.header, `{"sub":"user","exp":4102444800}`)
			if claims, err := tokens.Verify(raw); !errors.Is(err, ErrTokenInvalid) || claims.Subject != "" {
				t.Fatalf("unsupported crit was accepted: %+v, %v", claims, err)
			}
		})
	}
}

func TestVerifyHeadersWithoutCriticalExtensions(t *testing.T) {
	tokens, err := NewTokens([]byte(interopSecret), time.Hour, time.Hour, func() time.Time { return interopNow })
	if err != nil {
		t.Fatal(err)
	}
	for _, header := range []string{
		`{"alg":"HS512"}`,
		`{"alg":"HS512","crit":[]}`,
		`{"alg":"HS512","crit":null}`,
		`{"alg":"HS512","CRIT":["extension"]}`,
	} {
		raw := signJWTJSON(t, header, `{"sub":"user","exp":4102444800}`)
		if claims, err := tokens.Verify(raw); err != nil || claims.Subject != "user" {
			t.Fatalf("%s returned %+v, %v", header, claims, err)
		}
	}
}

func signJWTJSON(t *testing.T, header, payload string) string {
	t.Helper()
	encode := base64.RawURLEncoding.EncodeToString
	unsigned := encode([]byte(header)) + "." + encode([]byte(payload))
	signature, err := jwt.SigningMethodHS512.Sign(unsigned, []byte(interopSecret))
	if err != nil {
		t.Fatal(err)
	}
	return unsigned + "." + encode(signature)
}
