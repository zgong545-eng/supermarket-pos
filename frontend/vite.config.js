import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 三端共用一个 Vite 应用，靠路由分组区分：
 *   /admin/**  管理端（Element Plus，左侧菜单）
 *   /pos/**    收银端（Element Plus，全屏大按钮）
 *   /h5/**     会员端（Vant，手机尺寸）
 *
 * 为什么不分三个仓库：三端共用同一套接口封装、同一份权限判断、
 * 同一个登录页逻辑。拆三个工程会让"改一个字段要改三处"。
 * 打包体积的代价用路由懒加载抵消——顾客不会下载管理端的表格代码。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发时直接代理到后端，避免跨域和 token 丢失
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200
  }
})
