package auth

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/golang-jwt/jwt/v5"
)

var (
	ErrTokenInvalid = errors.New("invalid JWT")
	ErrTokenExpired = errors.New("expired JWT")

	uuidText = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)
)

const refreshTokenType = "refresh"

type Claims struct {
	jwt.RegisteredClaims
	Roles  []string `json:"roles,omitempty"`
	Type   string   `json:"type,omitempty"`
	Family string   `json:"family,omitempty"`
}

// UnmarshalJSON matches JWT claim names exactly, as required for JSON object
// members. Decoding directly into a Go struct also matches case variants.
func (c *Claims) UnmarshalJSON(data []byte) error {
	var values map[string]json.RawMessage
	if err := json.Unmarshal(data, &values); err != nil {
		return err
	}
	var claims Claims
	for _, claim := range []struct {
		name string
		dest any
	}{
		{"iss", &claims.Issuer},
		{"sub", &claims.Subject},
		{"aud", &claims.Audience},
		{"exp", &claims.ExpiresAt},
		{"nbf", &claims.NotBefore},
		{"iat", &claims.IssuedAt},
		{"jti", &claims.ID},
		{"roles", &claims.Roles},
		{"type", &claims.Type},
		{"family", &claims.Family},
	} {
		if value, ok := values[claim.name]; ok {
			if err := json.Unmarshal(value, claim.dest); err != nil {
				return err
			}
		}
	}
	*c = claims
	return nil
}

type Tokens struct {
	secret     []byte
	accessTTL  time.Duration
	refreshTTL time.Duration
	now        func() time.Time
	parser     *jwt.Parser
}

func NewTokens(secret []byte, accessTTL, refreshTTL time.Duration, now func() time.Time) (*Tokens, error) {
	if len(secret) < 64 || !utf8.Valid(secret) || len(bytes.TrimSpace(secret)) == 0 {
		return nil, errors.New("JWT_SECRET must be non-blank UTF-8 with at least 64 bytes")
	}
	if accessTTL <= 0 || refreshTTL <= 0 {
		return nil, errors.New("JWT expiration intervals must be positive")
	}
	if now == nil {
		now = time.Now
	}
	return &Tokens{
		secret:     bytes.Clone(secret),
		accessTTL:  accessTTL,
		refreshTTL: refreshTTL,
		now:        now,
		parser: jwt.NewParser(
			jwt.WithValidMethods([]string{jwt.SigningMethodHS512.Alg()}),
			jwt.WithStrictDecoding(),
			jwt.WithoutClaimsValidation(),
		),
	}, nil
}

// NewTokensFromEnv loads the Java configuration keys with the same TTL defaults.
func NewTokensFromEnv(now func() time.Time) (*Tokens, error) {
	accessTTL, err := jwtTTLFromEnv("JWT_EXPIRATION_MS", 24*time.Hour)
	if err != nil {
		return nil, err
	}
	refreshTTL, err := jwtTTLFromEnv("JWT_REFRESH_EXPIRATION_MS", 7*24*time.Hour)
	if err != nil {
		return nil, err
	}
	return NewTokens([]byte(os.Getenv("JWT_SECRET")), accessTTL, refreshTTL, now)
}

func jwtTTLFromEnv(name string, fallback time.Duration) (time.Duration, error) {
	raw, ok := os.LookupEnv(name)
	if !ok {
		return fallback, nil
	}
	ms, err := strconv.ParseInt(strings.TrimSpace(raw), 10, 64)
	if err != nil || ms <= 0 || ms > math.MaxInt64/int64(time.Millisecond) {
		return 0, fmt.Errorf("%s must be a positive integer in milliseconds within the supported duration range", name)
	}
	return time.Duration(ms) * time.Millisecond, nil
}

func (t *Tokens) Access(subject string, roles []string) (string, error) {
	if strings.TrimSpace(subject) == "" {
		return "", ErrTokenInvalid
	}
	if roles == nil {
		roles = []string{}
	}
	claims := struct {
		jwt.RegisteredClaims
		Roles []string `json:"roles"`
	}{t.registeredClaims(subject, t.accessTTL), roles}
	return jwt.NewWithClaims(jwt.SigningMethodHS512, claims).SignedString(t.secret)
}

func (t *Tokens) Refresh(subject, jti, family string) (string, error) {
	if strings.TrimSpace(subject) == "" || !uuidText.MatchString(jti) || !uuidText.MatchString(family) {
		return "", ErrTokenInvalid
	}
	claims := Claims{RegisteredClaims: t.registeredClaims(subject, t.refreshTTL), Type: refreshTokenType, Family: family}
	claims.ID = jti
	return jwt.NewWithClaims(jwt.SigningMethodHS512, claims).SignedString(t.secret)
}

func (t *Tokens) registeredClaims(subject string, ttl time.Duration) jwt.RegisteredClaims {
	now := t.now().Truncate(time.Millisecond)
	return jwt.RegisteredClaims{
		Subject:   subject,
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(ttl)),
	}
}

// Verify returns claims for valid tokens and for authenticated expired tokens.
// Claims returned with ErrTokenExpired must never grant access or rotate a session.
func (t *Tokens) Verify(raw string) (Claims, error) {
	var claims Claims
	token, err := t.parser.ParseWithClaims(raw, &claims, func(*jwt.Token) (any, error) {
		return t.secret, nil
	})
	if err != nil || claims.ExpiresAt == nil || strings.TrimSpace(claims.Subject) == "" {
		return Claims{}, ErrTokenInvalid
	}
	// HelloDoc does not support any critical JWS header extensions.
	if crit := token.Header["crit"]; crit != nil {
		names, ok := crit.([]any)
		if !ok || len(names) != 0 {
			return Claims{}, ErrTokenInvalid
		}
	}
	switch claims.Type {
	case "":
	case refreshTokenType:
		if !uuidText.MatchString(claims.ID) || !uuidText.MatchString(claims.Family) {
			return Claims{}, ErrTokenInvalid
		}
	default:
		return Claims{}, ErrTokenInvalid
	}

	// JJWT 0.12.6 uses a millisecond clock and expires only after exp.
	now := t.now().Truncate(time.Millisecond)
	if claims.NotBefore != nil && now.Before(claims.NotBefore.Time) {
		return Claims{}, ErrTokenInvalid
	}
	if now.After(claims.ExpiresAt.Time) {
		return claims, ErrTokenExpired
	}
	return claims, nil
}
