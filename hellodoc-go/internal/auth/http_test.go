package auth

import (
	"encoding/json"
	"io"
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

func TestAuthBodyLimit(t *testing.T) {
	mux := http.NewServeMux()
	new(Service).RegisterRoutes(mux)
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)
	for _, tc := range []struct {
		name    string
		size    int
		chunked bool
		want    int
	}{
		{"at limit", 64 << 10, false, 1001},
		{"oversized content length", 128 << 10, false, 9999},
		{"oversized chunked body", 128 << 10, true, 9999},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var reader io.Reader = strings.NewReader("{}" + strings.Repeat(" ", tc.size-2))
			if tc.chunked {
				reader = io.NopCloser(reader)
			}
			resp, err := server.Client().Post(server.URL+"/api/auth/login", "application/json", reader)
			if err != nil {
				t.Fatal(err)
			}
			defer resp.Body.Close()
			var result struct {
				Code int
				Data any
			}
			err = json.NewDecoder(resp.Body).Decode(&result)
			if err != nil || resp.StatusCode != http.StatusOK || result.Code != tc.want || result.Data != nil {
				t.Errorf("status=%d, code=%d, want=%d, err=%v", resp.StatusCode, result.Code, tc.want, err)
			}
		})
	}
}
