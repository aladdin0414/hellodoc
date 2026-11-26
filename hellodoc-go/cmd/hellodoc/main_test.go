package main

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/nopkg/hellodoc/hellodoc-go/internal/server"
)

func TestAuthConfiguration(t *testing.T) {
	for _, tc := range []struct {
		mode, secret, dsn string
		fail              bool
	}{
		{"false", "", "", false}, {"typo", "", "", true}, {"true", "", "", true},
		{"true", strings.Repeat("a", 64), "", true},
		{"true", strings.Repeat("a", 64), "jdbc:postgresql://127.0.0.1:0/unavailable", true},
	} {
		t.Setenv("HELLODOC_GO_AUTH_ENABLED", tc.mode)
		t.Setenv("JWT_SECRET", tc.secret)
		t.Setenv("DB_URL", tc.dsn)
		t.Setenv("DB_USERNAME", "fixture")
		t.Setenv("DB_PASSWORD", "fixture")
		_, err := configuredAuth()
		if (err != nil) != tc.fail {
			t.Fatal("unexpected auth configuration result")
		}
	}
}

func TestBearerConfiguredSystemInfo(t *testing.T) {
	portText, password := os.Getenv("AUTH_STORE_PORT"), os.Getenv("AUTH_STORE_PASSWORD")
	if portText == "" && password == "" {
		t.Skip("requires isolated PostgreSQL fixture")
	}
	port, err := strconv.ParseUint(portText, 10, 16)
	if err != nil || port == 0 || len(password) != 48 {
		t.Fatal("invalid fixture")
	}
	t.Setenv("HELLODOC_GO_AUTH_ENABLED", "true")
	t.Setenv("JWT_SECRET", strings.Repeat("a", 64))
	t.Setenv("DB_URL", fmt.Sprintf("jdbc:postgresql://127.0.0.1:%d/auth_fixture?sslmode=disable&currentSchema=fixture_scope,public", port))
	t.Setenv("DB_USERNAME", "store_reader")
	t.Setenv("DB_PASSWORD", password)
	for _, name := range []string{"URL", "USERNAME", "PASSWORD"} {
		t.Setenv("SPRING_DATASOURCE_"+name, "unused")
	}
	svc, err := configuredAuth()
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Store.DB.Close()
	for _, name := range []string{"URL", "USERNAME", "PASSWORD"} {
		t.Setenv("SPRING_DATASOURCE_"+name, os.Getenv("DB_"+name))
		if err := os.Unsetenv("DB_" + name); err != nil {
			t.Fatal(err)
		}
	}
	aliasService, err := configuredAuth()
	if err != nil {
		t.Fatal("production datasource aliases failed")
	}
	aliasService.Store.DB.Close()
	var schema string
	if err := svc.Store.DB.QueryRowContext(t.Context(), "select current_schema()").Scan(&schema); err != nil || schema != "fixture_scope" {
		t.Fatal("JDBC schema selection failed")
	}
	access, err := svc.Tokens.Access("migration_fixture", nil)
	if err != nil {
		t.Fatal(err)
	}
	refresh, err := svc.Tokens.Refresh("migration_fixture", "00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002")
	if err != nil {
		t.Fatal(err)
	}
	h := server.NewRouterWithAuth("fixture", time.Now, svc)
	for _, tc := range []struct {
		handler http.Handler
		token   string
		status  int
	}{
		{server.NewRouter("fixture", time.Now), access, 401}, {h, access, 200}, {h, refresh, 401},
	} {
		r := httptest.NewRequest("GET", "/api/system/info", nil)
		r.Header.Set("Authorization", "Bearer "+tc.token)
		w := httptest.NewRecorder()
		tc.handler.ServeHTTP(w, r)
		if w.Code != tc.status {
			t.Fatal("incorrect configured route status")
		}
		if tc.token == refresh && !strings.Contains(w.Body.String(), "令牌类型错误") {
			t.Fatal("incorrect refresh rejection")
		}
	}
	svc.Store.DB.Close()
	r := httptest.NewRequest("GET", "/api/system/info", nil)
	r.Header.Set("Authorization", "Bearer "+access)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	if w.Code != 401 || !strings.Contains(w.Body.String(), `"needRefresh":false`) {
		t.Fatal("database outage accepted a credential")
	}
}
