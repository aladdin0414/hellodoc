// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '3.6.7';
export const BUILD_TIME = '2026/11/10 15:37:56';
export const GIT_COMMIT = 'ffda91e';
export const RELEASE_NOTES: string[] = [
  "fix: 修复嵌套有序列表切换编号格式后标识未正确更新的问题。",
  "fix: 粘贴 KaTeX 公式时保留可编辑的公式，避免箭头及 LaTeX 源码重复显示。",
  "fix: 粘贴含图片的网页内容时保留代码块结构、换行和缩进，避免代码中的 Markdown 被误解析；保存包含反引号围栏的代码块后可完整重新打开。",
  "fix: 改善字符图代码块的换行与显示，并避免未指定语言的代码块被自动错误着色。"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
