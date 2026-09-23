import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'

NProgress.configure({ showSpinner: false })

// 免登录白名单
const whiteList = ['/login']

/**
 * 终端的路由守卫。
 *
 * <p>与管理后台最大的差别：**这里不生成动态路由**。
 * 后台的路由要根据 `roles` 用 `/getRouters` 拉回来再 addRoutes，
 * 而终端只有固定的三页，囚犯角色也没有任何权限位 ——
 * 照抄那套只会引入「拉不到路由就白屏」的故障面。</p>
 *
 * <p>因此这里只做一件事：没有令牌就送去登录页。</p>
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
