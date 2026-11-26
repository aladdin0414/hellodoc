// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.6.10';
export const BUILD_TIME = '2026/11/26 14:58:53';
export const GIT_COMMIT = 'fb76705';
export const RELEASE_NOTES: string[] = [
  "fix: 修复公开知识库匿名访问时因评论组件请求被 403 拦截而强制跳转登录页的问题，并将全局 403 无权限错误从强制登出跳登录逻辑中解耦。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
