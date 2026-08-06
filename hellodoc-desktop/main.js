const { app, BrowserWindow } = require('electron');
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const isDev = process.env.NODE_ENV === 'development';
const WINDOW_STATE_FILE_NAME = 'window-state.json';
const PREVIEW_WINDOW_STATE_FILE_NAME = 'preview-window-state.json';
const LAST_ROUTE_FILE_NAME = 'last-route.json';

function getWindowStateFilePath(stateFileName = WINDOW_STATE_FILE_NAME) {
  return path.join(app.getPath('userData'), stateFileName);
}

function loadWindowState(
  stateFileName = WINDOW_STATE_FILE_NAME,
  defaultState = {
    width: 1200,
    height: 800,
    isMaximized: false,
  }
) {
  try {
    const filePath = getWindowStateFilePath(stateFileName);
    if (!fs.existsSync(filePath)) {
      return defaultState;
    }

    const state = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
    return {
      ...defaultState,
      ...state,
    };
  } catch (err) {
    console.warn('Failed to load window state:', err.message);
    return defaultState;
  }
}

function saveWindowState(window, stateFileName = WINDOW_STATE_FILE_NAME) {
  try {
    const filePath = getWindowStateFilePath(stateFileName);
    const isMaximized = window.isMaximized();
    const bounds = isMaximized ? window.getNormalBounds() : window.getBounds();
    const state = {
      ...bounds,
      isMaximized,
    };
    fs.writeFileSync(filePath, JSON.stringify(state, null, 2), 'utf-8');
  } catch (err) {
    console.warn('Failed to save window state:', err.message);
  }
}

function getLastRouteFilePath() {
  return getWindowStateFilePath(LAST_ROUTE_FILE_NAME);
}

function loadLastRoutePath() {
  try {
    const filePath = getLastRouteFilePath();
    if (!fs.existsSync(filePath)) {
      return '/';
    }

    const state = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
    const routePath = typeof state?.routePath === 'string' ? state.routePath : '/';
    if (!routePath.startsWith('/')) {
      return '/';
    }
    return routePath;
  } catch (err) {
    console.warn('Failed to load last route:', err.message);
    return '/';
  }
}

function saveLastRouteFromUrl(urlString) {
  try {
    if (!urlString) return;
    const parsed = new URL(urlString);
    const routePath = `${parsed.pathname || '/'}${parsed.search || ''}${parsed.hash || ''}`;
    if (!routePath.startsWith('/')) return;

    const filePath = getLastRouteFilePath();
    fs.writeFileSync(
      filePath,
      JSON.stringify(
        {
          routePath,
          savedAt: Date.now(),
        },
        null,
        2
      ),
      'utf-8'
    );
  } catch (err) {
    console.warn('Failed to save last route:', err.message);
  }
}

function buildStartupUrl(port) {
  const routePath = loadLastRoutePath();
  const baseUrl = isDev ? 'http://localhost:3000' : `http://127.0.0.1:${port}`;
  return new URL(routePath, baseUrl).toString();
}

// 后端 API 地址（支持环境变量配置，默认回退至 localhost:8080）
const API_BASE = process.env.API_BASE || 'http://localhost:8080';

// MIME 类型映射
const MIME_TYPES = {
  '.html': 'text/html',
  '.js':   'application/javascript',
  '.css':  'text/css',
  '.json': 'application/json',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.gif':  'image/gif',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
  '.woff': 'font/woff',
  '.woff2':'font/woff2',
  '.ttf':  'font/ttf',
  '.eot':  'application/vnd.ms-fontobject',
  '.wasm': 'application/wasm',
};

/**
 * 启动本地 HTTP 服务器，同时提供静态文件和 API 代理
 */
function startLocalServer(staticDir) {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url, 'http://localhost');

      // API 和 WebSocket 请求代理到后端
      if (url.pathname.startsWith('/api') || url.pathname.startsWith('/ws')) {
        proxyRequest(req, res, url);
        return;
      }

      // 其余请求作为静态文件处理
      serveStaticFile(staticDir, url.pathname, res);
    });

    // 固定端口启动（确保 localStorage 的 origin 一致，登录状态不丢失）
    const FIXED_PORT = 17839;
    server.listen(FIXED_PORT, '127.0.0.1', () => {
      const port = server.address().port;
      console.log(`Local server started at http://127.0.0.1:${port}`);
      resolve(port);
    });

    server.on('error', reject);
  });
}

/**
 * 代理请求到后端服务器
 */
