import request from '@/utils/request'

/**
 * 分页查询我的借阅记录。
 *
 * <p>与管理后台的差别有两处：一是**没有**「查谁」的参数，后端按令牌里的囚号等值匹配；
 * 二是终端只能看、不能操作 —— 借出与归还都由管理员在后台完成，
 * 所以这里没有借书、还书接口。</p>
 *
 * @param query 支持 `pageNum`、`pageSize`、`status`
 * @returns {Promise}
 */
export function listMyBorrowRecord(query) {
  return request({
    url: '/client/borrowRecord/myList',
    method: 'get',
    params: query
  })
}

/**
 * 借阅状态下拉选项（借出中 / 已逾期 / 已归还）。
 *
 * <p>标签由后端计算借阅状态时用的同一组常量派生，前端不自己维护状态字典。</p>
 *
 * @returns {Promise} 成功时 data 为 `[{ value, label }]`
 */
export function borrowRecordStatusOptions() {
  return request({
    url: '/client/borrowRecord/statusOptions',
    method: 'get'
  })
}
