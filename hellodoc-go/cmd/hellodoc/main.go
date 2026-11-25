package main

import (
	"log"
	"net/http"
	"os"
	"time"

	"github.com/nopkg/hellodoc/hellodoc-go/internal/server"
)

// version can be set at build time with -ldflags '-X main.version=...'.
var version = "2.0.0"

func main() {
	addr := os.Getenv("HELLODOC_GO_ADDR")
	if addr == "" {
		addr = "127.0.0.1:18081"
	}
	log.Printf("starting incomplete Go migration service on %s", addr)
	srv := &http.Server{Addr: addr, Handler: server.NewRouter(version, time.Now), ReadHeaderTimeout: 5 * time.Second}
	log.Fatal(srv.ListenAndServe())
}
