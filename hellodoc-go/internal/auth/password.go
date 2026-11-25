package auth

import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"regexp"
	"strings"

	"golang.org/x/crypto/bcrypt"
)

type PasswordCodec struct {
	Encoder string
	Pepper  string
	Output  string
}

// Spring re-encodes the 16-byte salt when comparing hashes. Its last
// base64 digit must therefore have four zero padding bits.
var springBcrypt = regexp.MustCompile(`^\$2[aby]?\$[0-9]{2}\$[./0-9A-Za-z]{21}[.Oeu][./0-9A-Za-z]{31}$`)

func (c PasswordCodec) Encode(raw string) (string, error) {
	if strings.EqualFold(c.Encoder, "sha256") {
		digest := sha256.Sum256([]byte(c.Pepper + raw))
		if strings.EqualFold(c.Output, "base64") {
			return base64.StdEncoding.EncodeToString(digest[:]), nil
		}
		return hex.EncodeToString(digest[:]), nil
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(raw), bcrypt.DefaultCost)
	return string(hash), err
}

func (c PasswordCodec) Matches(raw, encoded string) bool {
	if strings.EqualFold(c.Encoder, "sha256") && !strings.HasPrefix(encoded, "$2a$") && !strings.HasPrefix(encoded, "$2b$") && !strings.HasPrefix(encoded, "$2y$") {
		want, _ := c.Encode(raw)
		return subtle.ConstantTimeCompare([]byte(want), []byte(encoded)) == 1
	}
	if !springBcrypt.MatchString(encoded) {
		return false
	}
	if len(raw) > 72 {
		raw = raw[:72]
	}
	if strings.HasPrefix(encoded, "$2$") {
		// Original revision cycles key bytes without a NUL terminator.
		// Fill all 72 bytes consumed by Blowfish before Go appends its NUL.
		if len(raw) == 0 {
			return false
		}
		raw = strings.Repeat(raw, (72+len(raw)-1)/len(raw))[:72]
	}
	return bcrypt.CompareHashAndPassword([]byte(encoded), []byte(raw)) == nil
}
