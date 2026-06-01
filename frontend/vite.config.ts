import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// Docker 컨테이너 내부에서는 VITE_BACKEND_URL 환경변수로 백엔드 주소 주입
// 로컬 실행 시에는 localhost:8080 사용
const backendUrl = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080'
const backendWsUrl = (process.env.VITE_BACKEND_URL ?? 'http://localhost:8080')
  .replace(/^http/, 'ws')

export default defineConfig({
  plugins: [react()],
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    host: '0.0.0.0',   // Docker 컨테이너에서 외부 접근 허용
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
      },
      '/ws': {
        target: backendWsUrl,
        changeOrigin: true,
        ws: true,
        rewrite: (path) => `/api${path}`,
      },
    },
  },
})
