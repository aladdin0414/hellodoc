package auth

import (
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"slices"
	"strconv"
	"testing"
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
