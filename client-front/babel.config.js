/**
 * Babel 配置。
 *
 * <p>preset 用 vue-cli 自带的 `@vue/cli-plugin-babel/preset`，它已经按
 * `package.json` 的 browserslist 决定了需要降级哪些语法特性，
 * 因此这里不额外挂 `@babel/preset-env`，避免两处配置打架。</p>
 *
 * <p>development 环境挂 `dynamic-import-node`：路由里的 `() => import(...)`
 * 会被改成 `require(...)`，页面多的时候热更新明显更快（不必等动态 chunk 重新请求）。
 * 生产构建不带这个插件，所以路由懒加载在打包产物里仍然生效。</p>
 */
module.exports = {
  presets: [
    // https://github.com/vuejs/vue-cli/tree/master/packages/@vue/babel-preset-app
    '@vue/cli-plugin-babel/preset'
  ],
  'env': {
    'development': {
      // 该插件会把所有 import() 转成 require()，页面多时能明显加快热更新
      'plugins': ['dynamic-import-node']
    }
  }
}
