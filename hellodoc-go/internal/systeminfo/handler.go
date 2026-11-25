package systeminfo

import (
	"encoding/json"
	"net/http"
	"time"
)

// New returns the system information response using the supplied version and clock.
func New(version string, now func() time.Time) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{
			"name":       "HelloDoc Server",
			"version":    version,
			"status":     "UP",
			"serverTime": now().Format("2006-01-02 15:04:05"),
		})
	})
}
