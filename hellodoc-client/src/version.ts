// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '2.0.7';
export const BUILD_TIME = '2026/8/6 17:12:06';
export const GIT_COMMIT = '63ad8b8';
export const RELEASE_NOTES: string[] = [
  "fix: resolve connection leakage by closing database initializer resources properly",
  "feat: support BCrypt password hashing for backward compatibility and add project development scripts",
  "refactor: simplify database initialization by removing manual schema migrations and consolidating logic to schema.sql execution",
  "feat: update database configuration to support environment-specific variables and remove unused schema example file",
  "refactor: remove client-side Dockerfile and consolidate environment configuration at the project root",
  "chore: update environment variable documentation in .env.example and README.md",
  "feat: add support for dynamic edit mode and auto-focus parameters when navigating to document views"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
