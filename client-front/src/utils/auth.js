import Cookies from 'js-cookie'

/**
 * 终端令牌在 Cookie 里的键名。
 *
 * <p>刻意与管理后台的 `Admin-Token` 分开：两个前端可能在同一台机器、
 * 同一个浏览器上被同时打开（联调时经常如此），键名相同会互相覆盖 ——
 * 后台登录一下就把终端挤下线，反之亦然。再加上两端后端用的
 * `token.secret` 本来就不同、令牌互不通用，键名分开才前后一致。</p>
 */
const TokenKey = 'Client-Token'

export function getToken() {
  return Cookies.get(TokenKey)
}

export function setToken(token) {
  return Cookies.set(TokenKey, token)
}

export function removeToken() {
  return Cookies.remove(TokenKey)
}
