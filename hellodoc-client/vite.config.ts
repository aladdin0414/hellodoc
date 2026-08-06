import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import VueDevTools from 'vite-plugin-vue-devtools'
import path from 'path'

// 自动在终端打印移动端 H5 快捷访问 URL 的插件
const mobileUrlPlugin = () => {
  return {
    name: 'vite-plugin-mobile-url',
    configureServer(server: any) {
      server.httpServer?.once('listening', () => {
        setTimeout(() => {
          const logger = server.config.logger
          const address = server.httpServer?.address()
          if (address && typeof address === 'object') {
            const port = address.port
            logger.info(`  \x1b[32m➜\x1b[0m  \x1b[1mMobile H5:\x1b[0m \x1b[36mhttp://localhost:${port}/m\x1b[0m`)
          }
        }, 150)
      })
    }
  }
}

import packageJson from './package.json'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const envDir = path.resolve(__dirname, '..')
  const env = loadEnv(mode, envDir, '')
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080'
  const wsTarget = env.VITE_WS_TARGET || 'ws://localhost:8080'
  console.log(`[Vite Proxy Target] => apiTarget: ${apiTarget}, wsTarget: ${wsTarget}`)


  return {
    envDir,
    define: {
      __APP_VERSION__: JSON.stringify(packageJson.version),
      __BUILD_TIME__: JSON.stringify(new Date().toLocaleString('zh-CN', { hour12: false }))
    },
    plugins: [
      VueDevTools(),
      vue(),
      mobileUrlPlugin()
    ],
    base: '/',
    build: {
      outDir: '../hellodoc-server/src/main/resources/static',
      emptyOutDir: true,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return

            const getPackageName = (moduleId: string) => {
              let p = moduleId.split('node_modules/').pop() || ''

              if (p.startsWith('.pnpm/')) {
                p = p.slice('.pnpm/'.length)
                const idx = p.indexOf('/node_modules/')
                if (idx >= 0) p = p.slice(idx + '/node_modules/'.length)
              }

              if (p.startsWith('@')) {
                const [scope, name] = p.split('/')
                if (scope && name) return `${scope}/${name}`
                return p
              }

              return p.split('/')[0] || p
            }

            const pkg = getPackageName(id)

            if (
              pkg === 'vue' ||
              pkg === 'vue-router' ||
              pkg === 'vue-devtools-api' ||
              pkg.startsWith('@vue/')
            ) {
              return 'vue'
            }

            if (id.includes('/axios/')) return 'http'
            if (id.includes('/lucide-vue-next/')) return 'icons'

            if (id.includes('/md-editor-v3/')) return 'md-editor'
            if (id.includes('/markdown-it/')) return 'markdown'
            if (id.includes('/highlight.js/')) return 'highlight'
            if (id.includes('/katex/')) return 'katex'
            if (id.includes('/@kangc/')) return 'kangc'
            if (id.includes('/vue-diff/')) return 'diff'
            if (id.includes('/mermaid/dist/mermaid.core.mjs')) return 'mermaid'
            if (id.includes('/@tiptap/') || id.includes('/prosemirror-') || id.includes('/tiptap-markdown/')) return 'tiptap'
            if (id.includes('/@turbodocx/html-to-docx/')) return 'docx'
            if (id.includes('/lowlight/')) return 'lowlight'
            if (id.includes('/fabric/')) return 'fabric'

            return
          }
        }
      },
      chunkSizeWarningLimit: 1200
    },
    server: {
      port: 3000,
      host: '0.0.0.0',
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true
        },
        '/ws': {
          target: wsTarget,
          ws: true,
          changeOrigin: true
        }
      }
    }
  }
})

