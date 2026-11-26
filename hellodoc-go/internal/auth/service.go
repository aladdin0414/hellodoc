package auth

import "sync"

type passwordEncoder interface {
	Encode(string) (string, error)
	Matches(string, string) bool
}

type Service struct {
	Store     *Store
	Tokens    *Tokens
	Passwords passwordEncoder

	dummyOnce sync.Once
	dummyHash string
	dummyErr  error
}

func (s *Service) passwordEncoder() passwordEncoder {
	if s.Passwords == nil {
		return PasswordCodec{}
	}
	return s.Passwords
}
