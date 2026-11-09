// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.6.4';
export const BUILD_TIME = '2026/11/9 14:14:05';
export const GIT_COMMIT = '1724d6d';
export const RELEASE_NOTES: string[] = [
  "feat: 将 AI 知识助理对话按用户和文档保存到数据库，重新打开时恢复，清空时同步清除历史。",
  "fix: 修复粘贴 HTML 时混合正文和图片、Base64 图片可能丢失，以及表格单元格换行或代码中的竖线导致保存后错行、错列的问题。",
  "fix: 加固外部图片转存的 DNS 地址校验，禁止自动跟随重定向；依赖重定向的图片会保留原始链接。",
  "fix: 修复目录右键菜单打开时再次右键弹出浏览器菜单，并避免移动端宽度模式覆盖桌面偏好。",
  "style: 将原生列表编号格式下拉选项设为左对齐。",
  "docs: 更新中英文项目介绍、Docker 部署说明和界面截图。",
  "chore: 升级前请备份数据库和数据目录；服务启动时会自动执行 AI 对话记录数据库迁移。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
