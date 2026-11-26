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
	dsn, err := url.Parse(strings.TrimPrefix(os.Getenv("DB_URL"), "jdbc:"))
	username, password := os.Getenv("DB_USERNAME"), os.Getenv("DB_PASSWORD")
	if err != nil || dsn.Scheme != "postgresql" || dsn.Hostname() == "" || len(dsn.Path) < 2 || username == "" || password == "" {
		return nil, errors.New("authentication requires a PostgreSQL DB_URL, DB_USERNAME and DB_PASSWORD")
	}
	dsn.User = url.UserPassword(username, password)
	db, err := sql.Open("pgx", dsn.String())
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
