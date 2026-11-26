package auth

import (
	"context"
	"database/sql"
)

// Session writes lock the account before locking any family or refresh row.
// The account key uses both halves of its ID in PostgreSQL's two-integer
// advisory namespace, which is separate from the family's bigint namespace.
func lockRefreshAccount(ctx context.Context, tx *sql.Tx, userID int64, exclusive bool) error {
	query := `SELECT pg_advisory_xact_lock_shared($1, $2)`
	if exclusive {
		query = `SELECT pg_advisory_xact_lock($1, $2)`
	}
	_, err := tx.ExecContext(ctx, query, int32(userID>>32), int32(userID))
	return err
}

func lockRefreshFamily(ctx context.Context, tx *sql.Tx, family string) error {
	_, err := tx.ExecContext(ctx, `SELECT pg_advisory_xact_lock(hashtextextended('hellodoc/auth/refresh/' || $1, 0))`, family)
	return err
}
