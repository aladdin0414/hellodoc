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

	"github.com/jackc/pgx/v5"

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
	t.Setenv("DB_URL", fmt.Sprintf("jdbc:postgresql://127.0.0.1:%d/auth_fixture?sslmode=disable&currentSchema=fixture_scope%%2C%%20public", port))
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
	t.Run("JDBC SSL requires encryption", func(t *testing.T) {
		var serverSSL string
		if err := svc.Store.DB.QueryRowContext(t.Context(), "show ssl").Scan(&serverSSL); err != nil || serverSSL != "off" {
			t.Fatal("expected plaintext fixture")
		}
		t.Setenv("DB_URL", strings.Replace(os.Getenv("DB_URL"), "sslmode=disable", "ssl=true", 1))
		if insecure, err := configuredAuth(); err == nil {
			insecure.Store.DB.Close()
			t.Fatal("JDBC ssl=true fell back to plaintext")
		}
	})
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

func TestDatabaseDSNEscaping(t *testing.T) {
	t.Setenv("DB_USERNAME", "fixture")
	t.Setenv("DB_PASSWORD", "fixture")
	for _, tc := range []struct{ prefix, app, want string }{
		{"jdbc:", "client%2Bname", "client+name"},
		{"jdbc:", "client+name", "client name"},
		{"", "client+name", "client+name"},
	} {
		t.Setenv("DB_URL", tc.prefix+"postgresql://127.0.0.1/fixture?currentSchema=tenant%2C%20public&options=-c%20statement_timeout%3D1000&application_name="+tc.app)
		dsn, err := databaseDSN()
		if err != nil {
			t.Fatal(err)
		}
		cfg, err := pgx.ParseConfig(dsn)
		if err != nil {
			t.Fatal("pgx could not parse fixture URI")
		}
		params := cfg.RuntimeParams
		if params["search_path"] != "tenant, public" || params["options"] != "-c statement_timeout=1000" || params["application_name"] != tc.want {
			t.Errorf("schema %q, options %q, app %q", params["search_path"], params["options"], params["application_name"])
		}
	}
}

func TestDatabaseDSNQueryOrder(t *testing.T) {
	t.Setenv("DB_USERNAME", "fixture")
	t.Setenv("DB_PASSWORD", "fixture")
	for _, prefix := range []string{"jdbc:", ""} {
		for _, tc := range []struct {
			query      string
			tls        bool
			db, schema string
		}{
			{"sslmode=disable&ssl=true&dbname=first&database=second&currentSchema=old&search_path=new", true, "second", "new"},
			{"ssl=true&sslmode=disable&database=first&dbname=second&search_path=old&currentSchema=new", false, "second", "new"},
			{"sslmode=require&sslmode=disable&database=old&database=new&currentSchema=old&currentSchema=tenant%2C%20public", false, "new", "tenant, public"},
		} {
			t.Setenv("DB_URL", prefix+"postgresql://127.0.0.1/fixture?"+tc.query)
			dsn, err := databaseDSN()
			if err != nil {
				t.Fatal(err)
			}
			cfg, err := pgx.ParseConfig(dsn)
			if err != nil {
				t.Fatal("pgx parse failed")
			}
			wantTLS := tc.tls
			if prefix == "jdbc:" && strings.Contains(tc.query, "sslmode=disable") {
				wantTLS = false
			}
			if (cfg.TLSConfig != nil) != wantTLS || cfg.Database != tc.db || cfg.RuntimeParams["search_path"] != tc.schema {
				t.Errorf("input %q %q yielded tls=%t database=%q schema=%q", prefix, tc.query, cfg.TLSConfig != nil, cfg.Database, cfg.RuntimeParams["search_path"])
			}
		}
	}
}

func TestJDBCSSLPolicy(t *testing.T) {
	t.Setenv("DB_USERNAME", "fixture")
	t.Setenv("DB_PASSWORD", "fixture")
	for _, tc := range []struct {
		query                     string
		verify, disabled, invalid bool
	}{
		{"ssl=true", true, false, false}, {"ssl", true, false, false}, {"ssl=TRUE", true, false, false},
		{"ssl=false&ssl=true", true, false, false},
		{"sslmode=verify-full&ssl=true", true, false, false}, {"ssl=true&sslmode=VERIFY-FULL", true, false, false},
		{"ssl=true&sslmode=disable", false, true, false}, {"sslmode=disable&ssl=true", false, true, false},
		{"sslmode=", false, false, true}, {"sslmode=unsupported", false, false, true}, {"ssl=true&%73slmode=disable", false, false, true},
	} {
		t.Setenv("DB_URL", "jdbc:postgresql://127.0.0.1/fixture?"+tc.query)
		dsn, err := databaseDSN()
		if err != nil && tc.invalid {
			continue
		}
		if err != nil {
			t.Fatal(err)
		}
		cfg, err := pgx.ParseConfig(dsn)
		if tc.invalid {
			if err == nil {
				t.Errorf("%s accepted invalid mode", tc.query)
			}
			continue
		}
		if err != nil {
			t.Fatal("pgx parse failed")
		}
		if tc.disabled {
			if cfg.TLSConfig != nil {
				t.Errorf("%s TLS still enabled", tc.query)
			}
			continue
		}
		if tc.verify && (cfg.TLSConfig == nil || cfg.TLSConfig.InsecureSkipVerify || cfg.TLSConfig.ServerName != "127.0.0.1" || len(cfg.Fallbacks) != 0) {
			t.Errorf("%s lost full server verification", tc.query)
		}
	}
}
