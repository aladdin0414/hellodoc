package auth

import (
	"bytes"
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
		case errors.Is(err, ErrTokenInvalid), errors.Is(err, ErrTokenExpired):
			code, zh, en = 1005, "refreshToken 无效", "Refresh token is invalid"
		case errors.Is(err, errRefreshType):
			code, zh, en = 1006, "令牌类型错误：需要 refresh token", "Invalid token type: refresh token required"
		case errors.Is(err, errRefreshMissing):
			code, zh, en = 1005, "refresh token 不存在", "Refresh token not found"
		case errors.Is(err, errRefreshFamily):
			code, zh, en = 1005, "refresh token family 不匹配", "Refresh token family mismatch"
		case errors.Is(err, errRefreshExpiry):
			code, zh, en = 1005, "refresh token 已过期", "Refresh token expired"
		case errors.Is(err, errRefreshAccount):
			code, zh, en = 1005, "用户不存在", "User not found"
		case errors.Is(err, errRefreshOwner):
			code, zh, en = 1005, "refresh token 用户不匹配", "Refresh token user mismatch"
		case errors.Is(err, errRefreshReuse):
			code, zh, en = 1005, "检测到 refresh token 被重复使用", "Refresh token reuse detected"
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

type refreshRequest struct {
	token string
}

func (r *refreshRequest) UnmarshalJSON(body []byte) error {
	dec := json.NewDecoder(bytes.NewReader(body))
	opening, err := dec.Token()
	if err != nil || opening != json.Delim('{') {
		return errRequest
	}
	// Jackson matches the field name exactly and binds each occurrence in order.
	for dec.More() {
		name, err := dec.Token()
		if err != nil {
			return err
		}
		var value json.RawMessage
		if err := dec.Decode(&value); err != nil {
			return err
		}
		if name != "refreshToken" {
			continue
		}
		switch value[0] {
		case '{', '[':
			return errRequest
		case 'n':
			r.token = ""
		case '"':
			if err := json.Unmarshal(value, &r.token); err != nil {
				return err
			}
		default:
			// Jackson coerces JSON numbers and booleans to strings.
			r.token = string(value)
		}
	}
	_, err = dec.Token()
	return err
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
	mux.HandleFunc("POST /api/auth/refresh-token", func(w http.ResponseWriter, r *http.Request) {
		req, err := readJSON[refreshRequest](w, r)
		if err != nil {
			writeResult(w, nil, err)
			return
		}
		data, err := s.Rotate(r.Context(), req.token)
		writeResult(w, data, err)
	})
}
