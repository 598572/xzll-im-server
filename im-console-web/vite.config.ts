import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 根据不同的 mode 设置不同的代理目标
  const proxyTarget = mode === 'development.remote'
    ? 'http://47.93.209.60:8092'  // 远程服务器
    : 'http://localhost:8084'      // 本地后端服务

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    server: {
      port: 3000,
      host: true,
      open: true,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          // 不重写路径，保持 /api 前缀
          // 本地: /api/xxx -> localhost:8084/api/xxx
          // 远程: /api/xxx -> 47.93.209.60:8092/api/xxx
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          manualChunks: {
            'element-plus': ['element-plus'],
            'echarts': ['echarts'],
            'vue-vendor': ['vue', 'vue-router', 'pinia']
          }
        }
      }
    }
  }
})
