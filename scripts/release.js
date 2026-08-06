const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { execSync } = require('child_process');

// 项目根目录
const ROOT_DIR = path.resolve(__dirname, '..');

// 子工程组件路径
const CLIENT_PKG_PATH = path.join(ROOT_DIR, 'hellodoc-client/package.json');
const DESKTOP_PKG_PATH = path.join(ROOT_DIR, 'hellodoc-desktop/package.json');
const SERVER_GRADLE_PATH = path.join(ROOT_DIR, 'hellodoc-server/build.gradle');
const CLIENT_VERSION_TS_PATH = path.join(ROOT_DIR, 'hellodoc-client/src/version.ts');
const ROOT_PKG_PATH = path.join(ROOT_DIR, 'package.json');

// 格式化输出颜色
const colors = {
  green: (str) => `\x1b[32m${str}\x1b[0m`,
  yellow: (str) => `\x1b[33m${str}\x1b[0m`,
  red: (str) => `\x1b[31m${str}\x1b[0m`,
  cyan: (str) => `\x1b[36m${str}\x1b[0m`,
  bold: (str) => `\x1b[1m${str}\x1b[0m`,
};

// 获取当前版本号
function getCurrentVersions() {
  let clientVersion = '2.0.0';
  let desktopVersion = '2.0.0';
  let serverVersion = '2.0.0';

  if (fs.existsSync(CLIENT_PKG_PATH)) {
    const pkg = JSON.parse(fs.readFileSync(CLIENT_PKG_PATH, 'utf-8'));
    clientVersion = pkg.version || '2.0.0';
  }

  if (fs.existsSync(DESKTOP_PKG_PATH)) {
    const pkg = JSON.parse(fs.readFileSync(DESKTOP_PKG_PATH, 'utf-8'));
    desktopVersion = pkg.version || '2.0.0';
  }

  if (fs.existsSync(SERVER_GRADLE_PATH)) {
    const content = fs.readFileSync(SERVER_GRADLE_PATH, 'utf-8');
    const match = content.match(/^version\s*=\s*['"]([^'"]+)['"]/m);
    if (match) {
      serverVersion = match[1].replace('-SNAPSHOT', '');
    }
  }

  return { clientVersion, desktopVersion, serverVersion };
}

// 递增语义化版本号
function bumpVersion(currentVersion, bumpType) {
  // 清理 -SNAPSHOT 等尾缀
  const cleanVersion = currentVersion.split('-')[0];
  const parts = cleanVersion.split('.').map(Number);
  let [major = 1, minor = 0, patch = 0] = parts;

  switch (bumpType) {
    case 'major':
      major += 1;
      minor = 0;
      patch = 0;
      break;
    case 'minor':
      minor += 1;
      patch = 0;
      break;
    case 'patch':
    default:
      patch += 1;
      break;
  }

  return `${major}.${minor}.${patch}`;
}

// 获取当前 Git Commit SHA
function getGitCommit() {
  try {
    return execSync('git rev-parse --short HEAD', { cwd: ROOT_DIR }).toString().trim();
  } catch (e) {
    return 'unknown';
  }
}

// 获取最新版本的 Git Tag (例如 v2.0.6)
function getLastGitTag() {
  try {
    const tags = execSync('git tag --sort=-v:refname', { cwd: ROOT_DIR }).toString().trim().split('\n');
    return tags.find((t) => /^v?\d+\.\d+\.\d+/.test(t)) || '';
  } catch (e) {
    return '';
  }
}

// 获取从上次 Tag 到 HEAD 的 Commit 历史记录
function getGitCommitsSince(tag) {
  try {
    const range = tag ? `${tag}..HEAD` : 'HEAD~10..HEAD';
    const logs = execSync(`git log ${range} --oneline --no-merges`, { cwd: ROOT_DIR }).toString().trim();
    if (!logs) return [];
    return logs
      .split('\n')
      .map((line) => {
        const parts = line.split(' ');
        parts.shift();
        return parts.join(' ').trim();
      })
      .filter(Boolean);
  } catch (e) {
    return [];
  }
}

// 检查 Git 是否有未提交改动
function isGitClean() {
  try {
    const status = execSync('git status --porcelain', { cwd: ROOT_DIR }).toString().trim();
    return status === '';
  } catch (e) {
    return true;
  }
}

