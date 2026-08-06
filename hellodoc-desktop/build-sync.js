const { execSync } = require('child_process');
const fs = require('fs-extra');
const path = require('path');

async function build() {
    console.log('Building hellodoc-client for desktop...');
    const clientPath = path.resolve(__dirname, '../hellodoc-client');
    const distAppPath = path.resolve(__dirname, './dist-app');

    // 1. 清理旧资源
    if (fs.existsSync(distAppPath)) {
        fs.removeSync(distAppPath);
    }

    // 2. 构建前端工程
    //    --base=/  : 使用绝对资源路径，避免在二级路由新窗口打开时资源相对路径错误
    //    --outDir  : 直接输出到桌面工程的 dist-app 目录
    execSync(`npx vue-tsc -b && npx vite build --base=/ --outDir=${distAppPath} --emptyOutDir`, {
        cwd: clientPath, 
        stdio: 'inherit',
        env: {
            ...process.env,
            PATH: process.env.PATH + ':/usr/local/bin',
        }
    });

    console.log('Build and sync completed!');
}

build().catch(err => {
    console.error('Build failed:', err);
    process.exit(1);
});
