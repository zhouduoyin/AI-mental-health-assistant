import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs'
import { extname, join, resolve } from 'node:path'
import { brotliCompressSync, constants as zlibConstants, gzipSync } from 'node:zlib'
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig, loadEnv } from 'vite'
import type { Plugin } from 'vite'

/**
 * 构建后为静态资源生成 .gz / .br。
 * 部署时由 Nginx 的 gzip_static / brotli_static 直接命中，避免每个请求现压缩；
 * 未开启静态预压缩的服务器也可以直接忽略这些文件。
 */
function precompressAssets(): Plugin {
  let root = process.cwd()
  let outDir = 'dist'
  const compressible = new Set(['.html', '.js', '.css', '.svg', '.json', '.xml', '.txt'])

  const walk = (dir: string, visit: (file: string) => void): void => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name)
      if (entry.isDirectory()) walk(full, visit)
      else visit(full)
    }
  }

  return {
    name: 'precompress-assets',
    apply: 'build',
    configResolved(config) {
      root = config.root
      outDir = config.build.outDir
    },
    closeBundle() {
      const dir = resolve(root, outDir)
      if (!statSync(dir, { throwIfNoEntry: false })?.isDirectory()) return

      walk(dir, (file) => {
        if (!compressible.has(extname(file))) return
        const source = readFileSync(file)
        if (source.length < 1024) return

        const gzip = gzipSync(source, { level: 9 })
        if (gzip.length < source.length) writeFileSync(`${file}.gz`, gzip)

        const brotli = brotliCompressSync(source, {
          params: { [zlibConstants.BROTLI_PARAM_QUALITY]: 5 },
        })
        if (brotli.length < source.length) writeFileSync(`${file}.br`, brotli)
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const cdnBase = (env.VITE_CDN_BASE ?? '').trim().replace(/\/+$/, '')

  return {
    // 默认同域部署；设置 VITE_CDN_BASE 后构建产物会指向 CDN 域名。
    base: cdnBase ? `${cdnBase}/` : '/',
    plugins: [
      vue(),
      // 自动引入 ElMessage、ElMessageBox 等 API，并生成 auto-imports.d.ts
      AutoImport({
        resolvers: [ElementPlusResolver()],
      }),
      // 自动按需注册 <el-*> 组件并生成 components.d.ts
      Components({
        resolvers: [ElementPlusResolver()],
      }),
      precompressAssets(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    define: {
      // 全站只用 <script setup>，关掉 Options API 后 Vue 运行时里对应代码会被摇掉
      __VUE_OPTIONS_API__: 'false',
      __VUE_PROD_DEVTOOLS__: 'false',
      __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'false',
    },
    server: {
      // 前端 /api 请求代理到本地 Spring Boot，避免跨域
      proxy: {
        '/api': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
    preview: {
      // 用 vite preview 验证生产构建时，/api 仍代理到本地后端
      proxy: {
        '/api': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          // 变量与混入自动注入每个 <style lang="scss"> 和 .scss 文件，
          // 组件里直接使用 $color-primary、@include media-down(...) 即可。
          // 令牌文件自身不再注入，避免自引用。
          additionalData: (source: string, filename: string) => {
            const normalized = filename.replace(/\\/g, '/')
            if (/\/(_variables|_mixins)\.scss$/.test(normalized)) {
              return source
            }
            return `@use "@/styles/variables" as *;\n@use "@/styles/mixins" as *;\n${source}`
          },
        },
      },
    },
    build: {
      target: 'es2020',
      // 现代浏览器都支持 modulepreload，去掉 polyfill 可少传一段脚本
      modulePreload: { polyfill: false },
      // 每次构建清理旧文件，避免哈希 chunk 越积越多
      emptyOutDir: true,
      reportCompressedSize: true,
      rollupOptions: {
        output: {
          // 只把变更频率低的框架拆成独立 chunk；
          // Element Plus 交给 Rollup 按路由自动拆分，避免首屏背上一整包组件库
          manualChunks(id: string) {
            if (!id.includes('node_modules')) return
            const path = id.replace(/\\/g, '/')
            if (path.includes('/vue-router/')) return 'vendor-vue-router'
            if (path.includes('/@vue/') || path.includes('/vue/')) return 'vendor-vue'
          },
        },
      },
    },
  }
})
