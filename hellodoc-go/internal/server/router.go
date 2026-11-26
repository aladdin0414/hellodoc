package server

import (
	"io"
	"mime"
	"net/http"
	"net/url"
	"os"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/nopkg/hellodoc/hellodoc-go/internal/auth"
	"github.com/nopkg/hellodoc/hellodoc-go/internal/systeminfo"
)

const corsMethods = "GET,POST,PUT,DELETE,OPTIONS"

// NewRouter creates the routes implemented by the Go migration so far.
func NewRouter(version string, now func() time.Time) http.Handler {
	return NewRouterWithAuth(version, now, nil)
}

func NewRouterWithAuth(version string, now func() time.Time, svc *auth.Service) http.Handler {
	info := systeminfo.New(version, now)
	origins, configured := os.LookupEnv("APP_CORS_ALLOWED_ORIGINS")
	if !configured {
		origins = "*"
	}
	mux := http.NewServeMux()
	var content http.Handler = http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		if r.Method == http.MethodOptions {
			h.Set("Allow", "GET,HEAD,OPTIONS")
			h.Set("Accept-Patch", "")
			return
		}
		if !acceptsJSON(strings.Join(r.Header.Values("Accept"), ",")) {
			h.Set("Accept", "application/json, application/*+json, application/yaml")
			w.WriteHeader(http.StatusNotAcceptable)
			return
		}
		h.Set("Content-Type", "application/json")
		switch r.Method {
		case http.MethodGet:
			info.ServeHTTP(w, r)
		case http.MethodPost:
			io.WriteString(w, `{"code":9999,"message":"系统异常，请稍后再试","data":null}`)
		}
	})
	if svc != nil {
		content = svc.Bearer(content)
	}
	serveInfo := func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("X-XSS-Protection", "0")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Vary", "Origin, Access-Control-Request-Method, Access-Control-Request-Headers")
		if handleCORS(w, r, origins) {
			return
		}
		h.Set("Content-Language", "zh-CN")
		// Standalone mode has no verifier and rejects supplied credentials.
		if svc == nil && len(r.Header.Values("Authorization")) > 0 {
			h.Set("WWW-Authenticate", `Bearer error="invalid_token", error_description="invalid_token"`)
			h.Set("Content-Type", "application/json;charset=UTF-8")
			w.WriteHeader(http.StatusUnauthorized)
			if r.Method != http.MethodHead {
				io.WriteString(w, `{"error":"invalid_token","error_description":"refreshToken 无效","needRefresh":false}`)
			}
			return
		}
		content.ServeHTTP(w, r)
	}
	for _, method := range []string{http.MethodGet, http.MethodOptions, http.MethodPost} {
		mux.HandleFunc(method+" /api/system/info", serveInfo)
	}
	return mux
}

func acceptsJSON(accept string) bool {
	if accept == "" {
		return true
	}
	for _, item := range strings.Split(accept, ",") {
		mediaType, params, err := mime.ParseMediaType(item)
		if err != nil {
			continue
		}
		if raw, ok := params["q"]; ok {
			q, err := strconv.ParseFloat(raw, 64)
			if err != nil || q <= 0 || q > 1 {
				continue
			}
		}
		if mediaType == "application/json" || mediaType == "*/*" || mediaType == "application/*" {
			return true
		}
	}
	return false
}

// sameOrigin compares the HTTP origin tuple, including the effective default port.
func sameOrigin(origin string, r *http.Request) bool {
	source, err := url.Parse(origin)
	if err != nil || source.Host == "" || source.User != nil || source.Path != "" || source.RawQuery != "" || source.Fragment != "" {
		return false
	}
	scheme, defaultPort := "http", "80"
	if r.TLS != nil {
		scheme, defaultPort = "https", "443"
	}
	target := &url.URL{Host: r.Host}
	if source.Scheme != scheme || !strings.EqualFold(source.Hostname(), target.Hostname()) {
		return false
	}
	sourcePort, targetPort := source.Port(), target.Port()
	if sourcePort == "" {
		sourcePort = defaultPort
	}
	if targetPort == "" {
		targetPort = defaultPort
	}
	return sourcePort == targetPort
}

func handleCORS(w http.ResponseWriter, r *http.Request, origins string) bool {
	origin := r.Header.Get("Origin")
	if origin == "" || sameOrigin(origin, r) {
		return false
	}
	preflight := r.Method == http.MethodOptions && r.Header.Get("Access-Control-Request-Method") != ""
	method := r.Method
	if preflight {
		method = r.Header.Get("Access-Control-Request-Method")
	}
	if (origins != "*" && !slices.Contains(strings.Split(origins, ","), origin)) || !slices.Contains(strings.Split(corsMethods, ","), method) {
		// An explicit nil suppresses net/http's automatic content-type sniffing.
		w.Header()["Content-Type"] = nil
		w.WriteHeader(http.StatusForbidden)
		io.WriteString(w, "Invalid CORS request")
		return true
	}
	if origins == "*" {
		w.Header().Set("Access-Control-Allow-Origin", "*")
	} else {
		w.Header().Set("Access-Control-Allow-Origin", origin)
		w.Header().Set("Access-Control-Allow-Credentials", "true")
	}
	if preflight {
		w.Header().Set("Access-Control-Allow-Methods", corsMethods)
		if headers := r.Header.Get("Access-Control-Request-Headers"); headers != "" {
			w.Header().Set("Access-Control-Allow-Headers", headers)
		}
	}
	return preflight
}
