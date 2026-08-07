# Changelog

## [v2.0.9] - 2026-08-07

- feat(mobile): 优化移动端知识库页面交互与缓存体验

## [v2.0.8] - 2026-08-06

- refactor: simplify database initialization logic in DatabaseInitializer
- feat: add host gateway mapping and simplify database environment variable configuration
- feat: optimize deployment SSH/SCP performance with connection multiplexing and key authentication support
- chore: improve error handling, add build suggestions, and optimize deployment script in deploy.sh
- refactor: rename NAS configuration variables to generic DEPLOY variables and update deployment commands
- docs: update deployment instructions in README
- docs: update project description and setup instructions in README

All notable changes to HelloDoc will be documented in this file.

## [v2.0.7] - 2026-08-06

- fix: resolve connection leakage by closing database initializer resources properly
- feat: support BCrypt password hashing for backward compatibility and add project development scripts
- refactor: simplify database initialization by removing manual schema migrations and consolidating logic to schema.sql execution
- feat: update database configuration to support environment-specific variables and remove unused schema example file
- refactor: remove client-side Dockerfile and consolidate environment configuration at the project root
- chore: update environment variable documentation in .env.example and README.md
- feat: add support for dynamic edit mode and auto-focus parameters when navigating to document views

