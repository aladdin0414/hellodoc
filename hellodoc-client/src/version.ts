// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.5.2';
export const BUILD_TIME = '2026/11/05 09:11:47';
export const GIT_COMMIT = 'c2709cb';
export const RELEASE_NOTES: string[] = [
  "fix: 完善 AI 助手、模型配置、错误提示和移动端创建知识库的中英文显示，修复缺失词条。",
  "fix: 修复移动端语言偏好保存与跨端同步，并使页面标题和日期格式跟随语言切换。",
  "fix: 修复服务端消息、通知和 AI 流式错误的语言选择，补齐文档版本冲突等提示。",
  "chore: 增加前后端国际化词条、语言切换和错误处理检查，并将客户端国际化检查加入镜像发布流程。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
