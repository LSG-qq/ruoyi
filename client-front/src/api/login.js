import request from '@/utils/request'

/**
 * 囚犯登录终端。
 *
 * <p>登录名就是囚号。终端服务的准入白名单（`login.allowed-roles: ssk_prisoner`）
 * 在密码校验通过之后执行，非囚犯角色会拿到「当前账号无权登录本系统」。</p>
 *
 * <p>验证码是一次性的：后端校验后立刻把它从 redis 删掉，
 * 所以 `getCodeImg` 与 `login` 必须成对调用，不能复用同一个 uuid。</p>
 *
 * @param username 囚号
 * @param password 口令
 * @param code     验证码
 * @param uuid     验证码对应的 uuid
 * @returns {Promise} 成功时返回体里带 token
 */
export function login(username, password, code, uuid) {
  return request({
    url: '/login',
    headers: {
      // 登录本身不需要令牌
      isToken: false
    },
    method: 'post',
    data: {
      username,
      password,
      code,
      uuid
    }
  })
}

/**
 * 获取图形验证码。
 *
 * <p>响应里 `captchaEnabled` 为 false 时表示后端关掉了验证码，
 * 此时前端要隐藏验证码输入框，不能硬要求用户填。</p>
 *
 * @returns {Promise} 成功时返回 uuid 与 base64 图片
 */
export function getCodeImg() {
  return request({
    url: '/captchaImage',
    headers: {
      isToken: false
    },
    method: 'get',
    // 图片稍大，超时给宽一些
    timeout: 20000
  })
}

/**
 * 退出登录。
 *
 * <p>接口在 framework 的安全过滤器里，终端服务同样有；
 * 它会把令牌从 redis 里删掉（只是当前这台机器上的会话，不影响管理后台）。</p>
 *
 * @returns {Promise}
 */
export function logout() {
  return request({
    url: '/logout',
    method: 'post'
  })
}
