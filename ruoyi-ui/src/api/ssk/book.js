import request from '@/utils/request'

// 查询图书列表（支持按类目、书架号、书籍名称、作者分页查询）
export function listBook(query) {
  return request({
    url: '/ssk/book/list',
    method: 'get',
    params: query
  })
}

// 查询图书详细
export function getBook(id) {
  return request({
    url: '/ssk/book/' + id,
    method: 'get'
  })
}

// 新增图书
export function addBook(data) {
  return request({
    url: '/ssk/book',
    method: 'post',
    data: data
  })
}

// 修改图书
export function updateBook(data) {
  return request({
    url: '/ssk/book',
    method: 'put',
    data: data
  })
}

// 删除图书，支持批量删除，ids 为以英文逗号分隔的图书ID
export function delBook(ids) {
  return request({
    url: '/ssk/book/' + ids,
    method: 'delete'
  })
}
