package systeminfo

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestSystemInfoResponse(t *testing.T) {
	now := time.Date(2026, 11, 25, 10, 15, 20, 0, time.Local)
	handler := New("3.6.9", func() time.Time { return now })
	req := httptest.NewRequest(http.MethodGet, "/api/system/info", nil)
	res := httptest.NewRecorder()
	handler.ServeHTTP(res, req)
	if res.Code != http.StatusOK {
		t.Fatalf("status = %d", res.Code)
	}
	var got map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &got); err != nil {
		t.Fatal(err)
	}
	want := map[string]string{"name": "HelloDoc Server", "version": "3.6.9", "status": "UP", "serverTime": "2026-11-25 10:15:20"}
	if len(got) != len(want) {
		t.Fatalf("response fields: %v", got)
	}
	for key, value := range want {
		if got[key] != value {
			t.Errorf("%s = %v; want %s", key, got[key], value)
		}
	}
}
