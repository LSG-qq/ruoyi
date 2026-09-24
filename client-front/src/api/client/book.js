import request from '@/utils/request'

/**
 * 图书浏览相关的接口（终端首页）。
 *
 * <p><b>本文件里的三个接口（列表、详情、类目）都无需登录。</b>终端首页是「先浏览、后登录」的形态：
 * 未登录的人可以看类目树、图书列表与图书详情，点「申请借阅」时才被引导去登录。
 * 对应的后端放行由 `ClientBookController` 上的 `@Anonymous` 注解声明
 * （若依的 `PermitAllUrlProperties` 启动时自动收集成放行清单），
 * 这里不需要、也不应该再传 `isToken: false` 之类的标记去绕过令牌 ——
 * 有令牌时照样带上（返回结果与不带令牌一致），避免同一份数据在两处拼接。</p>
 */

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
 * 查询图书详情。
 *
 * <p>返回的是单本图书对象（不是分页对象），字段与列表里的每一项完全相同 ——
 * 后端复用同一条查询与同一套终端字段裁剪，因此详情不会出现列表里没有的字段，
 * 也不会把 createdBy 这类内部审计字段带出来。</p>
 *
 * <p><b>本接口与列表一样无需登录</b>：图书详情属于馆藏公开信息，
 * 未登录的人点开一本书也应该能看到简介与图片。</p>
 *
 * @param id 图书ID
 * @returns {Promise} 成功时 data 为单本图书对象（含 images 图片集，逗号分隔）
 */
export function getBookDetail(id) {
  return request({
    url: `/client/book/detail/${id}`,
    method: 'get'
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

