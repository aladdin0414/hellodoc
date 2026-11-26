package auth

import (
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"
)

func fixtureStore(t *testing.T) (*Store, string) {
	t.Helper()
	if _, present := os.LookupEnv("AUTH_STORE_FIXTURE"); present {
		t.Fatal("raw fixture DSN is unsupported")
	}
	portText, password, phase := os.Getenv("AUTH_STORE_PORT"), os.Getenv("AUTH_STORE_PASSWORD"), os.Getenv("AUTH_LOOKUP_PHASE")
	if portText == "" && password == "" && phase == "" {
		t.Skip("run scripts/migration/test_auth_postgres.py")
	}
	if phase != "initial" && phase != "updated" && phase != "roles" && phase != "deleted" {
		t.Fatal("invalid fixture phase")
	}
	port, err := strconv.ParseUint(portText, 10, 16)
	if err != nil || port == 0 {
		t.Fatal("invalid fixture port")
	}
	if _, err := hex.DecodeString(password); len(password) != 48 || err != nil {
		t.Fatal("invalid fixture password")
	}
	dsn := fmt.Sprintf("postgres://store_reader:%s@127.0.0.1:%d/auth_fixture?sslmode=disable", password, port)
	db, err := sql.Open("pgx", dsn)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	return &Store{DB: db}, phase
}

func TestLookupPasswordAccount(t *testing.T) {
	store, phase := fixtureStore(t)
	account, err := store.Lookup(t.Context(), "migration_fixture")
	if err != nil {
		t.Fatal(err)
	}
	if account.Identifier != "migration_fixture" || account.Nickname == nil || *account.Nickname != "Migration Fixture" {
		t.Fatal("registered account fields differ")
	}
	if account.UserID <= 0 || account.AuthID <= 0 || account.Credential == nil ||
		account.Status == nil || *account.Status != 0 || !account.Enabled() {
		t.Fatal("expected enabled registered password account")
	}
	for _, identifier := range []string{"missing_fixture", "email_only"} {
		if _, err := store.Lookup(t.Context(), identifier); !errors.Is(err, sql.ErrNoRows) {
			t.Fatalf("Lookup(%q) error = %v, want sql.ErrNoRows", identifier, err)
		}
	}
	probe, err := store.Lookup(t.Context(), "lookup_fixture")
	if err != nil {
		t.Fatal(err)
	}
	if probe.UserID != -41 || probe.AuthID != -42 || probe.Credential != nil || probe.Nickname != nil {
		t.Fatal("wrong password identity or nullable fields")
	}
	switch phase {
	case "initial":
		if probe.Status != nil || !probe.Enabled() || probe.Avatar != nil || probe.Roles == nil || len(probe.Roles) != 0 {
			t.Fatal("expected enabled account with null status and no roles")
		}
	case "updated":
		want := []string{"ROLE_Zulu", "ROLE_null", "ROLE_😀", "ROLE_Ａ"}
		if !slices.Equal(probe.Roles, want) {
			t.Fatalf("roles = %q, want %q", probe.Roles, want)
		}
		if probe.Status == nil || *probe.Status != 2 || probe.Enabled() || probe.Avatar == nil || *probe.Avatar != "avatar.png" {
			t.Fatal("expected disabled account with updated avatar")
		}
	}
}

func TestLoginPersistence(t *testing.T) {
	store, phase := fixtureStore(t)
	tokens, err := NewTokens([]byte(strings.Repeat("x", 64)), time.Hour, 24*time.Hour, nil)
	if err != nil {
		t.Fatal(err)
	}
	svc := Service{Store: store, Tokens: tokens}
	ctx := t.Context()
	password := os.Getenv("AUTH_ACCOUNT_PASSWORD")
	if _, err = svc.Login(ctx, "migration_fixture", password+"wrong"); !errors.Is(err, errCredentials) && phase == "initial" {
		t.Fatal("incorrect password accepted")
	}
	var lastFamily string
	for range 2 {
		resp, err := svc.Login(ctx, "migration_fixture", password)
		if phase != "initial" {
			if err == nil || resp != (AuthResponse{}) || phase == "updated" && !errors.Is(err, errDisabled) {
				t.Fatal("failed login returned session or wrong error")
			}
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		access, e := tokens.Verify(resp.AccessToken)
		refresh, f := tokens.Verify(resp.RefreshToken)
		if e != nil || f != nil || access.Subject != "migration_fixture" || refresh.Family == lastFamily {
			t.Fatal("invalid claims or repeated family")
		}
		lastFamily = refresh.Family
		var found bool
		err = store.DB.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM sys_refresh_token r JOIN sys_user_auth a ON a.user_id=r.user_id JOIN sys_login_log l ON l.user_id=r.user_id AND l.auth_id=a.id WHERE a.identifier=$1 AND r.jti=$2 AND r.family_id=$3 AND r.expires_at=$4 AND l.success AND l.ip IS NULL AND l.device IS NULL AND l.user_agent IS NULL)`, access.Subject, refresh.ID, refresh.Family, refresh.ExpiresAt.Time.UTC()).Scan(&found)
		if err != nil || !found {
			t.Fatal("missing login rows", err)
		}
	}
	var logs, refreshes int
	if err := store.DB.QueryRowContext(ctx, "SELECT (SELECT count(*) FROM sys_login_log),(SELECT count(*) FROM sys_refresh_token)").Scan(&logs, &refreshes); err != nil || logs != 2 || refreshes != 2 {
		t.Fatalf("rows %d/%d: %v", logs, refreshes, err)
	}
}
