# Changelog

All notable changes to HelloDoc will be documented in this file.

## [v2.0.7] - 2026-08-06

- fix: resolve connection leakage by closing database initializer resources properly
- feat: support BCrypt password hashing for backward compatibility and add project development scripts
- refactor: simplify database initialization by removing manual schema migrations and consolidating logic to schema.sql execution
- feat: update database configuration to support environment-specific variables and remove unused schema example file
- refactor: remove client-side Dockerfile and consolidate environment configuration at the project root
- chore: update environment variable documentation in .env.example and README.md
- feat: add support for dynamic edit mode and auto-focus parameters when navigating to document views

