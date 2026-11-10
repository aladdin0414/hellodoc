// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.6.8';
export const BUILD_TIME = '2026/11/10 16:18:09';
export const GIT_COMMIT = 'dd4ed2b';
export const RELEASE_NOTES: string[] = [
  "fix: 修复 MCP Streamable HTTP 的 SSE 连接未及时返回响应头，导致 Antigravity 等客户端连接超时的问题。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
