import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'

NProgress.configure({ showSpinner: false })

/**
 * 免登录白名单。
 *
 * <p>终端首页（`/book`）是「先浏览、后登录」的形态：图书列表与类目树两个接口
 * 在后端标了 `@Anonymous`，因此未登录也要能进得去。
 * `/` 只是首页的入口（会重定向到 `/book`），一并放进来，
 * 免得用户直接访问根地址时被弹回登录页。</p>
 *
 * <p>其余页面（我的申请、我的借阅）仍然要求登录：守卫会把它们重定向到登录页，
 * 并把原地址带在 `redirect` 上，登录后自动回到用户原本想去的地方。</p>
 */
const whiteList = ['/login', '/', '/book']

/**
 * 终端的路由守卫。
 *
 * <p>与管理后台最大的差别：**这里不生成动态路由**。
 * 后台的路由要根据 `roles` 用 `/getRouters` 拉回来再 addRoutes，
 * 而终端只有固定的三页，囚犯角色也没有任何权限位 ——
 * 照抄那套只会引入「拉不到路由就白屏」的故障面。</p>
 *
 * <p>因此这里只做一件事：没登录时，白名单以内的页面放行，其余送去登录页。</p>
 */
router.beforeEach((to, from, next) => {
  NProgress.start()
  if (getToken()) {
    // 已登录还想去登录页，直接回首页
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else {
      next()
    }
  } else {
    if (whiteList.indexOf(to.path) !== -1) {
      // 白名单直接放行
      next()
    } else {
      // 其余全部重定向到登录页，并记住原本要去的地方
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})
