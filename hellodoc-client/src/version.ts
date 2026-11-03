// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.0.1';
export const BUILD_TIME = '2026/11/3 20:07:26';
export const GIT_COMMIT = '4c31f48';
export const RELEASE_NOTES: string[] = [
  "修复发布脚本误提交无关文件和掩盖推送失败的问题",
  "端到端测试改用隔离服务，验证版本冲突和文档修订流程"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
