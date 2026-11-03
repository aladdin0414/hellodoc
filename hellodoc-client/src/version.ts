// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.1.0';
export const BUILD_TIME = '2026/11/3 20:19:52';
export const GIT_COMMIT = '8463229';
export const RELEASE_NOTES: string[] = [
  "MCP 会话失效时自动重连，避免不确定的写入被重复执行",
  "支持向普通多级文件夹及知识库根目录创建文档，兼容既有事项接口",
  "新增知识库发现和分页目录浏览，支持跨知识库选择与权限过滤"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
