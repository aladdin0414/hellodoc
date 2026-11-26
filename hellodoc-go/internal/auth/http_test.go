package auth

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestLoginHTTP(t *testing.T) {
	s := new(Service)
	mux := http.NewServeMux()
	s.RegisterRoutes(mux)
	for _, tc := range []struct {
		body, media string
		code        int
	}{{"{}", "application/json", 1001}, {"{}", "text/plain", 9999}, {"null", "application/json", 9999}} {
		r := httptest.NewRequest("POST", "/api/auth/login", strings.NewReader(tc.body))
		r.Header.Set("Content-Type", tc.media)
		r.Header.Set("X-Language", "en")
		w := httptest.NewRecorder()
		s.Bearer(mux).ServeHTTP(w, r)
		var result struct {
			Code    int
			Data    any
			Message string
		}
		if json.Unmarshal(w.Body.Bytes(), &result) != nil || result.Code != tc.code || result.Data != nil || w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
}
