import request from '@/utils/request'

/**
 * 查询当前登录的囚犯。
 *
 * <p>管理后台的 `/getInfo` 在 ruoyi-admin 模块里，终端服务不依赖那个模块、
 * 拿不到它，所以终端用这个接口取「我是谁」：只回传囚号与姓名两个字段。</p>
 *
 * <p>值得留意：响应里**没有** roles 与 permissions。囚犯角色没有任何权限位，
 * 终端接口也一律不挂 @PreAuthorize，前端不要按权限去渲染菜单 ——
 * 终端能做什么，是由「这个接口存不存在」决定的，不是由权限位决定的。</p>
 *
 * @returns {Promise} 成功时返回 `{ prisonerNumber, nickName }`
 */
export function getProfile() {
  return request({
    url: '/client/profile',
    method: 'get'
  })
}
