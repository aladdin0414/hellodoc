const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync, spawn } = require('node:child_process');

test('release commits only generated release files, preserving unrelated changes', async (t) => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'hellodoc-release-test-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const run = (...args) => execFileSync('git', args, { cwd: root, stdio: 'pipe' }).toString();
  run('init', '-b', 'main');
  run('config', 'user.name', 'Test');
  run('config', 'user.email', 'test@example.test');
  for (const dir of ['scripts', 'hellodoc-client/src', 'hellodoc-desktop', 'hellodoc-server']) fs.mkdirSync(path.join(root, dir), { recursive: true });
  fs.copyFileSync(path.join(__dirname, '../scripts/release.js'), path.join(root, 'scripts/release.js'));
  for (const file of ['package.json', 'hellodoc-client/package.json', 'hellodoc-desktop/package.json']) fs.writeFileSync(path.join(root, file), '{"version":"1.0.0"}\n');
  fs.writeFileSync(path.join(root, 'hellodoc-server/build.gradle'), "version = '1.0.0'\n");
  fs.writeFileSync(path.join(root, 'tracked.txt'), 'original\n');
  run('add', '.'); run('commit', '-m', 'initial');
  const remote = path.join(root, 'remote.git');
  execFileSync('git', ['init', '--bare', remote], { stdio: 'pipe' });
  run('remote', 'add', 'origin', remote);
  fs.writeFileSync(path.join(root, 'tracked.txt'), 'personal changes\n');
  fs.writeFileSync(path.join(root, 'private.txt'), 'private data\n');
  const child = spawn(process.execPath, ['scripts/release.js', 'patch'], { cwd: root, env: { ...process.env, NO_COLOR: '1' }, stdio: ['pipe', 'pipe', 'pipe'] });
  let output = '', confirmationSent = false, notesSent = false, chineseSent = false, chineseEndSent = false, englishEmptySent = false, wrongPrefixSent = false, englishSent = false;
  const handle = chunk => {
    output += chunk;
    if (!confirmationSent && output.includes('确认继续发版？')) { confirmationSent = true; child.stdin.write('y\n'); }
    if (!notesSent && output.includes('请选择更新说明方式')) { notesSent = true; child.stdin.write('2\n'); }
    if (!chineseSent && output.includes('  1. ')) { chineseSent = true; child.stdin.write('feat: 新增目录\n'); }
    if (!chineseEndSent && output.includes('  2. ')) { chineseEndSent = true; child.stdin.write('\n'); }
    if (!englishEmptySent && output.includes('英文对应 1:')) { englishEmptySent = true; child.stdin.write('\n'); }
    if (!wrongPrefixSent && output.includes('英文说明不能为空')) { wrongPrefixSent = true; child.stdin.write('fix: add folders\n'); }
    if (!englishSent && output.includes('英文说明须使用相同前缀')) { englishSent = true; child.stdin.write('feat: add folders\n'); }
  };
  child.stdout.on('data', handle); child.stderr.on('data', handle);
  const timer = setTimeout(() => child.kill(), 5000);
  const code = await new Promise(resolve => child.on('close', resolve));
  clearTimeout(timer);
  assert.equal(code, 0, output);
  const files = run('show', '--format=', '--name-only', 'HEAD');
  assert.doesNotMatch(files, /tracked\.txt|private\.txt/);
  assert.match(files, /CHANGELOG\.md/);
  const changelog = fs.readFileSync(path.join(root, 'CHANGELOG.md'), 'utf8');
  assert.match(changelog, /### 更新说明\n\n- feat: 新增目录\n\n### Changelog\n\n- feat: add folders/);
  const tag = run('for-each-ref', '--format=%(contents)', 'refs/tags/v1.0.1');
  assert.match(tag, /### 更新说明/); assert.match(tag, /### Changelog/); assert.match(tag, /feat: add folders/);
  assert.equal(fs.readFileSync(path.join(root, 'tracked.txt'), 'utf8'), 'personal changes\n');
  assert.equal(fs.readFileSync(path.join(root, 'private.txt'), 'utf8'), 'private data\n');
  assert.equal(run('rev-parse', 'HEAD').trim(), run('rev-parse', 'v1.0.1^{}').trim());
});
