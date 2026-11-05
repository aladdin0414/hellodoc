// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.5.1';
export const BUILD_TIME = '2026/11/05 08:32:25';
export const GIT_COMMIT = '8916f15';
export const RELEASE_NOTES: string[] = [
  "fix: 修复 PostgreSQL 空数据库首次安装。",
  "fix: 修复 Docker 首页与 API 路径。",
  "fix: 持久化上传文件并加固部署默认配置。",
  "docs: 完善部署、备份恢复说明与发布检查。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
