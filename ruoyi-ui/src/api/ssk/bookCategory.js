import request from '@/utils/request'

// 查询图书类目列表（一次性全量返回，无需分页）
export function listBookCategory() {
  return request({
    url: '/ssk/bookCategory/list',
    method: 'get'
  })
}

// 查询图书类目列表（排除自身及其下级，用于编辑时选择上级类目）
export function listBookCategoryExcludeSelf(id) {
  return request({
    url: '/ssk/bookCategory/list/exclude/' + id,
    method: 'get'
  })
}

// 查询图书类目详细
export function getBookCategory(id) {
  return request({
    url: '/ssk/bookCategory/' + id,
    method: 'get'
  })
}

// 新增图书类目
export function addBookCategory(data) {
  return request({
    url: '/ssk/bookCategory',
    method: 'post',
    data: data
  })
}

// 修改图书类目
export function updateBookCategory(data) {
  return request({
    url: '/ssk/bookCategory',
    method: 'put',
    data: data
  })
}

// 保存图书类目排序，data 为仅含 id 与 orderNum 的数组
export function updateBookCategorySort(data) {
  return request({
    url: '/ssk/bookCategory/updateSort',
    method: 'put',
    data: data
  })
}

// 删除图书类目，支持批量删除，ids 为以英文逗号分隔的类目ID
export function delBookCategory(ids) {
  return request({
    url: '/ssk/bookCategory/' + ids,
    method: 'delete'
  })
}
