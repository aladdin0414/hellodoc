package auth

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestRegisterContract(t *testing.T) {
	mux := http.NewServeMux()
	s := new(Service)
	s.RegisterRoutes(mux)
	handler := s.Bearer(mux)

	for _, tc := range []struct{ name, media, body string }{
		{"missing fields", "application/json", "{}"},
		{"missing password", "application/json", `{"username":"new"}`},
		{"null username", "application/json", `{"username":null,"password":"fixture"}`},
		{"wrong media", "text/plain", `{"username":"new","password":"fixture"}`},
		{"malformed", "application/json", "{"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := httptest.NewRequest("POST", "/api/auth/register", strings.NewReader(tc.body))
			r.Header.Set("Content-Type", tc.media)
			w := httptest.NewRecorder()
			r.Header.Set("X-Language", "en")
			handler.ServeHTTP(w, r)

			var body struct {
				Code    int
				Message string
				Data    any
			}
			if w.Code != http.StatusOK || w.Header().Get("Content-Type") != "application/json" ||
				json.Unmarshal(w.Body.Bytes(), &body) != nil || body.Code != 9999 ||
				body.Message != "System error occurred, please try again later" || body.Data != nil {
				t.Fatalf("status %d, body %s", w.Code, w.Body.String())
			}
		})
	}

	t.Run("invalid bearer", func(t *testing.T) {
		r := httptest.NewRequest("POST", "/api/auth/register", strings.NewReader(`{}`))
		r.Header.Set("Content-Type", "application/json")
		r.Header.Set("X-Language", "en")
		r.Header.Set("Authorization", "Bearer malformed")
		w := httptest.NewRecorder()
		handler.ServeHTTP(w, r)
		var body struct {
			Error       string
			Description string `json:"error_description"`
			NeedRefresh bool
		}
		if w.Code != http.StatusUnauthorized || w.Header().Get("Content-Type") != "application/json;charset=UTF-8" ||
			json.Unmarshal(w.Body.Bytes(), &body) != nil || body.Error != "invalid_token" ||
			body.Description != "Refresh token is invalid" || body.NeedRefresh {
			t.Fatalf("status %d, body %s", w.Code, w.Body.String())
		}
	})
}
