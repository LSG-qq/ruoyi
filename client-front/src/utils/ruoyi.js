/**
 * 终端前端的通用工具。
 *
 * <p>这里只保留终端实际用到的两个函数，实现与 ruoyi-ui 的
 * `src/utils/ruoyi.js` 保持一致 —— 两端行为一致，排查问题时不用记两套逻辑。</p>
 */

/**
 * 把查询对象拼成 URL 查询串。
 *
 * <p>空值（null / 空串 / undefined）会被跳过，不给后端发 `status=` 这种空参数。
 * 后端接口的空值语义统一是「不筛选」，多发一个空串虽然结果一样，但会让日志与
 * 排查时的参数列表变脏。</p>
 *
 * @param params 查询对象
 * @returns {string} 形如 `pageNum=1&pageSize=10&`
 */
export function tansParams(params) {
  let result = ''
  for (const propName of Object.keys(params)) {
    const value = params[propName]
    const part = encodeURIComponent(propName) + '='
    if (value !== null && value !== '' && typeof value !== 'undefined') {
      if (typeof value === 'object') {
        for (const key of Object.keys(value)) {
          if (value[key] !== null && value[key] !== '' && typeof value[key] !== 'undefined') {
            const params = propName + '[' + key + ']'
            const subPart = encodeURIComponent(params) + '='
            result += subPart + encodeURIComponent(value[key]) + '&'
          }
        }
      } else {
        result += part + encodeURIComponent(value) + '&'
      }
    }
  }
  return result
}

/**
 * 把扁平列表组装成树。
 *
 * <p>终端图书页的类目下拉需要树形结构，而后端下发的是扁平列表
 * （与后台图书页保持一致，不把「组装树」这件纯展示的事推到后端）。
 * 根节点判定为「parentId 在数据里找不到对应节点」。</p>
 *
 * @param data     扁平列表
 * @param id       主键字段名，默认 id
 * @param parentId 父节点字段名，默认 parentId
 * @param children 子节点集合字段名，默认 children
 * @returns {Array} 树形列表
 */
export function handleTree(data, id, parentId, children) {
  const config = {
    id: id || 'id',
    parentId: parentId || 'parentId',
    childrenList: children || 'children'
  }

  const childrenListMap = {}
  const nodeIds = {}
  const tree = []

  for (const d of data) {
    const pid = d[config.parentId]
    if (childrenListMap[pid] == null) {
      childrenListMap[pid] = []
    }
    nodeIds[d[config.id]] = d
    childrenListMap[pid].push(d)
  }

  for (const d of data) {
    const pid = d[config.parentId]
    if (nodeIds[pid] == null) {
      tree.push(d)
    }
  }

  for (const t of tree) {
    adaptToChildrenList(t)
  }

  function adaptToChildrenList(o) {
    if (childrenListMap[o[config.id]] !== null) {
      o[config.childrenList] = childrenListMap[o[config.id]]
    }
    if (o[config.childrenList]) {
      for (const c of o[config.childrenList]) {
        adaptToChildrenList(c)
      }
    }
  }

  return tree
}
