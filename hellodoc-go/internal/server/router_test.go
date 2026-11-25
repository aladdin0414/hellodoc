package server

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestSystemInfoRoute(t *testing.T) {
	handler := NewRouter("3.6.9", func() time.Time { return time.Date(2026, 11, 25, 10, 15, 20, 0, time.Local) })
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/system/info", nil))
	if recorder.Code != http.StatusOK {
		t.Fatalf("status: got %d", recorder.Code)
	}
	var response map[string]string
	if err := json.Unmarshal(recorder.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	if response["version"] != "3.6.9" || response["serverTime"] != "2026-11-25 10:15:20" {
		t.Fatalf("unexpected response: %v", response)
	}
}

func TestUnknownRoutesAreNotHandledAsSystemInfo(t *testing.T) {
	handler := NewRouter("3.6.9", time.Now)
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/system/info/other", nil))
	if recorder.Code == http.StatusOK {
		t.Fatal("unknown route returned success")
	}
}

func TestSystemInfoHTTP(t *testing.T) {
	t.Setenv("APP_CORS_ALLOWED_ORIGINS", "http://localhost:13000")
	for _, tc := range []struct {
		name, method, accept, auth, origin string
		status                             int
		contentType, body                  string
	}{
		{"head", "HEAD", "", "", "", 200, "application/json", ""},
		{"options", "OPTIONS", "", "", "", 200, "", ""},
		{"post", "POST", "", "", "", 200, "application/json", `{"code":9999,"message":"系统异常，请稍后再试","data":null}`},
		{"json", "GET", "application/json", "", "", 200, "application/json", "info"},
		{"plain", "GET", "text/plain", "", "", 406, "", ""},
		{"bearer", "GET", "", "Bearer malformed", "", 401, "application/json;charset=UTF-8", `{"error":"invalid_token","error_description":"refreshToken 无效","needRefresh":false}`},
		{"cors allowed", "OPTIONS", "", "", "http://localhost:13000", 200, "", ""},
		{"cors denied", "OPTIONS", "", "", "https://denied.invalid", 403, "", "Invalid CORS request"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := httptest.NewRequest(tc.method, "/api/system/info", nil)
			r.Header.Set("Accept", tc.accept)
			if tc.auth != "" {
				r.Header.Set("Authorization", tc.auth)
			}
			if tc.origin != "" {
				r.Header.Set("Origin", tc.origin)
				r.Header.Set("Access-Control-Request-Method", "GET")
			}
			w := httptest.NewRecorder()
			NewRouter("3.6.9", time.Now).ServeHTTP(w, r)
			if w.Code != tc.status || w.Header().Get("Content-Type") != tc.contentType {
				t.Errorf("status/type = %d %q", w.Code, w.Header().Get("Content-Type"))
			}
			if tc.body != "info" && strings.TrimSpace(w.Body.String()) != tc.body {
				t.Errorf("body = %q", w.Body.String())
			}
			if w.Header().Get("X-Frame-Options") != "DENY" {
				t.Error("missing frame protection")
			}
			if tc.name == "cors allowed" && w.Header().Get("Access-Control-Allow-Origin") != tc.origin {
				t.Error("missing CORS origin")
			}
		})
	}
}
