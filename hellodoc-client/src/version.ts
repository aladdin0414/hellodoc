// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '2.1.0';
export const BUILD_TIME = '2026/9/8 11:18:35';
export const GIT_COMMIT = 'ba167fe';
export const RELEASE_NOTES: string[] = [
  "style: 统一 AI 大模型配置页面的头部与新增模型按钮样式",
  "fix: 修复系统设置初始化重复创建导致的 key_exists 报错与列表变空问题",
  "refactor: 从系统设置中完全移除已弃用的大模型系统配置",
  "style: 为编辑器中的块级数学公式添加与对话抽屉一致的背景块样式",
  "fix: 改进 LaTeX 公式渲染为靶向自愈模式，杜绝 \\left 等合法宏被误伤",
  "fix: 修复大模型流式传输空格丢失与 LaTeX 公式宏命令粘连报红问题",
  "fix: 修复LaTeX定界符被Markdown转义导致中括号与小括号原样暴露的问题",
  "fix: 修复流式传输误将换行符和空格丢弃导致的Markdown严重排版错乱问题",
  "fix: 优化模型连通测试，抑制推理模型深度思考并将探测超时放宽至30秒",
  "fix: 修复插入文档至富文本编辑器时标题与列表项语法未规范化解析问题",
  "fix: 重构Markdown数学公式渲染占位机制，杜绝下划线加粗歧义与占位符泄漏",
  "fix: 优化AI对话Markdown渲染，支持KaTeX数学公式与修复列表加粗语法",
  "feat: AI知识助理对话支持Markdown语法渲染与排版优化",
  "refactor: 统一在 DatabaseInitializer 中管理 sys_ai_model 数据表自动创建与初始化迁移",
  "feat: 支持启动时自动检测并创建 sys_ai_model 数据表与索引",
  "feat: 实现AI模型后台独立管理模块与多模型切换",
  "chore: update example environment variables configuration",
  "style: normalize product name to Hellodoc across codebase and documentation",
  "docs: localize documentation by splitting README into English and Chinese files and updating project language to English",
  "chore: update dev script environment configuration"
];

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
