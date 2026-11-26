package auth

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func refreshTokens(t *testing.T, now func() time.Time) *Tokens {
	t.Helper()
	tokens, err := NewTokens([]byte(strings.Repeat("x", 64)), time.Hour, 24*time.Hour, now)
	if err != nil {
		t.Fatal(err)
	}
	return tokens
}

func refreshHTTP(ctx context.Context, s *Service, body string) *httptest.ResponseRecorder {
	mux := http.NewServeMux()
	s.RegisterRoutes(mux)
	r := httptest.NewRequestWithContext(ctx, "POST", "/api/auth/refresh-token", strings.NewReader(body))
	r.Header.Set("Content-Type", "application/json")
	r.Header.Set("X-Language", "en")
	w := httptest.NewRecorder()
	s.Bearer(mux).ServeHTTP(w, r)
	return w
}

func refreshBody(raw string) string {
	body, _ := json.Marshal(map[string]string{"refreshToken": raw})
	return string(body)
}

func TestRefreshHTTPValidation(t *testing.T) {
	now := time.Now().Truncate(time.Second)
	clock := now
	tokens := refreshTokens(t, func() time.Time { return clock })
	svc := Service{Tokens: tokens}
	expired, err := tokens.Refresh("migration_fixture", newUUID(), newUUID())
	if err != nil {
		t.Fatal(err)
	}
	clock = now.Add(25 * time.Hour)
	access, err := tokens.Access("migration_fixture", nil)
	if err != nil {
		t.Fatal(err)
	}
	sign := func(jti, family string) string {
		t.Helper()
		token, err := jwt.NewWithClaims(jwt.SigningMethodHS512, jwt.MapClaims{
			"sub": "migration_fixture", "type": "refresh", "exp": clock.Add(time.Hour).Unix(),
			"jti": jti, "family": family,
		}).SignedString(tokens.secret)
		if err != nil {
			t.Fatal(err)
		}
		return token
	}
	wrongSignature, err := jwt.NewWithClaims(jwt.SigningMethodHS512, jwt.MapClaims{
		"sub": "migration_fixture", "type": "refresh", "exp": clock.Add(time.Hour).Unix(),
		"jti": newUUID(), "family": newUUID(),
	}).SignedString([]byte(strings.Repeat("y", 64)))
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		name, body, message string
		code                int
	}{
		{"missing field", `{}`, "Refresh token is invalid", 1005},
		{"null field", `{"refreshToken":null}`, "Refresh token is invalid", 1005},
		{"malformed JSON", `{"refreshToken":`, "System error occurred, please try again later", 9999},
		{"access token", refreshBody(access), "Invalid token type: refresh token required", 1006},
		{"expired token", refreshBody(expired), "Refresh token is invalid", 1005},
		{"wrong signature", refreshBody(wrongSignature), "Refresh token is invalid", 1005},
		{"missing jti", refreshBody(sign("", newUUID())), "Refresh token is invalid", 1005},
		{"malformed family", refreshBody(sign(newUUID(), "not-a-uuid")), "Refresh token is invalid", 1005},
	} {
		t.Run(tc.name, func(t *testing.T) {
			w := refreshHTTP(t.Context(), &svc, tc.body)
			var result struct {
				Code    int
				Message string
				Data    any
			}
			if w.Code != http.StatusOK || json.Unmarshal(w.Body.Bytes(), &result) != nil ||
				result.Code != tc.code || result.Message != tc.message || result.Data != nil {
				t.Fatalf("status %d, response %s, want code %d and message %q", w.Code, w.Body, tc.code, tc.message)
			}
		})
	}
}
