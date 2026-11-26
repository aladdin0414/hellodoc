package auth

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"
)

type RegisterRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
	Nickname string `json:"nickname"`
	RealName string `json:"realName"`
	Email    string `json:"email"`
	Phone    string `json:"phone"`
}

type PublicUser struct {
	ID            int64     `json:"id"`
	Nickname      string    `json:"nickname"`
	RealName      *string   `json:"realName"`
	Email         *string   `json:"email"`
	Phone         *string   `json:"phone"`
	Avatar        *string   `json:"avatar"`
	LanguageMode  string    `json:"languageMode"`
	QuickNoteKBID *int64    `json:"quickNoteKbId"`
	Status        int16     `json:"status"`
	CreateTime    time.Time `json:"createTime"`
	UpdateTime    time.Time `json:"updateTime"`
	Roles         []string  `json:"roles"`
	Username      *string   `json:"username"`
}

var errConflict = errors.New("username already exists")

func optionalRegistrationText(value string) *string {
	if strings.TrimFunc(value, javaWhitespace) == "" {
		return nil
	}
	return &value
}

func (s *Service) Register(ctx context.Context, req RegisterRequest) (PublicUser, error) {
	return s.register(ctx, req, &req.Password)
}

func (s *Service) register(ctx context.Context, req RegisterRequest, password *string) (PublicUser, error) {
	user := PublicUser{
		Nickname: req.Username,
		RealName: optionalRegistrationText(req.RealName),
		Email:    optionalRegistrationText(req.Email),
		Phone:    optionalRegistrationText(req.Phone),
		Roles:    []string{},
	}
	if nickname := optionalRegistrationText(req.Nickname); nickname != nil {
		user.Nickname = *nickname
	}
	usernameExists := func(queryRow func(context.Context, string, ...any) *sql.Row) (bool, error) {
		var exists bool
		err := queryRow(ctx, `
			SELECT EXISTS(SELECT 1 FROM sys_user_auth WHERE identity_type = 'PASSWORD' AND identifier = $1)`,
			req.Username).Scan(&exists)
		return exists, err
	}
	exists, err := usernameExists(s.Store.DB.QueryRowContext)
	if err != nil {
		return PublicUser{}, err
	}
	if exists {
		return PublicUser{}, errConflict
	}
	if password == nil {
		return PublicUser{}, errRequest
	}
	credential, err := s.passwordEncoder().Encode(*password)
	if err != nil {
		return PublicUser{}, err
	}

	tx, err := s.Store.DB.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return PublicUser{}, err
	}
	defer tx.Rollback()

	now := time.Now().UTC()
	err = tx.QueryRowContext(ctx, `
		INSERT INTO sys_user(nickname, real_name, email, phone, status, create_time, update_time, language_mode)
		VALUES ($1, $2, $3, $4, 0, $5, $5, 'AUTO')
		ON CONFLICT DO NOTHING
		RETURNING id, status, create_time, update_time, language_mode`,
		user.Nickname, user.RealName, user.Email, user.Phone, now).
		Scan(&user.ID, &user.Status, &user.CreateTime, &user.UpdateTime, &user.LanguageMode)
	if errors.Is(err, sql.ErrNoRows) {
		// A concurrent registration may collide on email or phone before
		// reaching the identity insert. Check whether it claimed this username.
		exists, lookupErr := usernameExists(tx.QueryRowContext)
		if lookupErr != nil {
			return PublicUser{}, lookupErr
		}
		if exists {
			return PublicUser{}, errConflict
		}
	}
	if err != nil {
		return PublicUser{}, err
	}

	identity, err := tx.ExecContext(ctx, `
		INSERT INTO sys_user_auth(user_id, identity_type, identifier, credential, status, verified, create_time)
		VALUES ($1, 'PASSWORD', $2, $3, 0, true, $4)
		ON CONFLICT (identity_type, identifier) DO NOTHING`, user.ID, req.Username, credential, now)
	if err != nil {
		return PublicUser{}, err
	}
	if rows, err := identity.RowsAffected(); err != nil {
		return PublicUser{}, err
	} else if rows == 0 {
		return PublicUser{}, errConflict
	}

	_, err = tx.ExecContext(ctx, `
		INSERT INTO sys_user_role(user_id, role_id)
		SELECT $1, id FROM sys_role WHERE role_code IN ('user', 'common')
		ORDER BY CASE role_code WHEN 'user' THEN 0 ELSE 1 END
		LIMIT 1`, user.ID)
	if err != nil {
		return PublicUser{}, err
	}
	if err := tx.Commit(); err != nil {
		return PublicUser{}, err
	}
	return user, nil
}
