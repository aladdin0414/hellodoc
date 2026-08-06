// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '2.0.8';
export const BUILD_TIME = '2026/8/6 18:18:39';
export const GIT_COMMIT = '1217a72';
export const RELEASE_NOTES: string[] = [
  "refactor: simplify database initialization logic in DatabaseInitializer",
  "feat: add host gateway mapping and simplify database environment variable configuration",
  "feat: optimize deployment SSH/SCP performance with connection multiplexing and key authentication support",
  "chore: improve error handling, add build suggestions, and optimize deployment script in deploy.sh",
  "refactor: rename NAS configuration variables to generic DEPLOY variables and update deployment commands",
  "docs: update deployment instructions in README",
  "docs: update project description and setup instructions in README"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
