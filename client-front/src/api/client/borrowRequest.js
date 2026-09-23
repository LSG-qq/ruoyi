import request from '@/utils/request'

/**
 * 提交借阅申请。
 *
 * <p>请求体只有两个字段，这是刻意设计：囚号由后端从登录令牌解析，
 * 申请时间取系统时间，申请状态固定为「待处理」。请求体里**没有**这些字段，
 * 前端也就无从传入、无从伪造。</p>
 *
 * <p>失败提示都来自后端（库存为 0、同一本书已有待处理申请、天数越界等），
 * 前端不要自己再写一套文案——否则两边规则一旦不同步，界面说的和后端做的不一致。</p>
 *
 * @param bookId     书籍ID
 * @param borrowDays 借阅天数，不传时后端按申请的默认天数处理
 * @returns {Promise}
 */
export function createBorrowRequest(bookId, borrowDays) {
  return request({
    url: '/client/borrowRequest/create',
    method: 'post',
    data: {
      bookId,
      borrowDays
    }
  })
}

/**
 * 分页查询我的借阅申请。
 *
 * <p>没有「查谁」这个参数：后端按令牌里的囚号**等值**匹配。
 * 底层查询刻意不复用后台列表的模糊匹配，否则囚号 20260001 会命中 20260011，
 * 等于把别人的申请记录翻出来了。</p>
 *
 * @param query 支持 `pageNum`、`pageSize`、`status`
 * @returns {Promise}
 */
export function listMyBorrowRequest(query) {
  return request({
    url: '/client/borrowRequest/myList',
    method: 'get',
    params: query
  })
}

/**
 * 申请状态下拉选项。
 *
 * <p>取值与标签由后端从状态枚举派生，与列表里显示的状态同源；
 * 前端不自己维护一份状态字典。</p>
 *
 * @returns {Promise} 成功时 data 为 `[{ value, label }]`
 */
export function borrowRequestStatusOptions() {
  return request({
    url: '/client/borrowRequest/statusOptions',
    method: 'get'
  })
}

/**
 * 借阅天数的默认值与上下限。
 *
 * <p>提交弹窗要用它渲染输入框的默认值、最小值与最大值。
 * 这些数字只在后端定义一次、由接口下发，前端不写死，
 * 免得出现「界面允许填 400 天、提交后被后端拒绝」这种前后矛盾。</p>
 *
 * @returns {Promise} 成功时 data 为 `{ defaultDays, minDays, maxDays }`
 */
export function borrowDayLimits() {
  return request({
    url: '/client/borrowRequest/borrowDayLimits',
    method: 'get'
  })
}
