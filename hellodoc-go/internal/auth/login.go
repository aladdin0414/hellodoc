package auth

import (
	"context"
	"crypto/rand"
	"database/sql"
	"errors"
	"fmt"
)

type AuthResponse struct {
	AccessToken  string  `json:"accessToken"`
	RefreshToken string  `json:"refreshToken"`
	TokenType    string  `json:"tokenType"`
	Nickname     *string `json:"nickname"`
	Avatar       *string `json:"avatar"`
	IsInitialPwd bool    `json:"isInitialPwd"`
}

var errCredentials = errors.New("incorrect username or password")
var errDisabled = errors.New("account disabled")

func newUUID() string {
	var b [16]byte
	rand.Read(b[:])
	b[6] = b[6]&0x0f | 0x40
	b[8] = b[8]&0x3f | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[:4], b[4:6], b[6:8], b[8:10], b[10:])
}

func (s *Service) Login(ctx context.Context, username, password string) (AuthResponse, error) {
	return s.login(ctx, username, &password)
}

func (s *Service) login(ctx context.Context, username string, password *string) (AuthResponse, error) {
	encoder := s.passwordEncoder()
	// Prepare the dummy hash before loading either an existing or missing user,
	// as Spring's DaoAuthenticationProvider does.
	s.dummyOnce.Do(func() {
		s.dummyHash, s.dummyErr = encoder.Encode("userNotFoundPassword")
	})
	if s.dummyErr != nil {
		return AuthResponse{}, s.dummyErr
	}
	a, err := s.Store.Lookup(ctx, username)
	if errors.Is(err, sql.ErrNoRows) {
		if password != nil {
			encoder.Matches(*password, s.dummyHash)
		}
		return AuthResponse{}, errCredentials
	}
	if err != nil {
		return AuthResponse{}, err
	}
	if !a.Enabled() {
		return AuthResponse{}, errDisabled
	}
	if password == nil || a.Credential == nil || !encoder.Matches(*password, *a.Credential) {
		return AuthResponse{}, errCredentials
	}
	access, err := s.Tokens.Access(a.Identifier, a.Roles)
	if err != nil {
		return AuthResponse{}, err
	}
	jti, family := newUUID(), newUUID()
	refresh, err := s.Tokens.Refresh(a.Identifier, jti, family)
	if err != nil {
		return AuthResponse{}, err
	}
	claims, err := s.Tokens.Verify(refresh)
	if err != nil {
		return AuthResponse{}, err
	}
	tx, err := s.Store.DB.BeginTx(ctx, nil)
	if err != nil {
		return AuthResponse{}, err
	}
	defer tx.Rollback()
	_, err = tx.ExecContext(ctx, `INSERT INTO sys_refresh_token(user_id,jti,family_id,issued_at,expires_at,create_time) VALUES($1,$2,$3,$4,$5,$4)`, a.UserID, jti, family, s.Tokens.now().UTC(), claims.ExpiresAt.Time.UTC())
	if err != nil {
		return AuthResponse{}, err
	}
	_, err = tx.ExecContext(ctx, `INSERT INTO sys_login_log(user_id,auth_id,success) VALUES($1,$2,true)`, a.UserID, a.AuthID)
	if err != nil {
		return AuthResponse{}, err
	}
	if err = tx.Commit(); err != nil {
		return AuthResponse{}, err
	}
	return AuthResponse{AccessToken: access, RefreshToken: refresh, TokenType: "Bearer", Nickname: a.Nickname, Avatar: a.Avatar}, nil
}