// 更新 hellodoc-client/package.json
function updateClientPackageJson(newVersion) {
  if (!fs.existsSync(CLIENT_PKG_PATH)) return;
  const pkg = JSON.parse(fs.readFileSync(CLIENT_PKG_PATH, 'utf-8'));
  pkg.version = newVersion;
  fs.writeFileSync(CLIENT_PKG_PATH, JSON.stringify(pkg, null, 2) + '\n', 'utf-8');
  console.log(colors.green(`  ✓ 更新 hellodoc-client/package.json -> ${newVersion}`));
}

// 更新 hellodoc-desktop/package.json
function updateDesktopPackageJson(newVersion) {
  if (!fs.existsSync(DESKTOP_PKG_PATH)) return;
  const pkg = JSON.parse(fs.readFileSync(DESKTOP_PKG_PATH, 'utf-8'));
  pkg.version = newVersion;
  fs.writeFileSync(DESKTOP_PKG_PATH, JSON.stringify(pkg, null, 2) + '\n', 'utf-8');
  console.log(colors.green(`  ✓ 更新 hellodoc-desktop/package.json -> ${newVersion}`));
}

// 更新 hellodoc-server/build.gradle
function updateServerBuildGradle(newVersion) {
  if (!fs.existsSync(SERVER_GRADLE_PATH)) return;
  let content = fs.readFileSync(SERVER_GRADLE_PATH, 'utf-8');
  content = content.replace(/^version\s*=\s*['"][^'"]+['"]/m, `version = '${newVersion}'`);
  fs.writeFileSync(SERVER_GRADLE_PATH, content, 'utf-8');
  console.log(colors.green(`  ✓ 更新 hellodoc-server/build.gradle -> ${newVersion}`));
}

// 更新根目录 package.json
function updateRootPackageJson(newVersion) {
  if (!fs.existsSync(ROOT_PKG_PATH)) return;
  const pkg = JSON.parse(fs.readFileSync(ROOT_PKG_PATH, 'utf-8'));
  pkg.version = newVersion;
  fs.writeFileSync(ROOT_PKG_PATH, JSON.stringify(pkg, null, 2) + '\n', 'utf-8');
  console.log(colors.green(`  ✓ 更新 package.json (根目录) -> ${newVersion}`));
}

// 更新/追加 CHANGELOG.md
const CHANGELOG_PATH = path.join(ROOT_DIR, 'CHANGELOG.md');

function updateChangelog(version, releaseNotes) {
  const dateStr = new Date().toISOString().split('T')[0];
  const notesMarkdown = releaseNotes.map((item) => `- ${item}`).join('\n');
  const newSection = `## [v${version}] - ${dateStr}\n\n${notesMarkdown}\n\n`;

  let content = '';
  if (fs.existsSync(CHANGELOG_PATH)) {
    content = fs.readFileSync(CHANGELOG_PATH, 'utf-8');
    if (content.startsWith('# Changelog\n\n')) {
      content = '# Changelog\n\n' + newSection + content.slice('# Changelog\n\n'.length);
    } else if (content.startsWith('# Changelog\n')) {
      content = '# Changelog\n\n' + newSection + content.slice('# Changelog\n'.length);
    } else {
      content = `# Changelog\n\n${newSection}` + content;
    }
  } else {
    content = `# Changelog\n\nAll notable changes to HelloDoc will be documented in this file.\n\n${newSection}`;
  }

  fs.writeFileSync(CHANGELOG_PATH, content, 'utf-8');
  console.log(colors.green(`  ✓ 更新 CHANGELOG.md -> v${version}`));
}

// 生成前端版本元数据文件 hellodoc-client/src/version.ts
function generateClientVersionTs(newVersion, releaseNotes = []) {
  const commit = getGitCommit();
  const buildTime = new Date().toLocaleString('zh-CN', { hour12: false });
  const notesJson = JSON.stringify(releaseNotes, null, 2);
  const content = `// 本文件由 scripts/release.js 自动生成，请勿手动修改
export const APP_VERSION = '${newVersion}';
export const BUILD_TIME = '${buildTime}';
export const GIT_COMMIT = '${commit}';
export const RELEASE_NOTES: string[] = ${notesJson};

export default {
  version: APP_VERSION,
  buildTime: BUILD_TIME,
  gitCommit: GIT_COMMIT,
  releaseNotes: RELEASE_NOTES,
};
`;
  fs.writeFileSync(CLIENT_VERSION_TS_PATH, content, 'utf-8');
  console.log(colors.green(`  ✓ 生成 hellodoc-client/src/version.ts`));
}

