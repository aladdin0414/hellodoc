// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.5.0';
export const BUILD_TIME = '2026/11/4 21:36:56';
export const GIT_COMMIT = '8916f15';
export const RELEASE_NOTES: string[] = [
  "新增 MCP 创建文件夹工具，支持知识库根目录及嵌套目录，使用 requestId 安全重试。",
  "新增 MCP 移动文件夹工具，支持完整子树移动、权限校验、位置冲突和循环目录防护。",
  "更新 MCP Bridge 和使用文档；升级后需重启服务及 MCP Bridge。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
