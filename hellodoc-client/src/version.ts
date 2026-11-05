// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.6.0';
export const BUILD_TIME = '2026/11/05 15:51:50';
export const GIT_COMMIT = '28aea69';
export const RELEASE_NOTES: string[] = [
  "feat: 新增知识库所有权转让功能，支持所有者安全交接管理权并更新成员身份。",
  "feat: 新增知识库访客留言开关配置，配套数据库 V6 迁移脚本，支持按库启用或禁用留言板。",
  "feat: 新增知识库设置中的 MCP Token 快捷管理入口，优化 Token 交互体验。",
  "fix: 强化知识库及私有文档的成员鉴权逻辑，修复未发布文档在特定公开场景下的越权风险。",
  "docs: 补充全面的 API 接口文档，更新项目说明与 MCP 使用指南。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
