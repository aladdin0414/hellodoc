package auth

import (
	"encoding/json"
	"testing"
)

func TestLoginContract(t *testing.T) {
	b, _ := json.Marshal(AuthResponse{TokenType: "Bearer"})
	if string(b) != `{"accessToken":"","refreshToken":"","tokenType":"Bearer","nickname":null,"avatar":null,"isInitialPwd":false}` {
		t.Fatalf("unexpected response %s", b)
	}
}
