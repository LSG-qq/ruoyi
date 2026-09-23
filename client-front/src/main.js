import Vue from 'vue'

import Cookies from 'js-cookie'

import Element from 'element-ui'
// element-ui 的组件样式。**这一行不能少**：它只提供组件的结构与行为，
// 视觉全部来自这份 theme-chalk 样式表；漏掉它整站会退化成裸标签
// （输入框没有边框、卡片没有阴影、提示消息看不见），现象很容易被误判成「页面没渲染」。
import 'element-ui/lib/theme-chalk/index.css'
// 终端自己的全局样式与变量入口（见该文件头注释：theme.scss 管变量，app.scss 管全局规则）
import '@/assets/styles/app.scss'

import App from './App'
import store from './store'
import router from './router'

// 路由守卫：只判「有没有令牌」，不做动态路由（终端无 /getRouters）
import './permission'

// 生产环境下把 Vue 的启动提示关掉，与 ruoyi-ui 保持一致
Vue.config.productionTip = false

// 全局注册 element-ui，并把控件默认尺寸设为 medium。
// 终端装在触摸屏上，medium 比默认的 small 更好点选；size 存在 Cookie 里，
// 便于将来按设备调整而不用改代码。
Vue.use(Element, {
  size: Cookies.get('size') || 'medium'
})

// 挂载根实例。终端只有一个入口组件，页面切换全部由 router 驱动
new Vue({
  el: '#app',
  router,
  store,
  render: h => h(App)
})
