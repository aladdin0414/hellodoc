// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.6.1';
export const BUILD_TIME = '2026/11/5 21:26:24';
export const GIT_COMMIT = '406e45b';
export const RELEASE_NOTES: string[] = [
  "fix: 修复管理员转移知识库所有权时弹窗被遮挡的问题。",
  "fix: 改进通用弹窗的滚动、键盘焦点和 Escape 关闭行为，修复小屏幕下内容无法访问的问题。",
  "fix: 统一链接输入、MCP 令牌撤销和冲突草稿处理的对话框，并支持在修改密码时按 Enter 提交。",
  "fix: 补齐共享知识库列表视图的管理入口，改善键盘和触屏操作，并修复系统设置在窄屏下被裁切的问题。",
  "fix: 改进移动端目录操作面板和键盘导航，防止重复创建文档或目录，并允许页面缩放。",
  "fix: 修复编辑器 AI 提示词输入框的换行及输入法确认行为。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
