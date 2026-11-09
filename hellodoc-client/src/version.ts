// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.6.6';
export const BUILD_TIME = '2026/11/9 16:06:29';
export const GIT_COMMIT = '8832c0e';
export const RELEASE_NOTES: string[] = [
  "fix: 修复中文加粗在标点边缘及末尾空格时的解析渲染，支持粘贴 HTML 中的 Markdown 标题和加粗内容转换为对应富文本。",
  "fix: 为 CI 构建的 Gradle 下载增加 SHA-256 校验和镜像备用源，保障依赖下载可靠性。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
