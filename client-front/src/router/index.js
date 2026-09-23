import Vue from 'vue'
import Router from 'vue-router'
import Layout from '@/layout'

Vue.use(Router)

/**
 * 终端的静态路由表。
 *
 * <p>与管理后台的差别：这里全部是**写死**的路由。
 * 后台要先 `/getRouters` 拉菜单再 `addRoutes`，而终端服务不依赖 ruoyi-admin、
 * 根本没有那个接口；囚犯角色也没有菜单授权（零授权）。终端能进哪几页，
 * 由这份路由表决定，这也正是「页面效果用户点验」时的对账依据。</p>
 *
 * <p>三个业务页都挂在同一个 Layout 下，共用顶部栏与左侧菜单。</p>
 */
export const constantRoutes = [
  {
    path: '/login',
    component: () => import('@/views/login'),
    hidden: true
  },
  {
    path: '/',
    component: Layout,
    redirect: '/book',
    children: [
      {
        path: 'book',
        component: () => import('@/views/book/index'),
        name: 'ClientBook',
        meta: { title: '图书浏览', icon: 'el-icon-reading' }
      },
      {
        path: 'request',
        component: () => import('@/views/request/index'),
        name: 'ClientBorrowRequest',
        meta: { title: '我的申请', icon: 'el-icon-document' }
      },
      {
        path: 'record',
        component: () => import('@/views/record/index'),
        name: 'ClientBorrowRecord',
        meta: { title: '我的借阅', icon: 'el-icon-collection' }
      }
    ]
  },
  {
    // 兜底路由：命中不到任何已声明路由时回首页。
    // 少了这一条，history 模式下访问一个不存在的地址（手输错、旧书签）会没有任何匹配，
    // router-view 渲染空白 —— 整页白屏，连顶部栏与菜单都不显示，用户只能自己改回地址。
    // 后台那边是 `* -> /404`，终端没有 404 页，直接回首页即可。
    path: '*',
    redirect: '/',
    hidden: true
  }
]

const createRouter = () => new Router({
  // 使用 history 模式，URL 里不带 #，与 ruoyi-ui 一致。
  // ⚠️ 生产部署前提：前置的 nginx 必须配 `try_files $uri $uri/ /index.html`，
  // 否则用户直接访问或刷新子路径（如 /book）会被 nginx 判成 404。
  // dev 环境下 webpack-dev-server 自带 history fallback，所以本地不会复现这个问题。
  mode: 'history',
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})

const router = createRouter()

export default router
