package main

import (
	"context"
	"database/sql"
	"errors"
	"log"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/nopkg/hellodoc/hellodoc-go/internal/auth"
	"github.com/nopkg/hellodoc/hellodoc-go/internal/server"
)

// version can be set at build time with -ldflags '-X main.version=...'.
var version = "2.0.0"

// Database settings may use the aliases declared in application-prod.properties.
func databaseSetting(name string) string {
	if value, ok := os.LookupEnv("DB_" + name); ok {
		return value
	}
	return os.Getenv("SPRING_DATASOURCE_" + name)
}

func databaseDSN() (string, error) {
	raw, jdbc := strings.CutPrefix(databaseSetting("URL"), "jdbc:")
	dsn, err := url.Parse(raw)
	username, password := databaseSetting("USERNAME"), databaseSetting("PASSWORD")
	if err != nil || dsn.Scheme != "postgresql" || dsn.Hostname() == "" || len(dsn.Path) < 2 || username == "" || password == "" {
		return "", errors.New("authentication requires a PostgreSQL DB_URL, DB_USERNAME and DB_PASSWORD")
	}
	// pgx applies aliases and duplicates in URL order, including ssl / sslmode.
	// Keep each entry in place when translating the JDBC property spelling.
	var pairs []string
	var ssl, sslMode string
	var hasSSL, hasMode bool
	for pair := range strings.SplitSeq(dsn.RawQuery, "&") {
		if pair == "" && jdbc {
			continue
		}
		key, value, found := strings.Cut(pair, "=")
		name, err := url.PathUnescape(key)
		// JDBC leaves property names literal, but pgx decodes them. Reject
		// ambiguous encodings that could introduce a different connection option.
		if err != nil || (jdbc && name != key) {
			return "", errors.New("invalid authentication database URL parameters")
		}
		if name == "currentSchema" {
			key = "search_path"
		}
		if jdbc {
			// JDBC form-decodes values. libpq uses percent decoding and reserves raw '='.
			value, err = url.QueryUnescape(value)
			if err != nil {
				return "", errors.New("invalid authentication database URL parameters")
			}
			switch name {
			case "ssl":
				ssl, hasSSL = value, true
				continue
			case "sslmode":
				sslMode, hasMode = strings.ToLower(value), true
				continue
			}
			value = strings.ReplaceAll(url.QueryEscape(value), "+", "%20")
			found = true
		}
		if found {
			pair = key + "=" + value
		}
		pairs = append(pairs, pair)
	}
	// pgjdbc SslMode.of gives an explicit sslmode priority over ssl in either
	// order. Its ssl=true (and bare ssl) requires hostname and certificate checks.
	if hasMode {
		switch sslMode {
		case "disable", "allow", "prefer", "require", "verify-ca", "verify-full":
		default:
			return "", errors.New("invalid authentication database sslmode")
		}
	} else if hasSSL {
		sslMode = "prefer"
		if ssl == "" || strings.EqualFold(ssl, "true") {
			sslMode = "verify-full"
		}
	}
	if hasSSL || hasMode {
		pairs = append(pairs, "sslmode="+sslMode)
	}
	dsn.RawQuery = strings.Join(pairs, "&")
	dsn.User = url.UserPassword(username, password)
	return dsn.String(), nil
}

func configuredAuth() (*auth.Service, error) {
	raw, configured := os.LookupEnv("HELLODOC_GO_AUTH_ENABLED")
	if !configured {
		return nil, nil
	}
	enabled, err := strconv.ParseBool(raw)
	if err != nil {
		return nil, errors.New("invalid HELLODOC_GO_AUTH_ENABLED")
	}
	if !enabled {
		return nil, nil
	}
	tokens, err := auth.NewTokensFromEnv(time.Now)
	if err != nil {
		return nil, err
	}
	dsn, err := databaseDSN()
	if err != nil {
		return nil, err
	}
	db, err := sql.Open("pgx", dsn)
	if err != nil {
		return nil, errors.New("invalid authentication database configuration")
	}
	store := &auth.Store{DB: db}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	// Exercise the account query so missing schema or privileges fail before serving.
	_, err = store.Lookup(ctx, "")
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		db.Close()
		return nil, errors.New("authentication database or account schema unavailable")
	}
	return &auth.Service{Store: store, Tokens: tokens, Passwords: auth.PasswordCodec{
		Encoder: os.Getenv("AUTH_PASSWORD_ENCODER"), Pepper: os.Getenv("AUTH_PASSWORD_PEPPER"), Output: os.Getenv("AUTH_PASSWORD_SHA256_OUTPUT"),
	}}, nil
}

func main() {
	addr := os.Getenv("HELLODOC_GO_ADDR")
	if addr == "" {
		addr = "127.0.0.1:18081"
	}
	service, err := configuredAuth()
	if err != nil {
		log.Fatal(err)
	}
	log.Printf("starting incomplete Go migration service on %s", addr)
	srv := &http.Server{Addr: addr, Handler: server.NewRouterWithAuth(version, time.Now, service), ReadHeaderTimeout: 5 * time.Second}
	log.Fatal(srv.ListenAndServe())
}
