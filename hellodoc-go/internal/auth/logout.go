package auth

import (
	"context"
	"database/sql"
	"errors"
)

// Logout revokes the authenticated account's sessions, or the one family
// represented by an anonymous caller's refresh token.
func (s *Service) Logout(ctx context.Context, principal *Account, refresh *string) error {
	var claims Claims
	if principal == nil {
		if refresh == nil || *refresh == "" {
			return nil
		}
		var ok bool
		claims, ok = s.Tokens.verifyLogoutRefresh(*refresh)
		if !ok {
			return nil
		}
	}

	tx, err := s.Store.DB.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return err
	}
	defer tx.Rollback()

	if principal == nil {
		var owner int64
		err := tx.QueryRowContext(ctx, `SELECT issued.user_id FROM sys_refresh_token issued
			JOIN sys_user_auth a ON a.user_id=issued.user_id
			WHERE issued.jti=$1 AND issued.family_id=$2 AND a.identifier=$3 AND a.identity_type='PASSWORD'`,
			claims.ID, claims.Family, claims.Subject).Scan(&owner)
		if errors.Is(err, sql.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		if err := lockRefreshAccount(ctx, tx, owner, false); err != nil {
			return err
		}
		if err := lockRefreshFamily(ctx, tx, claims.Family); err != nil {
			return err
		}
		now := s.Tokens.now().UTC()
		// Bind the signed token to an issued row and its password identity.
		// Historical rows can authorize revocation of their family's descendants.
		if _, err := tx.ExecContext(ctx, `UPDATE sys_refresh_token t SET revoked_at=$1, revoked_reason='LOGOUT'
			WHERE t.family_id=$2 AND t.user_id=$3 AND t.revoked_at IS NULL AND EXISTS (
				SELECT 1 FROM sys_refresh_token issued JOIN sys_user_auth a ON a.user_id=issued.user_id
				WHERE issued.jti=$4 AND issued.family_id=t.family_id AND issued.user_id=t.user_id
					AND a.identifier=$5 AND a.identity_type='PASSWORD')`, now, claims.Family, owner, claims.ID, claims.Subject); err != nil {
			return err
		}
	} else {
		if err := lockRefreshAccount(ctx, tx, principal.UserID, true); err != nil {
			return err
		}
		// The statement starts after obtaining the lock, so its snapshot
		// includes sessions committed by login or rotation while we waited.
		if _, err := tx.ExecContext(ctx, `UPDATE sys_refresh_token SET revoked_at=$1, revoked_reason='LOGOUT'
			WHERE user_id=$2 AND revoked_at IS NULL`, s.Tokens.now().UTC(), principal.UserID); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (t *Tokens) verifyLogoutRefresh(raw string) (Claims, bool) {
	claims, err := t.Verify(raw)
	return claims, claims.Type == refreshTokenType && (err == nil || errors.Is(err, ErrTokenExpired))
}
