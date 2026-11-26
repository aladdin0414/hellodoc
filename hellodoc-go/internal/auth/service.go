package auth

type Service struct {
	Store     *Store
	Tokens    *Tokens
	Passwords PasswordCodec
}
