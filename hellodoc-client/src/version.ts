// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.5.3';
export const BUILD_TIME = '2026/11/05 15:30:58';
export const GIT_COMMIT = 'b5b3f16';
export const RELEASE_NOTES: string[] = [
  "fix: 修复文档协作 WebSocket 连接及权限校验，恢复多人会话的连接与锁状态同步。",
  "fix: 串行化文档抢锁，并在保存、MCP 写入及版本恢复时校验有效编辑锁，防止其他会话绕过持锁者写入。",
  "fix: 修复协作同步失败和断线重连处理；编辑器在失去编辑权时保持只读并保留本地草稿。",
  "chore: 自定义客户端在持锁编辑时，更新正文或创建版本需提交 `collabSessionId`，恢复版本时需提供同名参数；部署时请同时升级前后端。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
