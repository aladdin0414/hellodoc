// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.0.0';
export const BUILD_TIME = '2026/11/3 20:00:43';
export const GIT_COMMIT = 'a339242';
export const RELEASE_NOTES: string[] = [
  "MCP 支持跨知识库检索和安全更新文档，优化检索性能与结果准确性",
  "正文保存需要版本号；旧客户端和第三方集成需升级以避免覆盖新内容",
  "桌面及移动端在版本冲突时保留本地草稿，完善 Markdown 文档保存指引"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
