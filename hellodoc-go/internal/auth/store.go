package auth

import (
	"context"
	"database/sql"
	"encoding/json"
	"slices"
	"unicode/utf16"

	_ "github.com/jackc/pgx/v5/stdlib"
)

type Store struct {
	DB *sql.DB
}

type Account struct {
	UserID, AuthID               int64
	Identifier, LanguageMode     string
	Credential, Nickname, Avatar *string
	Status                       *int16
	Roles                        []string
}

func (a Account) Enabled() bool {
	return a.Status == nil || *a.Status == 0
}

func (s *Store) Lookup(ctx context.Context, username string) (Account, error) {
	return lookupAccount(ctx, s.DB, username)
}

func lookupAccount(ctx context.Context, db interface {
	QueryRowContext(context.Context, string, ...any) *sql.Row
}, username string) (Account, error) {
	var a Account
	var rolesJSON []byte
	err := db.QueryRowContext(ctx, `
		SELECT u.id, a.id, a.identifier, a.credential, u.status, u.nickname, u.avatar, COALESCE(u.language_mode, 'AUTO'),
		       ARRAY_TO_JSON(ARRAY(
		           SELECT 'ROLE_' || COALESCE(r.role_code, 'null')
		           FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
		           WHERE ur.user_id = u.id))
		FROM sys_user_auth a JOIN sys_user u ON u.id = a.user_id
		WHERE a.identifier = $1 AND a.identity_type = 'PASSWORD'`, username).
		Scan(&a.UserID, &a.AuthID, &a.Identifier, &a.Credential, &a.Status, &a.Nickname, &a.Avatar, &a.LanguageMode, &rolesJSON)
	if err != nil {
		return Account{}, err
	}
	if err := json.Unmarshal(rolesJSON, &a.Roles); err != nil {
		return Account{}, err
	}
	// Spring User stores authorities in a TreeSet ordered by Java UTF-16 code units.
	slices.SortFunc(a.Roles, func(left, right string) int {
		return slices.Compare(utf16.Encode([]rune(left)), utf16.Encode([]rune(right)))
	})
	a.Roles = slices.Compact(a.Roles)
	return a, nil
}
