// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.6.3';
export const BUILD_TIME = '2026/11/6 15:14:26';
export const GIT_COMMIT = 'cfcb8e5';
export const RELEASE_NOTES: string[] = [
  "style: 统一编辑页与发布页的知识库侧栏、目录层级轨道、状态标记及顶部导航，优化笔记编辑界面的视觉层级。",
  "style: 统一桌面和移动端弹窗的遮罩、圆角及操作样式，优化知识库设置、切换器和右键菜单。",
  "fix: 修复右键菜单在窗口边缘被裁切，以及目录自定义颜色遮挡图标内部线条的问题。",
  "fix: 修复批量删除、复制和创建副本时父子项目重复处理及部分失败反馈不清的问题，并保留失败操作的重试能力。",
  "fix: 复制或创建文件夹副本时逐项校验子项权限；跨知识库复制至公开知识库时提示副本保留发布状态。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
