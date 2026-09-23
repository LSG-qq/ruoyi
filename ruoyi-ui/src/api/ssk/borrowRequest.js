/**
 * 借阅申请模块接口
 *
 * 对应后端 BorrowRequestController（/ssk/borrowRequest）。
 * 申请由囚犯在终端提交，后台只提供「查看列表」「同意」「拒绝」三类操作，
 * 没有新增、修改与删除接口。
 *
 * 权限标识：ssk:borrowRequest:list / approve / reject。
 */
import request from '@/utils/request'

// 查询借阅申请列表（支持按书籍名称、申请状态分页查询）
export function listBorrowRequest(query) {
  return request({
    url: '/ssk/borrowRequest/list',
    method: 'get',
    params: query
  })
}

// 查询申请状态下拉选项（选项由后端状态枚举派生，前端不再自己维护一份）
export function listBorrowRequestStatusOptions() {
  return request({
    url: '/ssk/borrowRequest/statusOptions',
    method: 'get'
  })
}

// 查询借阅天数的默认值与上下限 {defaultDays, minDays, maxDays}
// 与状态下拉同理：由后端统一定义并下发，前端不再写死数字靠人工对齐，
// 否则只改后端不改前端时会出现「界面允许填的天数被后端拒绝」
export function getBorrowRequestDayLimits() {
  return request({
    url: '/ssk/borrowRequest/borrowDayLimits',
    method: 'get'
  })
}

// 同意申请：同意即借出，后端会在同一事务内生成借阅记录并扣减库存
// 只传借阅天数（可空，缺省时后端按 borrowDayLimits 的默认值处理）。
// 借阅人（囚号）不在入参里：后端直接取申请行上的 prisoner_number，
// 审核环节只决定「批不批」、不决定「借给谁」。
export function approveBorrowRequest(id, borrowDays) {
  return request({
    url: '/ssk/borrowRequest/approve/' + id,
    method: 'put',
    params: {
      borrowDays: borrowDays
    }
  })
}

// 拒绝申请：只改状态，不生成借阅记录，也不影响书籍库存
export function rejectBorrowRequest(id) {
  return request({
    url: '/ssk/borrowRequest/reject/' + id,
    method: 'put'
  })
}
