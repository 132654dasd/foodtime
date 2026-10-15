import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig } from 'vite'

/** 后端地址：dev server 只做代理，不做 mock */
const BACKEND = 'http://localhost:8080'

/**
 * 手动分包：vue/router/pinia、element-plus、echarts 三块互不牵连。
 *
 * 这里踩了两个坑，记下来免得以后又被坑：
 * 1. Vite 8 底层是 rolldown。Rollup 时代的 `manualChunks` 虽然还能传，
 *    但实测「函数被调用了、返回值却没生效」，所以改用官方推荐的
 *    `output.codeSplitting.groups`。
 * 2. 分组默认 `includeDependenciesRecursively: true`：element-plus 组会顺着依赖
 *    把 Vue 一起吞掉，导致 vendor 里根本没有 Vue。必须显式关掉，
 *    并按 rolldown 的建议同时设置 preserveEntrySignatures / strictExecutionOrder。
 * 3. Windows 上模块 id 是反斜杠路径，正则要用 `[\\/]`。
 */
const CHUNK_GROUPS = [
  {
    name: 'vendor',
    test: /[\\/]node_modules[\\/](vue|@vue|vue-router|pinia)[\\/]/,
    priority: 30,
  },
  {
    name: 'element-plus',
    test: /[\\/]node_modules[\\/](element-plus|@element-plus)[\\/]/,
    priority: 20,
  },
  {
    name: 'echarts',
    test: /[\\/]node_modules[\\/](echarts|zrender)[\\/]/,
    priority: 10,
  },
]

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // ElMessage / ElMessageBox 等 El 系列函数按需自动引入
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/types/auto-imports.d.ts',
    }),
    // Element Plus 组件（含样式）按需引入
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/types/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: true },
      '/uploads': { target: BACKEND, changeOrigin: true },
      // 页脚的「后端服务状态」直接用公开的 health 端点
      '/actuator': { target: BACKEND, changeOrigin: true },
    },
  },
  build: {
    target: 'es2022',
    chunkSizeWarningLimit: 1500,
    rolldownOptions: {
      // 关掉依赖递归捕获后，官方建议同时打开这两个开关，避免生成非法 chunk
      preserveEntrySignatures: false,
      output: {
        strictExecutionOrder: true,
        codeSplitting: {
          groups: CHUNK_GROUPS,
        },
      },
    },
  },
})
