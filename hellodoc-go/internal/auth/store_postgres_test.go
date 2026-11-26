package auth

import (
	"context"
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

func TestRefreshRotationPostgres(t *testing.T) {
	store, _ := fixtureStore(t)
	tokens := refreshTokens(t, nil)
	svc := &Service{Store: store, Tokens: tokens}
	ctx, cancel := context.WithTimeout(t.Context(), 15*time.Second)
	defer cancel()
	login := func(t *testing.T) (string, Claims) {
		t.Helper()
		resp, err := svc.Login(ctx, "migration_fixture", os.Getenv("AUTH_ACCOUNT_PASSWORD"))
		if err != nil {
			t.Fatal(err)
		}
		claims, err := tokens.Verify(resp.RefreshToken)
		if err != nil {
			t.Fatal(err)
		}
		return resp.RefreshToken, claims
	}
	type outcome struct {
		Code    int
		Message string
		Data    AuthResponse
	}
	parse := func(t *testing.T, w *httptest.ResponseRecorder) outcome {
		t.Helper()
		var o outcome
		if w.Code != http.StatusOK || json.Unmarshal(w.Body.Bytes(), &o) != nil {
			// Avoid printing successful responses, which contain tokens.
			t.Fatalf("unexpected refresh response status %d", w.Code)
		}
		return o
	}
	t.Run("child insert failure rolls back", func(t *testing.T) {
		raw, parent := login(t)
		// The fixture trigger fails child insertion for this family only.
		if _, err := store.DB.ExecContext(ctx, "INSERT INTO refresh_failures VALUES ($1)", parent.Family); err != nil {
			t.Fatal(err)
		}
		var pgErr *pgconn.PgError
		response, err := svc.Rotate(ctx, raw)
		if response != (AuthResponse{}) || !errors.As(err, &pgErr) ||
			pgErr.Code != "P0001" || pgErr.Message != "fixture child insert rejected" {
			t.Fatalf("expected injected PostgreSQL child insert failure, got %v", err)
		}
		var intact bool
		if err := store.DB.QueryRowContext(ctx, `SELECT count(*)=1 AND bool_and(
			jti=$1 AND used_at IS NULL AND revoked_at IS NULL AND revoked_reason IS NULL AND replaced_by_jti IS NULL)
			FROM sys_refresh_token WHERE family_id=$2`, parent.ID, parent.Family).Scan(&intact); err != nil || !intact {
			t.Fatalf("failed transaction changed family, query error %v", err)
		}
		if _, err := store.DB.ExecContext(ctx, "DELETE FROM refresh_failures WHERE family_id=$1", parent.Family); err != nil {
			t.Fatal(err)
		}
		if got := parse(t, refreshHTTP(ctx, svc, refreshBody(raw))); got.Code != 0 || got.Data.RefreshToken == "" {
			t.Fatalf("retry after removing fault returned code %d", got.Code)
		}
	})
	t.Run("record and current account validation", func(t *testing.T) {
		raw, parent := login(t)
		sign := func(subject, id, family string) string {
			t.Helper()
			got, err := tokens.Refresh(subject, id, family)
			if err != nil {
				t.Fatal(err)
			}
			return got
		}
		exec := func(statement string, args ...any) {
			t.Helper()
			if _, err := store.DB.ExecContext(ctx, statement, args...); err != nil {
				t.Fatal(err)
			}
		}
		check := func(token string, code int, message string) {
			t.Helper()
			got := parse(t, refreshHTTP(ctx, svc, refreshBody(token)))
			if got.Code != code || got.Message != message || got.Data != (AuthResponse{}) {
				t.Fatalf("code %d, message %q, want %d and %q", got.Code, got.Message, code, message)
			}
		}
		check(sign(parent.Subject, newUUID(), parent.Family), 1005, "Refresh token not found")
		check(sign(parent.Subject, parent.ID, newUUID()), 1005, "Refresh token family mismatch")
		check(sign("missing_fixture", parent.ID, parent.Family), 1005, "User not found")
		check(sign("refresh_other", parent.ID, parent.Family), 1005, "Refresh token user mismatch")
		exec("UPDATE sys_user SET status=2 WHERE id=(SELECT user_id FROM sys_refresh_token WHERE jti=$1)", parent.ID)
		check(raw, 1002, "Account is disabled")
		exec("UPDATE sys_user SET status=0 WHERE id=(SELECT user_id FROM sys_refresh_token WHERE jti=$1)", parent.ID)
		exec("UPDATE sys_refresh_token SET expires_at=issued_at - INTERVAL '1 second' WHERE jti=$1", parent.ID)
		check(raw, 1005, "Refresh token expired")
		var intact bool
		if err := store.DB.QueryRowContext(ctx, `SELECT count(*)=1 AND bool_and(used_at IS NULL AND revoked_at IS NULL
			AND replaced_by_jti IS NULL AND revoked_reason IS NULL) FROM sys_refresh_token WHERE family_id=$1`, parent.Family).
			Scan(&intact); err != nil || !intact {
			t.Fatalf("rejected refresh changed family, query error %v", err)
		}
	})

	t.Run("invalid replay leaves active child usable", func(t *testing.T) {
		for _, tc := range []struct {
			name, message string
			code          int
		}{
			{"family mismatch", "Refresh token family mismatch", 1005},
			{"owner mismatch", "Refresh token user mismatch", 1005},
			{"disabled account", "Account is disabled", 1002},
		} {
			t.Run(tc.name, func(t *testing.T) {
				raw, parent := login(t)
				rotated := parse(t, refreshHTTP(ctx, svc, refreshBody(raw)))
				if rotated.Code != 0 {
					t.Fatalf("first rotation returned %d", rotated.Code)
				}
				child, err := tokens.Verify(rotated.Data.RefreshToken)
				if err != nil {
					t.Fatal(err)
				}
				// Reserve the observer connection so the request cannot use it.
				observer, err := store.DB.Conn(ctx)
				if err != nil {
					t.Fatal(err)
				}
				defer observer.Close()
				setStatus := func(status int) {
					t.Helper()
					if _, err := observer.ExecContext(ctx, `UPDATE sys_user SET status=$1
						WHERE id=(SELECT user_id FROM sys_refresh_token WHERE jti=$2)`, status, parent.ID); err != nil {
						t.Fatal(err)
					}
				}
				switch tc.name {
				case "family mismatch":
					raw, err = tokens.Refresh(parent.Subject, parent.ID, newUUID())
				case "owner mismatch":
					raw, err = tokens.Refresh("refresh_other", parent.ID, parent.Family)
				case "disabled account":
					setStatus(2)
					defer setStatus(0)
				}
				if err != nil {
					t.Fatal(err)
				}
				got := parse(t, refreshHTTP(ctx, svc, refreshBody(raw)))
				if got.Code != tc.code || got.Message != tc.message || got.Data != (AuthResponse{}) {
					t.Errorf("code %d, message %q, want %d and %q", got.Code, got.Message, tc.code, tc.message)
				}
				var intact bool
				if err := observer.QueryRowContext(ctx, `SELECT (SELECT count(*) FROM sys_refresh_token WHERE family_id=p.family_id)=2
					AND p.revoked_reason='ROTATED' AND p.used_at IS NOT NULL AND p.revoked_at=p.used_at AND p.replaced_by_jti=c.jti
					AND c.parent_jti=p.jti AND c.family_id=p.family_id AND c.user_id=p.user_id
					AND c.used_at IS NULL AND c.revoked_at IS NULL AND c.revoked_reason IS NULL AND c.replaced_by_jti IS NULL
					FROM sys_refresh_token p CROSS JOIN sys_refresh_token c WHERE p.jti=$1 AND c.jti=$2`,
					parent.ID, child.ID).Scan(&intact); err != nil || !intact {
					t.Errorf("invalid replay changed committed family state, query error %v", err)
				}
				if tc.name == "disabled account" {
					setStatus(0)
				}
				if got := parse(t, refreshHTTP(ctx, svc, refreshBody(rotated.Data.RefreshToken))); got.Code != 0 || got.Data.RefreshToken == "" {
					t.Fatalf("original child could not rotate, code %d", got.Code)
				}
			})
		}
	})

	t.Run("concurrent same token", func(t *testing.T) {
		raw, parent := login(t)
		other, independent := login(t)
		if _, err := store.DB.ExecContext(ctx, "UPDATE sys_role SET role_code='refresh_current' WHERE role_code='user'"); err != nil {
			t.Fatal(err)
		}

		// Hold the parent row until both requests reach a PostgreSQL lock wait.
		// Since the holder uses a third session, the two waiters are distinct connections.
		tx, err := store.DB.BeginTx(ctx, nil)
		if err != nil {
			t.Fatal(err)
		}
		defer tx.Rollback()
		var blocker int
		if err := tx.QueryRowContext(ctx, `SELECT pg_backend_pid() FROM sys_refresh_token WHERE jti=$1 FOR UPDATE`, parent.ID).Scan(&blocker); err != nil {
			t.Fatal(err)
		}
		responses := make(chan *httptest.ResponseRecorder, 2)
		for range 2 {
			go func() {
				responses <- refreshHTTP(ctx, svc, refreshBody(raw))
			}()
		}
		waitForRefreshLocks(t, ctx, store.DB, blocker, 2, responses)
		if err := tx.Commit(); err != nil {
			t.Fatal(err)
		}
		var successes, reuses int
		var winner AuthResponse
		for range 2 {
			select {
			case response := <-responses:
				got := parse(t, response)
				switch got.Code {
				case 0:
					successes++
					winner = got.Data
				case 1005:
					if got.Message == "Refresh token reuse detected" && got.Data == (AuthResponse{}) {
						reuses++
					}
				}
			case <-ctx.Done():
				t.Fatal("refresh requests did not finish")
			}
		}
		if successes != 1 || reuses != 1 {
			t.Fatalf("wanted one success and one reuse, got %d/%d", successes, reuses)
		}
		child, err := tokens.Verify(winner.RefreshToken)
		access, accessErr := tokens.Verify(winner.AccessToken)
		if err != nil || accessErr != nil || child.Subject != parent.Subject || child.Family != parent.Family ||
			child.ID == parent.ID || access.Subject != parent.Subject || !slices.Equal(access.Roles, []string{"ROLE_refresh_current"}) ||
			winner.TokenType != "Bearer" || winner.IsInitialPwd || winner.Nickname == nil || *winner.Nickname != "Migration Fixture" {
			t.Fatal("invalid rotated response or current permissions")
		}
		var correct bool
		if err := store.DB.QueryRowContext(ctx, `SELECT (SELECT count(*) FROM sys_refresh_token WHERE family_id=p.family_id)=2
			AND p.revoked_reason='ROTATED' AND p.replaced_by_jti=c.jti AND p.used_at IS NOT NULL AND p.revoked_at=p.used_at
			AND c.parent_jti=p.jti AND c.user_id=p.user_id AND c.family_id=p.family_id AND c.expires_at=$3
			AND c.revoked_reason='REUSE_DETECTED' AND c.revoked_at IS NOT NULL AND c.used_at IS NULL AND c.replaced_by_jti IS NULL
			FROM sys_refresh_token p CROSS JOIN sys_refresh_token c WHERE p.jti=$1 AND c.jti=$2`,
			parent.ID, child.ID, child.ExpiresAt.Time.UTC()).Scan(&correct); err != nil || !correct {
			t.Fatalf("wrong committed rotation and revocation state, query error %v", err)
		}
		if got := parse(t, refreshHTTP(ctx, svc, refreshBody(winner.RefreshToken))); got.Code != 1005 {
			t.Fatal("revoked winner token rotated")
		}
		if got := parse(t, refreshHTTP(ctx, svc, refreshBody(other))); got.Code != 0 || got.Data.RefreshToken == "" {
			t.Fatalf("independent family returned code %d", got.Code)
		}
		var unaffected bool
		if err := store.DB.QueryRowContext(ctx, `SELECT count(*)=2 AND count(revoked_at)=1
			AND count(*) FILTER (WHERE revoked_reason='ROTATED')=1
			FROM sys_refresh_token WHERE family_id=$1`, independent.Family).Scan(&unaffected); err != nil || !unaffected {
			t.Fatalf("unrelated family changed, query error %v", err)
		}
	})
	t.Run("ancestor replay cannot miss concurrent grandchild", func(t *testing.T) {
		old, parent := login(t)
		first := parse(t, refreshHTTP(ctx, svc, refreshBody(old)))
		if first.Code != 0 {
			t.Fatalf("first rotation returned %d", first.Code)
		}
		child, err := tokens.Verify(first.Data.RefreshToken)
		if err != nil {
			t.Fatal(err)
		}
		holder, err := store.DB.BeginTx(ctx, nil)
		if err != nil {
			t.Fatal(err)
		}
		defer holder.Rollback()
		var blocker int
		if err := holder.QueryRowContext(ctx, `SELECT pg_backend_pid() FROM sys_refresh_token WHERE jti=$1 FOR UPDATE`,
			child.ID).Scan(&blocker); err != nil {
			t.Fatal(err)
		}
		rotation, replay := make(chan *httptest.ResponseRecorder, 1), make(chan *httptest.ResponseRecorder, 1)
		go func() { rotation <- refreshHTTP(ctx, svc, refreshBody(first.Data.RefreshToken)) }()
		waitForRefreshLocks(t, ctx, store.DB, blocker, 1, rotation)
		go func() { replay <- refreshHTTP(ctx, svc, refreshBody(old)) }()
		waitForRefreshLocks(t, ctx, store.DB, blocker, 2, replay)
		if err := holder.Commit(); err != nil {
			t.Fatal(err)
		}
		finish := func(result chan *httptest.ResponseRecorder) outcome {
			t.Helper()
			select {
			case w := <-result:
				return parse(t, w)
			case <-ctx.Done():
				t.Fatal("refresh request did not finish")
				return outcome{}
			}
		}
		fresh, reused := finish(rotation), finish(replay)
		if fresh.Code != 0 || reused.Code != 1005 || reused.Message != "Refresh token reuse detected" {
			t.Fatalf("child rotation/replay returned codes %d/%d", fresh.Code, reused.Code)
		}
		grandchild, err := tokens.Verify(fresh.Data.RefreshToken)
		if err != nil {
			t.Fatal(err)
		}
		var revoked bool
		if err := store.DB.QueryRowContext(ctx, `SELECT count(*)=3 AND count(revoked_at)=3
			AND count(*) FILTER (WHERE jti=$1 AND parent_jti=$2 AND revoked_reason='REUSE_DETECTED')=1
			FROM sys_refresh_token WHERE family_id=$3`, grandchild.ID, child.ID, parent.Family).
			Scan(&revoked); err != nil || !revoked {
			t.Fatalf("ancestor replay missed a committed grandchild, query error %v", err)
		}
	})
}

func waitForRefreshLocks(t *testing.T, ctx context.Context, db *sql.DB, blocker, expected int, response <-chan *httptest.ResponseRecorder) {
	t.Helper()
	ticker := time.NewTicker(10 * time.Millisecond)
	defer ticker.Stop()
	for {
		var waiting int
		err := db.QueryRowContext(ctx, `WITH RECURSIVE blockers(pid) AS (
			SELECT $1::int UNION SELECT a.pid FROM pg_stat_activity a JOIN blockers b
			ON b.pid=ANY(pg_blocking_pids(a.pid))
		) SELECT count(*)-1 FROM blockers`, blocker).Scan(&waiting)
		if err != nil {
			t.Fatal(err)
		}
		if waiting == expected {
			return
		}
		select {
		case w := <-response:
			t.Fatalf("refresh completed before lock release, HTTP status %d", w.Code)
		case <-ctx.Done():
			t.Fatal("refresh requests did not block in PostgreSQL")
		case <-ticker.C:
		}
	}
}
