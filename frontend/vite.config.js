import { defineConfig, loadEnv } from 'vite'
import path from 'path'
import { viteSingleFile } from 'vite-plugin-singlefile'
import createVitePlugins from './vite/plugins'

const baseUrl = process.env.VITE_BACKEND_URL || 'http://127.0.0.1:18080' // 后端接口

// https://vitejs.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd())
  const { VITE_APP_ENV } = env
  /* 离线演示构建（.env.demo）。产物是单个 HTML，双击就能演示 */
  const isDemo = env.VITE_DEMO === 'true'
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
    // 演示构建必须用相对路径：file:// 下没有站点根目录，'/assets/x.js' 会被解析到磁盘根目录去
    base: isDemo ? './' : '/',
    plugins: [
      ...createVitePlugins(env, command === 'build'),
      // 只在演示构建时启用：把 JS/CSS/字体全部内联进一个 HTML
      ...(isDemo ? [viteSingleFile()] : [])
    ],
    resolve: {
      // https://cn.vitejs.dev/config/#resolve-alias
      alias: {
        // 设置路径
        '~': path.resolve(__dirname, './'),
        // 设置别名
        '@': path.resolve(__dirname, './src')
      },
      // https://cn.vitejs.dev/config/#resolve-extensions
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // 打包配置
    build: {
      // https://vite.dev/config/build-options.html
      sourcemap: command === 'build' ? false : 'inline',
      outDir: 'dist',
      assetsDir: 'assets',
      chunkSizeWarningLimit: 2000,
      /*
        演示构建要把所有资源塞进单个 HTML：
        · assetsInlineLimit 调到很大 —— 图标、字体一律转 base64 内联，不剩外链文件
        · cssCodeSplit 关掉 —— 否则 CSS 会按路由拆成几十个文件，还得再内联回去
        · inlineDynamicImports 打开 —— 路由懒加载会产出独立 chunk，不合并的话
          单文件里仍然会有外链 .js，file:// 下加载不了
      */
      assetsInlineLimit: isDemo ? 100 * 1024 * 1024 : 4096,
      cssCodeSplit: !isDemo,
      rollupOptions: {
        output: {
          inlineDynamicImports: isDemo,
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          assetFileNames: 'static/[ext]/[name]-[hash].[ext]'
        }
      }
    },
    // vite 相关配置
    server: {
      port: Number(process.env.VITE_FRONTEND_PORT || 18081),
      host: true,
      open: true,
      // Vite 6 默认只应答 localhost / IP 的 Host 头，其余一律 403
      // （防 DNS rebinding）。走 ngrok 时 Host 是 xxxx.ngrok-free.app，
      // 不放行的话公网访问会看到 "Blocked request"，而后端和隧道都是好的，
      // 很容易误判成 ngrok 没配通。
      // 前导点表示「该域名的所有子域」——ngrok 免费版每次重启换一个随机子域，
      // 写死某个具体子域的话下次开机就得改代码。
      allowedHosts: (process.env.VITE_ALLOWED_HOSTS || '.ngrok-free.app,.ngrok-free.dev,.ngrok.app,.ngrok.io')
        .split(',').map((h) => h.trim()).filter(Boolean),
      proxy: {
        // https://cn.vitejs.dev/config/#server-proxy
        '/dev-api': {
          target: baseUrl,
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/dev-api/, '')
        },
         // springdoc proxy
         '^/v3/api-docs/(.*)': {
          target: baseUrl,
          changeOrigin: true,
        }
      }
    },
    css: {
      postcss: {
        plugins: [
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule) => {
                if (atRule.name === 'charset') {
                  atRule.remove()
                }
              }
            }
          }
        ]
      }
    }
  }
})
