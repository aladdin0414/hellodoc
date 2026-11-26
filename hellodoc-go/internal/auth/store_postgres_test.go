package auth

import (
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgconn"
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
	if phase == "initial" {
		codec := &observedPasswords{PasswordCodec: PasswordCodec{Encoder: "sha256", Pepper: "pepper", Output: "base64"}}
		missing := Service{Store: store, Passwords: codec}
		for _, raw := range []string{"first", "second"} {
			resp, err := missing.Login(ctx, "missing_user", raw)
			if !errors.Is(err, errCredentials) || resp != (AuthResponse{}) {
				t.Fatalf("missing account returned a session or error %v", err)
			}
		}
		if codec.encodes != 1 || len(codec.matches) != 2 || codec.matches[0][0] != "first" || codec.matches[1][0] != "second" ||
			codec.matches[0][1] != codec.matches[1][1] || len(codec.matches[0][1]) != 44 {
			t.Fatal("missing accounts did not compare both passwords with one configured dummy hash")
		}
	}
	if _, err = svc.Login(ctx, "migration_fixture", password+"wrong"); !errors.Is(err, errCredentials) && phase == "initial" {
		t.Fatal("incorrect password accepted")
	}
	var lastFamily string
	for range 2 {
		resp, err := svc.Login(ctx, "migration_fixture", password)
		if phase != "initial" {
			if err == nil || resp != (AuthResponse{}) {
				t.Fatal("failed login returned a session")
			}
			if phase == "roles" {
				var pgErr *pgconn.PgError
				if !errors.As(err, &pgErr) || pgErr.Code != "42501" || !strings.Contains(pgErr.Message, "sys_login_log") {
					t.Fatalf("expected denied login audit insert, got %v", err)
				}
			} else {
				if !errors.Is(err, errDisabled) {
					t.Fatalf("disabled login returned %v", err)
				}
				for _, body := range []string{`{"username":"migration_fixture"}`, `{"username":"migration_fixture","password":null}`} {
					checkAuthResult(t, &svc, "/api/auth/login", body, 1002)
				}
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

func TestRegisterAtomic(t *testing.T) {
	store, phase := fixtureStore(t)
	svc := Service{Store: store, Passwords: PasswordCodec{Encoder: "sha256"}}
	ctx := t.Context()
	name := "new_" + phase
	req := RegisterRequest{Username: name, Password: "fixture", Nickname: " ", RealName: "\t"}
	if phase != "deleted" {
		req.Email = " " + phase + "@example.invalid "
	}
	count := func() [3]int {
		var rows [3]int
		err := store.DB.QueryRowContext(ctx, `SELECT (SELECT count(*) FROM sys_user),
			(SELECT count(*) FROM sys_user_auth), (SELECT count(*) FROM sys_user_role)`).
			Scan(&rows[0], &rows[1], &rows[2])
		if err != nil {
			t.Fatal(err)
		}
		return rows
	}
	before := count()
	type outcome struct {
		user PublicUser
		err  error
	}
	results := make(chan outcome, 2)
	for range 2 {
		go func() { u, e := svc.Register(ctx, req); results <- outcome{u, e} }()
	}
	var successes, conflicts, denied int
	for range 2 {
		result := <-results
		if result.err != nil {
			if errors.Is(result.err, errConflict) {
				conflicts++
			} else {
				var pgErr *pgconn.PgError
				if phase == "roles" && errors.As(result.err, &pgErr) && pgErr.Code == "42501" {
					denied++
				} else {
					t.Error(result.err)
				}
			}
			continue
		}
		successes++
		u := result.user
		if u.Nickname != name || u.RealName != nil || u.Phone != nil || u.Roles == nil || len(u.Roles) != 0 {
			t.Fatal("profile fields differ")
		}
		if phase == "deleted" && u.Email != nil || phase != "deleted" && (u.Email == nil || *u.Email != req.Email) {
			t.Fatal("email normalization differs")
		}
		if u.ID == 0 || u.Status != 0 || u.LanguageMode != "AUTO" || u.CreateTime.IsZero() || u.UpdateTime.IsZero() ||
			u.Username != nil || u.QuickNoteKBID != nil || u.Avatar != nil {
			t.Fatalf("unexpected public defaults: %+v", u)
		}
		data, err := json.Marshal(u)
		if err != nil {
			t.Fatal(err)
		}
		var fields map[string]any
		if err := json.Unmarshal(data, &fields); err != nil {
			t.Fatal(err)
		}
		keys := []string{"id", "nickname", "realName", "email", "phone", "avatar", "languageMode", "quickNoteKbId", "status", "createTime", "updateTime", "roles", "username"}
		if len(fields) != len(keys) {
			t.Fatal("profile field count")
		}
		for _, k := range keys {
			if _, ok := fields[k]; !ok {
				t.Fatalf("missing %s", k)
			}
		}
		a, err := store.Lookup(ctx, name)
		if err != nil || a.Credential == nil || !svc.Passwords.Matches(req.Password, *a.Credential) {
			t.Fatal("credential mismatch", err)
		}
		var verified bool
		if err := store.DB.QueryRowContext(ctx, "SELECT verified FROM sys_user_auth WHERE id=$1", a.AuthID).Scan(&verified); err != nil || !verified {
			t.Fatal("identity unverified", err)
		}
		want := map[string][]string{"initial": {"ROLE_user"}, "updated": {"ROLE_common"}, "deleted": {}}[phase]
		if !slices.Equal(a.Roles, want) {
			t.Fatalf("roles %v want %v", a.Roles, want)
		}
	}
	if phase == "roles" {
		if successes != 0 || denied != 2 || count() != before {
			t.Fatal("partial registration")
		}
		return
	}
	want := before
	want[0]++
	want[1]++
	if phase != "deleted" {
		want[2]++
	}
	if after := count(); successes != 1 || conflicts != 1 || after != want {
		t.Fatalf("successes %d conflicts %d and rows %v; want one success, one conflict and rows %v", successes, conflicts, after, want)
	}

	for _, fields := range []string{"", `,"password":null`} {
		checkAuthResult(t, &svc, "/api/auth/register", fmt.Sprintf(`{"username":%q%s}`, name, fields), 1003)
		checkAuthResult(t, &svc, "/api/auth/register", fmt.Sprintf(`{"username":"missing_password"%s}`, fields), 9999)
	}
	mux := http.NewServeMux()
	svc.RegisterRoutes(mux)
	for _, tc := range []struct{ locale, message string }{
		{"zh-CN", "用户名已存在"},
		{"en-US", "Username already exists"},
	} {
		r := httptest.NewRequest("POST", "/api/auth/register", strings.NewReader(
			fmt.Sprintf(`{"username":%q,"password":"fixture"}`, name)))
		r.Header.Set("Content-Type", "application/json")
		w := httptest.NewRecorder()
		w.Header().Set("Content-Language", tc.locale)
		mux.ServeHTTP(w, r)
		var body struct {
			Code    int
			Message string
			Data    any
		}
		if w.Code != http.StatusOK || json.Unmarshal(w.Body.Bytes(), &body) != nil ||
			body.Code != 1003 || body.Message != tc.message || body.Data != nil {
			t.Fatalf("duplicate response: status %d, body %s", w.Code, w.Body.String())
		}
	}
	if phase == "initial" {
		other := req
		other.Username = "different_username"
		if _, err := svc.Register(ctx, other); err == nil || errors.Is(err, errConflict) {
			t.Fatalf("different username with duplicate email returned %v", err)
		}
	}
	if after := count(); after != want {
		t.Fatalf("repeated request changed rows from %v to %v", want, after)
	}
	if phase == "initial" {
		codec := &observedPasswords{
			PasswordCodec: PasswordCodec{Encoder: "sha256"},
			beforeEncode: func() {
				if used := store.DB.Stats().InUse; used != 0 {
					t.Errorf("password encoding held %d database connections", used)
				}
			},
		}
		httpService := Service{Store: store, Passwords: codec}
		httpMux := http.NewServeMux()
		httpService.RegisterRoutes(httpMux)
		r := httptest.NewRequest("POST", "/api/auth/register", strings.NewReader(
			`{"username":"http_fixture","password":"fixture","realName":" Fixture Name ","nickname":"Fixture Nickname","phone":"   "}`))
		r.Header.Set("Content-Type", "application/json")
		r.Header.Set("X-Language", "en")
		w := httptest.NewRecorder()
		httpService.Bearer(httpMux).ServeHTTP(w, r)
		var body struct {
			Code    int
			Message string
			Data    PublicUser
		}
		if w.Code != http.StatusOK || json.Unmarshal(w.Body.Bytes(), &body) != nil ||
			body.Code != 0 || body.Message != "Success" || body.Data.ID == 0 ||
			body.Data.Nickname != "Fixture Nickname" || body.Data.RealName == nil ||
			*body.Data.RealName != " Fixture Name " || body.Data.Phone != nil {
			t.Fatalf("registration response: status %d, body %s", w.Code, w.Body.String())
		}
	}
}

func checkAuthResult(t *testing.T, svc *Service, path, body string, want int) {
	t.Helper()
	mux := http.NewServeMux()
	svc.RegisterRoutes(mux)
	r := httptest.NewRequest("POST", path, strings.NewReader(body))
	r.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	mux.ServeHTTP(w, r)
	var result struct {
		Code int
		Data any
	}
	if w.Code != http.StatusOK || json.Unmarshal(w.Body.Bytes(), &result) != nil || result.Code != want || result.Data != nil {
		t.Fatalf("%s: status %d, body %s, want code %d", path, w.Code, w.Body.String(), want)
	}
}

type observedPasswords struct {
	PasswordCodec
	encodes      int
	matches      [][2]string
	beforeEncode func()
}

func (c *observedPasswords) Encode(raw string) (string, error) {
	c.encodes++
	if c.beforeEncode != nil {
		c.beforeEncode()
	}
	return c.PasswordCodec.Encode(raw)
}

func (c *observedPasswords) Matches(raw, encoded string) bool {
	c.matches = append(c.matches, [2]string{raw, encoded})
	return c.PasswordCodec.Matches(raw, encoded)
}
