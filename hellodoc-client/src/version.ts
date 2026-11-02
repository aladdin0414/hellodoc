// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '2.2.0';
export const BUILD_TIME = '2026/11/2 22:48:50';
export const GIT_COMMIT = 'c3aa26c';
export const RELEASE_NOTES: string[] = [
  "新增 MCP 接口，支持 AI 按事项归档 Markdown 文档。",
  "新增本地 MCP 桥接程序，复用现有账号并支持知识库范围配置。",
  "支持并发请求防重、超时结果查询与现有知识库权限校验。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
