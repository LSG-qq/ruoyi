/**
 * axios 实例：终端所有接口调用的统一出入口。
 *
 * <p>结构照搬 ruoyi-ui 的 `utils/request.js`，只在两处按终端场景改了：</p>
 * <ul>
 *   <li>网络层失败文案里的「系统」改成「终端」（如「终端服务连接异常」「终端接口请求超时」），
 *       便于现场一眼看出是哪个进程出的问题；</li>
 *   <li>去掉了后台那套「防重复提交」逻辑 —— 终端没有表单批量提交的场景。</li>
 * </ul>
 *
 * <p>错误文案一律以**后端下发的 msg 为准**（见 errorCode.js 的说明），
 * 前端只在网络层异常、拿不到后端响应时才自己拼文案。</p>
 *
 * <p>约定：拦截器成功时直接返回 `res.data`（即后端的 AjaxResult / TableDataInfo），
 * 所以业务代码里拿到的是 `{ code, msg, data }` 或 `{ rows, total }`，
 * 不需要再写 `.data.data`。失败时一律 reject，错误提示由这里统一弹出，
 * 页面只在需要「失败后回到可用状态」时才写 catch/finally。</p>
 */

import axios from 'axios'
import { Message, MessageBox } from 'element-ui'
import store from '@/store'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { tansParams } from '@/utils/ruoyi'

// 是否显示重新登录的确认框。
// 终端页面上常有几个请求同时发出，令牌过期时它们会几乎同时拿到 401，
// 用一个模块级标记保证只弹一次确认框。
export let isRelogin = { show: false }

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'

// 创建独立实例（不改全局 axios 默认值，避免影响将来可能的第三方调用）
const service = axios.create({
  // 请求 URL 的公共前缀，开发环境是 /dev-api，由 vue.config.js 代理到终端服务 8081
  baseURL: process.env.VUE_APP_BASE_API,
  timeout: 10000
})

// ============================ 请求拦截器 ============================

service.interceptors.request.use(config => {
  // 登录、取验证码这几个接口本身不需要令牌
  const isToken = (config.headers || {}).isToken === false
  if (getToken() && !isToken) {
    config.headers['Authorization'] = 'Bearer ' + getToken()
  }
  // GET 的 params 手工拼成查询串（与 ruoyi-ui 一致），空值会被跳过
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.params = {}
    config.url = url
  }
  return config
}, error => {
  console.log(error)
  return Promise.reject(error)
})

// ============================ 响应拦截器 ============================

service.interceptors.response.use(res => {
  // 未设置状态码则默认成功状态
  const code = res.data.code || 200
  // 获取错误信息：优先用后端下发的 msg，后端没给才退回本地兜底文案
  const msg = errorCode[code] || res.data.msg || errorCode['default']
  if (code === 401) {
    // 令牌失效：只弹一次确认框，其余并发请求静默拒绝
    if (!isRelogin.show) {
      isRelogin.show = true
      MessageBox.confirm('登录状态已过期，您可以继续留在该页面，或者重新登录', '系统提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        isRelogin.show = false
        store.dispatch('LogOut').then(() => {
          // 终端没有后台那套首页，回登录页
          location.href = '/login'
        })
      }).catch(() => {
        isRelogin.show = false
      })
    }
    return Promise.reject('无效的会话，或者会话已过期，请重新登录。')
  } else if (code === 500) {
    // 业务校验失败（库存不足、重复申请、天数超限等）都走这里，
    // 用后端下发的 msg 原样提示——这类文案是业务方定的，前端不重写
    Message({ message: msg, type: 'error' })
    return Promise.reject(new Error(msg))
  } else if (code !== 200) {
    Message({ message: msg, type: 'error' })
    return Promise.reject('error')
  } else {
    return res.data
  }
}, error => {
  console.log('err' + error)
  let { message } = error
  if (message === 'Network Error') {
    message = '终端服务连接异常'
  } else if (message.includes('timeout')) {
    message = '终端接口请求超时'
  } else if (message.includes('Request failed with status code')) {
    message = '终端接口' + message.slice(-3) + '异常'
  }
  Message({ message: message, type: 'error', duration: 5 * 1000 })
  return Promise.reject(error)
})

export default service
