package auth

import (
	"encoding/json"
	"errors"
	"io"
	"mime"
	"net/http"
	"strings"
)

var errRequest = errors.New("invalid request")

const maxAuthBodyBytes = 64 << 10

func readJSON[T any](w http.ResponseWriter, r *http.Request) (*T, error) {
	media, _, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil || !(media == "application/json" || strings.HasPrefix(media, "application/") && strings.HasSuffix(media, "+json")) {
		return nil, errRequest
	}
	var req *T
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxAuthBodyBytes))
	if dec.Decode(&req) != nil || req == nil || dec.Decode(new(any)) != io.EOF {
		return nil, errRequest
	}
	return req, nil
}
func writeResult(w http.ResponseWriter, data any, err error) {
	code, zh, en := 0, "成功", "Success"
	if err != nil {
		data = nil
		switch {
		case errors.Is(err, errCredentials):
			code, zh, en = 1001, "用户名或密码错误", "Incorrect username or password"
		case errors.Is(err, errConflict):
			code, zh, en = 1003, "用户名已存在", "Username already exists"
		case errors.Is(err, errDisabled):
			code, zh, en = 1002, "账号已被禁用", "Account is disabled"
		default:
			code, zh, en = 9999, "系统异常，请稍后再试", "System error occurred, please try again later"
		}
	}
	message := zh
	if w.Header().Get("Content-Language") == "en-US" {
		message = en
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(struct {
		Code    int    `json:"code"`
		Message string `json:"message"`
		Data    any    `json:"data"`
	}{code, message, data})
}
func (s *Service) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("POST /api/auth/login", func(w http.ResponseWriter, r *http.Request) {
		req, err := readJSON[struct{ Username, Password *string }](w, r)
		if err != nil {
			writeResult(w, nil, err)
			return
		}
		if req.Username == nil {
			writeResult(w, nil, errCredentials)
			return
		}
		data, err := s.login(r.Context(), *req.Username, req.Password)
		writeResult(w, data, err)
	})
	mux.HandleFunc("POST /api/auth/register", func(w http.ResponseWriter, r *http.Request) {
		req, err := readJSON[struct {
			RegisterRequest
			Username *string `json:"username"`
			Password *string `json:"password"`
		}](w, r)
		if err != nil || req.Username == nil {
			writeResult(w, nil, errRequest)
			return
		}
		req.RegisterRequest.Username = *req.Username
		data, err := s.register(r.Context(), req.RegisterRequest, req.Password)
		writeResult(w, data, err)
	})
}
