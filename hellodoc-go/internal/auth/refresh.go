package auth

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

var (
	errRefreshType    = errors.New("refresh token required")
	errRefreshMissing = errors.New("refresh token not found")
	errRefreshFamily  = errors.New("refresh token family mismatch")
	errRefreshExpiry  = errors.New("refresh record expired")
	errRefreshAccount = errors.New("refresh account not found")
	errRefreshOwner   = errors.New("refresh token user mismatch")
	errRefreshReuse   = errors.New("refresh token reuse detected")
)

func (s *Service) Rotate(ctx context.Context, raw string) (AuthResponse, error) {
	claims, err := s.Tokens.Verify(raw)
	if err != nil {
		return AuthResponse{}, err
	}
	if claims.Type != refreshTokenType {
		return AuthResponse{}, errRefreshType
	}

	tx, err := s.Store.DB.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return AuthResponse{}, err
	}
	defer tx.Rollback()

	// Read the candidate owner without a row lock. Recheck it after taking
	// the account and family locks and locking the issued record.
	var lockOwner int64
	err = tx.QueryRowContext(ctx, `SELECT user_id FROM sys_refresh_token WHERE jti=$1`, claims.ID).Scan(&lockOwner)
	if errors.Is(err, sql.ErrNoRows) {
		return AuthResponse{}, errRefreshMissing
	}
	if err != nil {
		return AuthResponse{}, err
	}
	if err := lockRefreshAccount(ctx, tx, lockOwner, false); err != nil {
		return AuthResponse{}, err
	}
	// Serialize even when the requests address different generations. Otherwise
	// reuse can revoke a child while its concurrent rotation inserts a grandchild
	// outside the revocation statement's snapshot.
	if err := lockRefreshFamily(ctx, tx, claims.Family); err != nil {
		return AuthResponse{}, err
	}
	var userID int64
	var family string
	var expiresAt time.Time
	var usedAt, revokedAt sql.NullTime
	err = tx.QueryRowContext(ctx, `SELECT user_id, family_id, expires_at, used_at, revoked_at
		FROM sys_refresh_token WHERE jti=$1 FOR UPDATE`, claims.ID).Scan(&userID, &family, &expiresAt, &usedAt, &revokedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return AuthResponse{}, errRefreshMissing
	}
	if err != nil {
		return AuthResponse{}, err
	}
	if family != claims.Family {
		return AuthResponse{}, errRefreshFamily
	}
	now := s.Tokens.now().UTC()
	if now.Truncate(time.Millisecond).After(claims.ExpiresAt.Time) {
		return AuthResponse{}, ErrTokenExpired
	}
	if now.After(expiresAt) {
		return AuthResponse{}, errRefreshExpiry
	}
	account, err := lookupAccount(ctx, tx, claims.Subject)
	if errors.Is(err, sql.ErrNoRows) {
		return AuthResponse{}, errRefreshAccount
	}
	if err != nil {
		return AuthResponse{}, err
	}
	if userID != lockOwner || userID != account.UserID {
		return AuthResponse{}, errRefreshOwner
	}
	if !account.Enabled() {
		return AuthResponse{}, errDisabled
	}
	if usedAt.Valid || revokedAt.Valid {
		if _, err := tx.ExecContext(ctx, `UPDATE sys_refresh_token SET revoked_at=$1, revoked_reason='REUSE_DETECTED'
			WHERE family_id=$2 AND user_id=$3 AND revoked_at IS NULL`, now, family, account.UserID); err != nil {
			return AuthResponse{}, err
		}
		if err := tx.Commit(); err != nil {
			return AuthResponse{}, err
		}
		return AuthResponse{}, errRefreshReuse
	}

	access, err := s.Tokens.Access(account.Identifier, account.Roles)
	if err != nil {
		return AuthResponse{}, err
	}
	childID := newUUID()
	refresh, err := s.Tokens.Refresh(account.Identifier, childID, family)
	if err != nil {
		return AuthResponse{}, err
	}
	child, err := s.Tokens.Verify(refresh)
	if err != nil {
		return AuthResponse{}, err
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO sys_refresh_token(user_id,jti,family_id,parent_jti,issued_at,expires_at,create_time)
		VALUES($1,$2,$3,$4,$5,$6,$5)`, account.UserID, childID, family, claims.ID, now, child.ExpiresAt.Time.UTC()); err != nil {
		return AuthResponse{}, err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE sys_refresh_token SET used_at=$1, revoked_at=$1, revoked_reason='ROTATED', replaced_by_jti=$2
		WHERE jti=$3`, now, childID, claims.ID); err != nil {
		return AuthResponse{}, err
	}
	if err := tx.Commit(); err != nil {
		return AuthResponse{}, err
	}
	return AuthResponse{AccessToken: access, RefreshToken: refresh, TokenType: "Bearer", Nickname: account.Nickname, Avatar: account.Avatar}, nil
}