// 创建 readline 交互界面
function createPrompt() {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });
  return (question) => new Promise((resolve) => rl.question(question, (answer) => resolve(answer.trim())));
}

// 主交互与发版逻辑
async function main() {
  console.log(colors.bold(colors.cyan('\n🚀 HelloDoc 统一自动发版工具\n')));

  const { clientVersion, desktopVersion, serverVersion } = getCurrentVersions();
  console.log(`当前组件版本号状态：`);
  console.log(`  - hellodoc-client  : ${colors.yellow(clientVersion)}`);
  console.log(`  - hellodoc-desktop : ${colors.yellow(desktopVersion)}`);
  console.log(`  - hellodoc-server  : ${colors.yellow(serverVersion)}\n`);

  // 基准当前版本
  const currentBaseVersion = clientVersion !== '0.0.0' ? clientVersion : serverVersion || '2.0.0';

  // 命令行第一个参数（如 node scripts/release.js patch）
  const argType = process.argv[2];
  let targetVersion = '';

  if (argType) {
    if (['patch', 'minor', 'major'].includes(argType.toLowerCase())) {
      targetVersion = bumpVersion(currentBaseVersion, argType.toLowerCase());
    } else if (/^\d+\.\d+\.\d+/.test(argType)) {
      targetVersion = argType;
    }
  }

  const ask = createPrompt();

  if (!targetVersion) {
    const patchVer = bumpVersion(currentBaseVersion, 'patch');
    const minorVer = bumpVersion(currentBaseVersion, 'minor');
    const majorVer = bumpVersion(currentBaseVersion, 'major');

    console.log(`请选择更新版本类型：`);
    console.log(`  1) patch (${colors.green(patchVer)}) - 小补丁 / Bug 修复`);
    console.log(`  2) minor (${colors.green(minorVer)}) - 新功能迭代`);
    console.log(`  3) major (${colors.green(majorVer)}) - 重大架构/破坏性更新`);
    console.log(`  4) custom            - 自定义版本号`);

    const choice = await ask(colors.bold('\n请输入选项 (1-4, 默认 1): '));

    if (choice === '2') {
      targetVersion = minorVer;
    } else if (choice === '3') {
      targetVersion = majorVer;
    } else if (choice === '4') {
      targetVersion = await ask(colors.bold('请输入自定义版本号 (例如 1.2.0): '));
    } else {
      targetVersion = patchVer;
    }
  }

  if (!/^\d+\.\d+\.\d+/.test(targetVersion)) {
    console.log(colors.red('❌ 无效的版本号输入！发版终止。'));
    process.exit(1);
  }

  console.log(colors.bold(`\n确定将项目统一提升为版本: ${colors.green('v' + targetVersion)}`));
  const confirm = await ask(colors.bold('确认继续发版？(y/N): '));
  if (confirm.toLowerCase() !== 'y') {
    console.log(colors.yellow('已取消发版操作。'));
    process.exit(0);
  }

  // 收集更新说明 (Release Notes)
  const lastTag = getLastGitTag();
  const autoCommits = getGitCommitsSince(lastTag);

  console.log(colors.bold(`\n📝 准备更新说明 (Release Notes)：`));
  if (lastTag) {
    console.log(`自上次版本 ${colors.yellow(lastTag)} 以来的提交记录：`);
  } else {
    console.log(`最近的提交记录：`);
  }

  if (autoCommits.length > 0) {
    autoCommits.forEach((msg, idx) => {
      console.log(`  ${colors.cyan(idx + 1 + '.')} ${msg}`);
    });
  } else {
    console.log(colors.yellow(`  (未检测到新的提交记录)`));
  }

  console.log(`\n更新说明录入选项：`);
  console.log(`  1) 直接使用上述 Git 提交记录作为更新说明 (默认)`);
  console.log(`  2) 手动逐行输入自定义更新说明`);
  console.log(`  3) 在 Git 提交记录基础上，追加补充内容`);

  const notesChoice = await ask(colors.bold('\n请选择更新说明方式 (1-3, 默认 1): '));
  let releaseNotes = [];

  if (notesChoice === '2') {
    console.log(colors.cyan('\n请输入更新说明（每行一条，空行按回车结束）：'));
    let lineIdx = 1;
    while (true) {
      const line = await ask(`  ${lineIdx}. `);
      if (!line) break;
      releaseNotes.push(line);
      lineIdx++;
    }
  } else if (notesChoice === '3') {
    releaseNotes = [...autoCommits];
    console.log(colors.cyan('\n请输入要追加的补充说明（每行一条，空行按回车结束）：'));
    let lineIdx = releaseNotes.length + 1;
    while (true) {
      const line = await ask(`  ${lineIdx}. `);
      if (!line) break;
      releaseNotes.push(line);
      lineIdx++;
    }
  } else {
    releaseNotes = autoCommits.length > 0 ? autoCommits : [`v${targetVersion} 版本发布与日常维护`];
  }

  if (releaseNotes.length === 0) {
    releaseNotes = [`v${targetVersion} 版本发布`];
  }

  console.log(colors.bold('\n[1/4] 正在更新各个组件的版本号及元数据...'));
  updateClientPackageJson(targetVersion);
  updateDesktopPackageJson(targetVersion);
  updateServerBuildGradle(targetVersion);
  updateRootPackageJson(targetVersion);
  generateClientVersionTs(targetVersion, releaseNotes);

  console.log(colors.bold('\n[2/4] 正在生成/更新 CHANGELOG.md...'));
  updateChangelog(targetVersion, releaseNotes);

  console.log(colors.bold('\n[3/4] 正在提交 Git、创建版本 Tag 并推送到远程仓库...'));
  try {
    const commitMsg = `chore: 发布版本 v${targetVersion}`;
    const tagMsgHeader = `Release v${targetVersion}\n\n更新说明:\n`;
    const tagMsgBody = releaseNotes.map((n) => `- ${n}`).join('\n');
    const fullTagMsg = tagMsgHeader + tagMsgBody;

    execSync('git add .', { cwd: ROOT_DIR });
    execSync(`git commit -m "${commitMsg}"`, { cwd: ROOT_DIR });

    // 使用临时文件处理多行 Git Tag 消息
    const tempTagMsgPath = path.join(ROOT_DIR, '.git', 'RELEASE_TAG_MSG_TMP');
    fs.writeFileSync(tempTagMsgPath, fullTagMsg, 'utf-8');
    try {
      execSync(`git tag -a "v${targetVersion}" -F "${tempTagMsgPath}"`, { cwd: ROOT_DIR });
    } finally {
      if (fs.existsSync(tempTagMsgPath)) fs.unlinkSync(tempTagMsgPath);
    }

    console.log(colors.green(`  ✓ Git 本地提交成功，打上 Tag: v${targetVersion}`));

    // 自动推送到远程 Gitea 仓库
    console.log(colors.cyan('  ➜ 正在推送到远程 Gitea 仓库 (包含代码及 Tag)...'));
    execSync('git push origin HEAD --follow-tags', { cwd: ROOT_DIR, stdio: 'inherit' });
    console.log(colors.green(`  ✓ 远程 Gitea 代码及 Tag (v${targetVersion}) 推送成功！`));
  } catch (e) {
    console.log(colors.yellow(`  ! Git 自动提交或推送跳过/未变动 (${e.message})`));
  }

  console.log(colors.bold('\n[4/4] 发版流程全部完成！🎉'));
  console.log(`全工程现已升级为版本: ${colors.green('v' + targetVersion)}`);
  console.log(`提示：发版与打包部署完全独立。如需上传并部署到远程服务器，请运行命令：`);
  console.log(`      ${colors.cyan('npm run deploy')}`);

  process.exit(0);
}

main().catch((err) => {
  console.error(colors.red('发版过程出现错误:'), err);
  process.exit(1);
});

