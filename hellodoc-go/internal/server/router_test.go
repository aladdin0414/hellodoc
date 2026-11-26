package server

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/nopkg/hellodoc/hellodoc-go/internal/auth"
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

func TestSystemInfoOrigin(t *testing.T) {
	t.Setenv("APP_CORS_ALLOWED_ORIGINS", "http://localhost:13000")
	handler := NewRouter("test", time.Now)
	for _, tc := range []struct {
		method, target, origin string
		status                 int
	}{
		{"POST", "http://example.com:1234", "http://example.com:1234", 200},
		{"GET", "http://example.com", "http://example.com:80", 200},
		{"GET", "https://example.com:443", "https://example.com", 200},
		{"POST", "http://example.com:1234", "http://example.com:1235", 403},
		{"GET", "https://example.com", "http://example.com", 403},
	} {
		req := httptest.NewRequest(tc.method, tc.target+"/api/system/info", nil)
		req.Header.Set("Origin", tc.origin)
		res := httptest.NewRecorder()
		handler.ServeHTTP(res, req)
		if res.Code != tc.status || res.Header().Get("Access-Control-Allow-Origin") != "" {
			t.Errorf("%s %s origin %s: status %d, headers %v", tc.method, tc.target, tc.origin, res.Code, res.Header())
		}
		if tc.status == 200 && tc.method == "POST" && !strings.Contains(res.Body.String(), `"code":9999`) {
			t.Errorf("same-origin POST body %s", res.Body.String())
		}
	}
}

func TestUnsupportedMethods(t *testing.T) {
	handler := NewRouter("test", time.Now)
	for _, method := range []string{"TRACE", "OTHER"} {
		req := httptest.NewRequest(method, "/api/system/info", nil)
		req.Header.Set("Accept", "text/plain")
		res := httptest.NewRecorder()
		handler.ServeHTTP(res, req)
		if res.Code != 405 {
			t.Errorf("%s returned %d", method, res.Code)
		}
	}
}

func TestSystemInfoCORSConfiguration(t *testing.T) {
	const key = "APP_CORS_ALLOWED_ORIGINS"
	const origin = "https://client.example"
	for _, tc := range []struct {
		name, setting        string
		unset                bool
		status               int
		allowed, credentials string
	}{
		{"unset", "", true, 200, "*", ""},
		{"empty", "", false, 403, "", ""},
		{"allowlist", "https://other.example," + origin, false, 200, origin, "true"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			t.Setenv(key, tc.setting)
			if tc.unset {
				if err := os.Unsetenv(key); err != nil {
					t.Fatal(err)
				}
			}
			req := httptest.NewRequest(http.MethodGet, "/api/system/info", nil)
			req.Header.Set("Origin", origin)
			res := httptest.NewRecorder()
			NewRouter("test", time.Now).ServeHTTP(res, req)
			if res.Code != tc.status || res.Header().Get("Access-Control-Allow-Origin") != tc.allowed || res.Header().Get("Access-Control-Allow-Credentials") != tc.credentials {
				t.Errorf("status %d, headers %v", res.Code, res.Header())
			}
		})
	}
}

func TestBearerSystemInfo(t *testing.T) {
	for _, tc := range []struct {
		method, credential string
		status             int
	}{
		{"GET", "Basic example", 200}, {"GET", "bearer malformed", 200}, {"GET", "Bearer malformed", 401},
		{"HEAD", "Bearer malformed", 401}, {"OPTIONS", "Bearer malformed", 401},
	} {
		r := httptest.NewRequest(tc.method, "/api/system/info", nil)
		r.Header.Set("Authorization", tc.credential)
		w := httptest.NewRecorder()
		NewRouterWithAuth("test", time.Now, &auth.Service{}).ServeHTTP(w, r)
		if w.Code != tc.status || (tc.method == "HEAD" && w.Body.Len() != 0) {
			t.Errorf("%s %s: status %d", tc.method, tc.credential, w.Code)
		}
	}
}

func TestBearerPreflightBeforeAuthentication(t *testing.T) {
	t.Setenv("APP_CORS_ALLOWED_ORIGINS", "*")
	r := httptest.NewRequest("OPTIONS", "/api/system/info", nil)
	r.Header.Set("Origin", "http://localhost:12345")
	r.Header.Set("Access-Control-Request-Method", "GET")
	r.Header.Set("Authorization", "Bearer invalid")
	w := httptest.NewRecorder()
	NewRouterWithAuth("test", time.Now, &auth.Service{}).ServeHTTP(w, r)
	if w.Code != 200 || w.Header().Get("Access-Control-Allow-Origin") != "*" || w.Header().Get("WWW-Authenticate") != "" {
		t.Fatal("credential blocked preflight")
	}
}

func TestBearerMethodDispatch(t *testing.T) {
	t.Setenv("APP_CORS_ALLOWED_ORIGINS", "*")
	now := time.Unix(1000, 0)
	tokens, err := auth.NewTokens([]byte(strings.Repeat("x", 64)), time.Minute, time.Hour, func() time.Time { return now })
	if err != nil {
		t.Fatal(err)
	}
	expired, err := tokens.Access("fixture", nil)
	if err != nil {
		t.Fatal(err)
	}
	now = now.Add(time.Hour)
	configured := NewRouterWithAuth("fixture", time.Now, &auth.Service{Tokens: tokens})
	public := NewRouter("fixture", time.Now)
	for _, tc := range []struct {
		method, auth, origin      string
		handler                   http.Handler
		status                    int
		reason, allowOrigin, body string
	}{
		{"PUT", "Bearer invalid", "", configured, 401, "invalid_token", "", ""},
		{"DELETE", "Bearer " + expired, "", configured, 401, "token_expired", "", ""},
		{"PATCH", "Bearer invalid", "https://example.invalid", configured, 403, "", "", "Invalid CORS request"},
		{"DELETE", "Bearer invalid", "https://example.invalid", configured, 401, "invalid_token", "*", ""},
		{"PATCH", "", "", configured, 200, "", "", `"code":9999`},
		{"PUT", "", "", public, 405, "", "", ""},
	} {
		r := httptest.NewRequest(tc.method, "/api/system/info", nil)
		r.Header.Set("Authorization", tc.auth)
		r.Header.Set("Origin", tc.origin)
		w := httptest.NewRecorder()
		tc.handler.ServeHTTP(w, r)
		challenge := ""
		if tc.reason != "" {
			challenge = `Bearer error="invalid_token", error_description="` + tc.reason + `"`
		}
		if w.Code != tc.status || w.Header().Get("WWW-Authenticate") != challenge || w.Header().Get("Access-Control-Allow-Origin") != tc.allowOrigin || !strings.Contains(w.Body.String(), tc.body) {
			t.Errorf("%s got %d", tc.method, w.Code)
		}
	}
}
