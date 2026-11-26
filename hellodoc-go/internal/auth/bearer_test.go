package auth

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestBearerErrors(t *testing.T) {
	now := time.Unix(2000, 0)
	tokens, err := NewTokens([]byte(strings.Repeat("a", 64)), time.Minute, time.Hour, func() time.Time { return now })
	if err != nil {
		t.Fatal(err)
	}
	raw, err := tokens.Access("fixture", nil)
	if err != nil {
		t.Fatal(err)
	}
	now = now.Add(time.Hour)
	service := Service{Tokens: tokens}
	for _, tc := range []struct {
		name                string
		headers             []string
		reason, description string
	}{
		{"expired", []string{"Bearer " + raw}, "token_expired", "Link has expired"},
		{"invalid", []string{"Bearer bogus"}, "invalid_token", "Refresh token is invalid"},
		{"mcp", []string{"Bearer hdmcp_sample"}, "invalid_token", "Invalid token"},
		{"first ignored", []string{"Basic a", "Bearer bogus"}, "", ""},
		{"first malformed", []string{"Bearer bogus", "Basic a"}, "invalid_token", "Refresh token is invalid"},
		{"nonbreaking space", []string{"Bearer \u00a0"}, "invalid_token", "Refresh token is invalid"},
		{"blank", []string{"Bearer  "}, "", ""},
	} {
		t.Run(tc.name, func(t *testing.T) {
			r := httptest.NewRequest("GET", "/api/system/info", nil)
			r.Header["Authorization"] = tc.headers
			r.Header.Set("Accept-Language", "zh-CN")
			r.Header.Set("X-Language", "en")
			w := httptest.NewRecorder()
			service.Bearer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if _, ok := Principal(r.Context()); ok {
					t.Fatal("anonymous principal")
				}
			})).ServeHTTP(w, r)
			if tc.reason == "" {
				if w.Code != 200 {
					t.Fatal(w.Code)
				}
				return
			}
			if w.Code != 401 || w.Header().Get("WWW-Authenticate") != `Bearer error="invalid_token", error_description="`+tc.reason+`"` ||
				!strings.Contains(w.Body.String(), tc.description) || strings.Contains(w.Body.String(), `"needRefresh":true`) != (tc.reason == "token_expired") {
				t.Fatal("incorrect rejection")
			}
		})
	}
}

func TestBearerReloadAccount(t *testing.T) {
	store, phase := fixtureStore(t)
	tokens, err := NewTokens([]byte(strings.Repeat("a", 64)), time.Minute, time.Hour, func() time.Time { return time.Unix(5000, 0) })
	if err != nil {
		t.Fatal(err)
	}
	// Identical bytes across all four fixture phases.
	raw, err := tokens.Access("lookup_fixture", []string{"ROLE_STALE"})
	if err != nil {
		t.Fatal(err)
	}
	service := Service{Store: store, Tokens: tokens}
	r := httptest.NewRequest("GET", "/api/system/info", nil)
	r.Header.Set("Authorization", "Bearer "+raw)
	w := httptest.NewRecorder()
	service.Bearer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		a, ok := Principal(r.Context())
		if !ok || a.Identifier != "lookup_fixture" || a.Credential != nil {
			t.Fatal("incorrect principal")
		}
		want := ""
		if phase == "roles" {
			want = "ROLE_Zulu ROLE_null ROLE_😀 ROLE_Ａ"
		}
		if strings.Join(a.Roles, " ") != want {
			t.Fatal("stale roles")
		}
	})).ServeHTTP(w, r)
	want := 200
	if phase == "updated" || phase == "deleted" {
		want = 401
	}
	if w.Code != want {
		t.Fatalf("%s: got %d", phase, w.Code)
	}
	if phase == "updated" && !strings.Contains(w.Body.String(), "Account is disabled") {
		t.Fatal("missing account locale")
	}
	if _, ok := Principal(r.Context()); ok {
		t.Fatal("principal escaped request scope")
	}
}
