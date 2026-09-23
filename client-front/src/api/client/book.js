import request from '@/utils/request'

/**
 * 分页查询可借图书。
 *
 * <p>返回的是分页对象（`rows` / `total`），字段与后台图书列表不同：
 * 后端用的是终端专用 VO，只下发书名、作者、封面、书架号、库存等展示字段，
 * **不含 createdBy / createdAt 这类审计字段**。</p>
 *
 * @param query 查询条件，支持 `pageNum`、`pageSize`、`name`、`author`、`shelfCode`、`categoryId`
 * @returns {Promise}
 */
export function listBook(query) {
  return request({
    url: '/client/book/list',
    method: 'get',
    params: query
  })
}

/**
 * 查询图书类目（扁平列表）。
 *
 * <p>返回的是 `AjaxResult.data` 里的一条扁平数组，**没有 total**（不是分页接口）。
 * 树形结构由前端用 `handleTree` 组装，与后台图书页的做法一致。</p>
 *
 * @returns {Promise} 成功时 data 为 `[{ id, parentId, title, orderNum }]`
 */
export function listCategory() {
  return request({
    url: '/client/book/categoryList',
    method: 'get'
  })
}
