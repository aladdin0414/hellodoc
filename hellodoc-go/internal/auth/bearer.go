package auth

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"unicode"
)

type principalKey struct{}

func Principal(ctx context.Context) (Account, bool) {
	a, ok := ctx.Value(principalKey{}).(Account)
	return a, ok
}

func languageTag(language string) string {
	if strings.HasPrefix(strings.ToLower(language), "en") {
		return "en-US"
	}
	return "zh-CN"
}

func rejectBearer(w http.ResponseWriter, r *http.Request, zh, en string, expired bool) {
	desc := zh
	if w.Header().Get("Content-Language") == "en-US" {
		desc = en
	}
	reason := "invalid_token"
	if expired {
		reason = "token_expired"
	}
	w.Header().Set("WWW-Authenticate", `Bearer error="invalid_token", error_description="`+reason+`"`)
	w.Header().Set("Content-Type", "application/json;charset=UTF-8")
	w.WriteHeader(http.StatusUnauthorized)
	if r.Method != http.MethodHead {
		body, _ := json.Marshal(struct {
			Error       string `json:"error"`
			Description string `json:"error_description"`
			NeedRefresh bool   `json:"needRefresh"`
		}{"invalid_token", desc, expired})
		w.Write(body)
	}
}

// javaWhitespace mirrors Character.isWhitespace used by Spring StringUtils.hasText.
func javaWhitespace(r rune) bool {
	return (unicode.IsSpace(r) && r != 0x85 && r != 0xa0 && r != 0x2007 && r != 0x202f) || (r >= 0x1c && r <= 0x1f)
}

func (s *Service) Bearer(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		lang := r.Header.Get("X-Language")
		if strings.TrimSpace(lang) == "" {
			first, _, _ := strings.Cut(r.Header.Get("Accept-Language"), ",")
			lang, _, _ = strings.Cut(strings.TrimSpace(first), ";")
		}
		w.Header().Set("Content-Language", languageTag(lang))
		token, found := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		if !found || strings.TrimFunc(token, javaWhitespace) == "" {
			next.ServeHTTP(w, r)
			return
		}
		invalid := func() { rejectBearer(w, r, "refreshToken 无效", "Refresh token is invalid", false) }
		if strings.HasPrefix(token, "hdmcp_") {
			rejectBearer(w, r, "Invalid token", "Invalid token", false)
			return
		}
		if s == nil || s.Tokens == nil {
			invalid()
			return
		}
		claims, err := s.Tokens.Verify(token)
		if errors.Is(err, ErrTokenExpired) {
			rejectBearer(w, r, "链接已过期", "Link has expired", true)
			return
		}
		if err != nil {
			invalid()
			return
		}
		if s.Store == nil || s.Store.DB == nil {
			invalid()
			return
		}
		account, err := s.Store.Lookup(r.Context(), claims.Subject)
		if err == nil && strings.TrimSpace(account.LanguageMode) != "" && !strings.EqualFold(account.LanguageMode, "AUTO") {
			w.Header().Set("Content-Language", languageTag(account.LanguageMode))
		}
		if claims.Type == refreshTokenType {
			rejectBearer(w, r, "令牌类型错误", "Invalid token type", false)
			return
		}
		if err != nil {
			invalid()
			return
		}
		if !account.Enabled() {
			rejectBearer(w, r, "账号已被禁用", "Account is disabled", false)
			return
		}
		account.Credential = nil
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), principalKey{}, account)))
	})
}