function proxyRequest(clientReq, clientRes, url) {
  let activeApiBase = API_BASE;
  const customBackend = clientReq.headers['x-backend-url'];
  if (customBackend) {
    activeApiBase = customBackend;
    delete clientReq.headers['x-backend-url']; // 避免传递给实际后端导致问题
  }

  const backendUrl = new URL(url.pathname + (url.search || ''), activeApiBase);

  const options = {
    hostname: backendUrl.hostname,
    port: backendUrl.port,
    path: backendUrl.pathname + (backendUrl.search || ''),
    method: clientReq.method,
    headers: {
      ...clientReq.headers,
      host: backendUrl.host,
    },
  };

  const requestModule = backendUrl.protocol === 'https:' ? https : http;

  const proxyReq = requestModule.request(options, (proxyRes) => {
    clientRes.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(clientRes, { end: true });
  });

  proxyReq.on('error', (err) => {
    console.error('Proxy error:', err.message);
    clientRes.writeHead(502);
    clientRes.end('Bad Gateway');
  });

  clientReq.pipe(proxyReq, { end: true });
}

/**
 * 提供静态文件服务
 */
function serveStaticFile(staticDir, pathname, res) {
  const requestExt = path.extname(pathname).toLowerCase();
  let filePath = path.join(staticDir, pathname);

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    if (requestExt) {
      res.writeHead(404);
      res.end('Not Found');
      return;
    }
    filePath = path.join(staticDir, 'index.html');
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404);
      res.end('Not Found');
      return;
    }
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(data);
  });
}

function createWindow(port) {
  const state = loadWindowState(WINDOW_STATE_FILE_NAME, {
    width: 1200,
    height: 800,
    isMaximized: false,
  });
  const windowOptions = {
    width: state.width,
    height: state.height,
    show: false, // 先隐藏，等窗口状态恢复后再显示，避免闪烁
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: __dirname + '/preload.js'
    },
    title: 'HelloDoc Desktop'
  };

  if (Number.isFinite(state.x) && Number.isFinite(state.y)) {
    windowOptions.x = state.x;
    windowOptions.y = state.y;
  }

  const mainWindow = new BrowserWindow(windowOptions);

  if (process.platform === 'win32') {
    mainWindow.setMenuBarVisibility(false);
    mainWindow.removeMenu();
  }

  // 窗口准备好后恢复最大化状态并显示
  mainWindow.once('ready-to-show', () => {
    if (state.isMaximized) {
      mainWindow.maximize();
    }
    mainWindow.show();
  });

  mainWindow.on('close', () => {
    saveWindowState(mainWindow, WINDOW_STATE_FILE_NAME);
    saveLastRouteFromUrl(mainWindow.webContents.getURL());
  });

  mainWindow.webContents.setWindowOpenHandler(() => {
    const previewState = loadWindowState(PREVIEW_WINDOW_STATE_FILE_NAME, {
      width: 1000,
      height: 800,
      isMaximized: false,
    });
    const previewWindowOptions = {
      width: previewState.width,
      height: previewState.height,
      show: false,
      autoHideMenuBar: true,
      title: 'HelloDoc Preview'
    };

    if (Number.isFinite(previewState.x) && Number.isFinite(previewState.y)) {
      previewWindowOptions.x = previewState.x;
      previewWindowOptions.y = previewState.y;
    }

    return {
      action: 'allow',
      overrideBrowserWindowOptions: previewWindowOptions
    };
  });

  mainWindow.webContents.on('did-create-window', (previewWindow) => {
    const previewState = loadWindowState(PREVIEW_WINDOW_STATE_FILE_NAME, {
      width: 1000,
      height: 800,
      isMaximized: false,
    });

    previewWindow.once('ready-to-show', () => {
      if (previewState.isMaximized) {
        previewWindow.maximize();
      }
      previewWindow.show();
    });

    previewWindow.on('close', () => {
      saveWindowState(previewWindow, PREVIEW_WINDOW_STATE_FILE_NAME);
    });
  });

  if (isDev) {
    // 开发环境下加载前端开发服务器（优先恢复上次退出页面）
    mainWindow.loadURL(buildStartupUrl(port));
    mainWindow.webContents.openDevTools();
  } else {
    // 生产环境下加载本地 HTTP 服务器（优先恢复上次退出页面）
    mainWindow.loadURL(buildStartupUrl(port));
  }
}

app.whenReady().then(async () => {
  let port = 3000;

  if (!isDev) {
    // 启动本地服务器，提供静态文件 + API 代理
    const staticDir = path.join(__dirname, 'dist-app');
    port = await startLocalServer(staticDir);
  }

  createWindow(port);

  app.on('activate', function () {
    if (BrowserWindow.getAllWindows().length === 0) createWindow(port);
  });
});

app.on('window-all-closed', function () {
  if (process.platform !== 'darwin') app.quit();
});
