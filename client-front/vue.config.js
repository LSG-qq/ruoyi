/**
 * 终端前端的构建配置（@vue/cli-service 4.x）。
 *
 * <p>整体照搬 ruoyi-ui 的 vue.config.js，只按终端场景改了四处，
 * 每一处都在下方对应位置写了「为什么不同」，改动前请先读那段注释：</p>
 * <ol>
 *   <li>dev server 端口 81、接口代理指向 **8081**（终端服务），而不是后台的 80 → 8080；</li>
 *   <li>不注册 svg-sprite-loader —— 终端不使用后台那套 svg 图标；</li>
 *   <li>标题与接口前缀改由 .env.development / .env.production 提供；</li>
 *   <li>保留 gzip 压缩与按库拆包，产物结构与后台一致，便于同一套 nginx 规则部署。</li>
 * </ol>
 *
 * <p><b>关于 `NODE_OPTIONS=--openssl-legacy-provider`：本工程不需要它。</b>
 * 网上大量 Vue CLI 4 的文章让你加上它，是因为那些文章写在 webpack 4.46 及更早 ——
 * 那时 webpack 直接向 OpenSSL 要 md4，而 Node 17+ 的 OpenSSL 3 把 md4 移进了
 * legacy provider，于是报 `error:0308010C:digital envelope routines::unsupported`。
 * 本工程用的是 **webpack 4.47.0**，它的 `util/createHash` 在 Node &gt;= 18 时
 * 改用自带的 JS/WASM 版 md4，完全不经过 OpenSSL（实测 Node 22/24 上均可用，
 * 且算出的 digest 与开启 legacy provider 后 OpenSSL 的结果一致，不影响 hash 稳定性）。
 * 另外两个会碰 md4 的包也都自带兜底：`loader-utils@2.0.4` 会捕获该错误后回落到
 * 自己的 JS md4；`compression-webpack-plugin` 直接复用 webpack 的 createHash。
 * 只有 Node 17 会踩（webpack 里的判断是 `&gt;= 18`），而本机是 Node 22/24。
 * 顺带说明为什么不"顺手"把它写进 package.json 或系统环境变量：
 * 该开关会让整个 Node 进程加载 OpenSSL 的 legacy provider，把 MD4/RC4/DES 这类
 * 早已淘汰的弱算法重新暴露出来 —— 为了一个走不到的代码路径去降低加密策略等级不划算。</p>
 */

'use strict'
const path = require('path')

// 把相对路径换算成绝对路径，供 alias 与 copy 插件使用
function resolve(dir) {
  return path.join(__dirname, dir)
}

const CompressionPlugin = require('compression-webpack-plugin')

const name = process.env.VUE_APP_TITLE || '图书借阅终端' // 网页标题

// 终端后端接口地址。管理后台前端（ruoyi-ui）代理到 8080，
// 终端前端代理到 8081 —— 两个后端进程各自独立，前端也必须各指各的。
const baseUrl = 'http://localhost:8081'

// 终端前端固定占用 81：管理后台前端用的是 80，两个前端同时启动时不能撞端口。
// 仍然保留环境变量覆盖能力，便于临时换端口排查。
const port = process.env.port || process.env.npm_config_port || 81

// vue.config.js 配置说明
// 官方 vue.config.js 参考文档 https://cli.vuejs.org/zh/config/#css-loaderoptions
module.exports = {
  // 部署生产环境和开发环境下的URL
  publicPath: '/',
  // 构建输出目录
  outputDir: 'dist',
  // 静态资源目录
  assetsDir: 'static',
  // 生产环境不生成 source map，加快构建
  productionSourceMap: false,

  // ======================= dev server =======================
  // webpack-dev-server 相关配置
  devServer: {
    host: '0.0.0.0',
    port: port,
    open: true,
    proxy: {
      // 开发环境把 /dev-api 前缀剥掉后转发到终端服务
      // 例：GET /dev-api/client/book/list -> GET http://localhost:8081/client/book/list
      [process.env.VUE_APP_BASE_API]: {
        target: baseUrl,
        changeOrigin: true,
        pathRewrite: {
          ['^' + process.env.VUE_APP_BASE_API]: ''
        }
      },
      // 终端服务的 springdoc 文档，联调时可直接在浏览器里翻接口
      '^/v3/api-docs/(.*)': {
        target: baseUrl,
        changeOrigin: true
      }
    },
    disableHostCheck: true
  },
  // ======================= 样式 =======================
  // sass 编译设置。变量统一放 src/assets/styles/theme.scss、全局样式放 app.scss
  // （由 main.js 引入），这里只决定编译产物的可读性，不注入任何变量 ——
  // 页面用 CSS 自定义属性取主题变量，避免依赖构建期路径解析。
  css: {
    loaderOptions: {
      sass: {
        sassOptions: { outputStyle: 'expanded' }
      }
    }
  },

  // ======================= 打包与别名 =======================
  configureWebpack: {
    name: name,
    resolve: {
      alias: {
        '@': resolve('src')
      }
    },
    plugins: [
      // 构建时对静态资源做 gzip，交由 nginx 等前置服务判断是否下发
      new CompressionPlugin({
        cache: false,                                  // 不启用文件缓存
        test: /\.(js|css|html|jpe?g|png|gif|svg)?$/i,  // 压缩文件格式
        filename: '[path][base].gz[query]',            // 压缩后的文件名
        algorithm: 'gzip',                             // 使用gzip压缩
        minRatio: 0.8,                                 // 压缩比例，小于 80% 的文件不会被压缩
        deleteOriginalAssets: false                    // 压缩后删除原文件
      })
    ]
  },
  // ======================= 链路微调 =======================
  chainWebpack(config) {
    // 去掉 preload / prefetch：终端只有三页且都在首屏用得到，
    // 这两组标签只会多出无用的请求，反而拖慢一体机上的首屏
    config.plugins.delete('preload')
    config.plugins.delete('prefetch')

    // 说明：这里与 ruoyi-ui 的配置有一处刻意不同 —— 不注册 svg-sprite-loader。
    // 终端不使用后台那套 svg 图标，图标一律用 element-ui 自带的，少一层构建配置。

    config.when(process.env.NODE_ENV !== 'development', config => {
      config
        .plugin('ScriptExtHtmlWebpackPlugin')
        .after('html')
        .use('script-ext-html-webpack-plugin', [{
          // `runtime` 必须与 runtimeChunk 的名字一致，默认就是 runtime
          inline: /runtime\..*\.js$/
        }])
        .end()

      config.optimization.splitChunks({
        chunks: 'all',
        cacheGroups: {
          libs: {
            name: 'chunk-libs',
            test: /[\\/]node_modules[\\/]/,
            priority: 10,
            chunks: 'initial' // 只打包初始时依赖的第三方
          },
          elementUI: {
            name: 'chunk-elementUI', // 单独将 elementUI 拆包
            test: /[\\/]node_modules[\\/]_?element-ui(.*)/,
            priority: 20 // 权重需大于 libs 和 app，否则会被打包进 libs 或 app
          },
          commons: {
            name: 'chunk-commons',
            test: resolve('src/components'),
            minChunks: 3,
            priority: 5,
            reuseExistingChunk: true
          }
        }
      })
      config.optimization.runtimeChunk('single')
    })
  }
}
