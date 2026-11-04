// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.4.0';
export const BUILD_TIME = '2026/11/4 15:37:51';
export const GIT_COMMIT = '9a2edc7';
export const RELEASE_NOTES: string[] = [
  "新增 MCP 同目录排序，支持根目录及任意层级文件夹中的文档与文件夹混合排序。",
  "新增目录快照、权限校验与冲突检测，避免并发修改被覆盖。",
  "网页同目录拖拽和上移、下移改为原子批量排序，统一网页与 MCP 的显示顺序。",
  "包含 V5 数据库迁移；升级后需重启服务和 MCP Bridge。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
