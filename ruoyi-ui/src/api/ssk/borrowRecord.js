import request from '@/utils/request'

// 查询借阅记录列表（支持按囚号、书籍名称、借阅状态分页查询）
export function listBorrowRecord(query) {
  return request({
    url: '/ssk/borrowRecord/list',
    method: 'get',
    params: query
  })
}

// 查询借出弹窗的书籍下拉数据（返回全部未删除的书籍，含库存）
export function listBorrowBookOptions() {
  return request({
    url: '/ssk/borrowRecord/bookOptions',
    method: 'get'
  })
}

// 借出图书（新增借阅记录）
export function addBorrowRecord(data) {
  return request({
    url: '/ssk/borrowRecord',
    method: 'post',
    data: data
  })
}

// 归还图书，实际归还时间由后端取当前系统时间
export function returnBorrowRecord(id) {
  return request({
    url: '/ssk/borrowRecord/return/' + id,
    method: 'put'
  })
}
