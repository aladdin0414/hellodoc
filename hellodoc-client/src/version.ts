// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.3.0';
export const BUILD_TIME = '2026/11/04 08:37:46';
export const GIT_COMMIT = 'a539c51';
export const RELEASE_NOTES: string[] = [
  "支持 MCP 客户端通过 Streamable HTTP 直连，无需本地桥接或固定知识库 ID",
  "新增可撤销的 MCP 专用访问令牌和个人资料管理入口",
  "支持配置公开服务地址，返回完整文档来源链接"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
