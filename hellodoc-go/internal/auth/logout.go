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
		if err := lockRefreshFamily(ctx, tx, claims.Family); err != nil {
			return err
		}
		now := s.Tokens.now().UTC()
		// Bind the signed token to an issued row and its password identity.
		// Historical rows can authorize revocation of their family's descendants.
		_, err = tx.ExecContext(ctx, `UPDATE sys_refresh_token t SET revoked_at=$1, revoked_reason='LOGOUT'
			WHERE t.family_id=$2 AND t.revoked_at IS NULL AND EXISTS (
				SELECT 1 FROM sys_refresh_token issued JOIN sys_user_auth a ON a.user_id=issued.user_id
				WHERE issued.jti=$3 AND issued.family_id=t.family_id AND issued.user_id=t.user_id
					AND a.identifier=$4 AND a.identity_type='PASSWORD')`, now, claims.Family, claims.ID, claims.Subject)
	} else {
		err = s.logoutAccount(ctx, tx, principal.UserID)
	}
	if err != nil {
		return err
	}
	return tx.Commit()
}

func (t *Tokens) verifyLogoutRefresh(raw string) (Claims, bool) {
	claims, err := t.Verify(raw)
	return claims, claims.Type == refreshTokenType && (err == nil || errors.Is(err, ErrTokenExpired))
}

func (s *Service) logoutAccount(ctx context.Context, tx *sql.Tx, userID int64) error {
	rows, err := tx.QueryContext(ctx, `SELECT DISTINCT family_id FROM sys_refresh_token
		WHERE user_id=$1 AND revoked_at IS NULL ORDER BY family_id`, userID)
	if err != nil {
		return err
	}
	defer rows.Close()
	var families []string
	for rows.Next() {
		var family string
		if err := rows.Scan(&family); err != nil {
			return err
		}
		families = append(families, family)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	// Every account-wide logout takes locks in the same order. Update
	// after locking so the statement sees children committed by a pending rotation.
	for _, family := range families {
		if err := lockRefreshFamily(ctx, tx, family); err != nil {
			return err
		}
	}
	if len(families) == 0 {
		return nil
	}
	_, err = tx.ExecContext(ctx, `UPDATE sys_refresh_token SET revoked_at=$1, revoked_reason='LOGOUT'
		WHERE user_id=$2 AND family_id = ANY($3::text[]) AND revoked_at IS NULL`,
		s.Tokens.now().UTC(), userID, families)
	return err
}
