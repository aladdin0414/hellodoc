package auth

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func logoutHTTP(ctx context.Context, s *Service, body, media, bearer string) *httptest.ResponseRecorder {
	mux := http.NewServeMux()
	s.RegisterRoutes(mux)
	r := httptest.NewRequestWithContext(ctx, "POST", "/api/auth/logout", strings.NewReader(body))
	if media != "" {
		r.Header.Set("Content-Type", media)
	}
	r.Header.Set("X-Language", "en")
	if bearer != "" {
		r.Header.Set("Authorization", "Bearer "+bearer)
	}
	w := httptest.NewRecorder()
	s.Bearer(mux).ServeHTTP(w, r)
	return w
}

func requireLogoutResult(t *testing.T, w *httptest.ResponseRecorder, code int) {
	t.Helper()
	var response struct {
		Code    int
		Message string
		Data    json.RawMessage
	}
	message := "Success"
	if code == 9999 {
		message = "System error occurred, please try again later"
	}
	if w.Code != http.StatusOK || json.Unmarshal(w.Body.Bytes(), &response) != nil ||
		response.Code != code || response.Message != message || string(response.Data) != "null" {
		t.Fatalf("logout status %d, response %s, want code %d and explicit null data", w.Code, w.Body, code)
	}
}

func TestLogoutHTTP(t *testing.T) {
	tokens := refreshTokens(t, func() time.Time { return time.Unix(1700000000, 0) })
	svc := &Service{Tokens: tokens}
	for _, tc := range []struct {
		name, body, media string
		code              int
	}{
		{"absent", "", "", 0},
		{"null", "null", "application/json", 0},
		{"missing field", "{}", "application/json", 0},
		{"null field", `{"refreshToken":null}`, "application/json", 0},
		{"invalid token", refreshBody("invalid"), "application/json", 0},
		{"scalar field", `{"refreshToken":true}`, "application/json", 0},
		{"field casing", `{"RefreshToken":{}}`, "application/json", 0},
		{"wrong media", "{}", "text/plain", 9999},
		{"whitespace only", " ", "application/json", 9999},
		{"malformed", "{", "application/json", 9999},
		{"two documents", "null {}", "application/json", 9999},
		{"object field", `{"refreshToken":{},"refreshToken":"ignored"}`, "application/json", 9999},
		{"over limit", `{"padding":"` + strings.Repeat("x", maxAuthBodyBytes) + `"}`, "application/json", 9999},
	} {
		t.Run(tc.name, func(t *testing.T) {
			requireLogoutResult(t, logoutHTTP(t.Context(), svc, tc.body, tc.media, ""), tc.code)
		})
	}
}
