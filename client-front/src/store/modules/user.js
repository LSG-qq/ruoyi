import { login, logout } from '@/api/login'
import { getProfile } from '@/api/client/profile'
import { getToken, setToken, removeToken } from '@/utils/auth'

/**
 * 当前登录囚犯。
 *
 * <p>与后台的 user 模块相比，这里没有 roles / permissions：
 * 囚犯角色没有任何权限位，终端接口也一律不挂 @PreAuthorize，
 * 留一份恒为空的权限列表只会误导后来者去写按权限渲染的代码。</p>
 */
const user = {
  state: {
    token: getToken(),
    prisonerNumber: '',
    nickName: ''
  },

  mutations: {
    SET_TOKEN: (state, token) => {
      state.token = token
    },
    SET_PRISONER_NUMBER: (state, prisonerNumber) => {
      state.prisonerNumber = prisonerNumber
    },
    SET_NICK_NAME: (state, nickName) => {
      state.nickName = nickName
    }
  },

  actions: {
    /**
     * 登录。成功后把令牌同时写进 store 与 Cookie
     * （Cookie 是刷新页面后还能保持登录的依据）。
     *
     * @param userInfo 含 username / password / code / uuid
     */
    Login({ commit }, userInfo) {
      const username = userInfo.username.trim()
      const password = userInfo.password
      const code = userInfo.code
      const uuid = userInfo.uuid
      return new Promise((resolve, reject) => {
        login(username, password, code, uuid).then(res => {
          setToken(res.token)
          commit('SET_TOKEN', res.token)
          resolve()
        }).catch(error => {
          reject(error)
        })
      })
    },

    /**
     * 拉取当前登录人信息。
     *
     * <p>走的是终端的 `/client/profile`，不是后台的 `/getInfo` ——
     * 后者在 ruoyi-admin 模块里，终端服务不依赖那个模块，调不到。</p>
     */
    GetInfo({ commit }) {
      return new Promise((resolve, reject) => {
        getProfile().then(res => {
          commit('SET_PRISONER_NUMBER', res.prisonerNumber)
          commit('SET_NICK_NAME', res.nickName)
          resolve(res)
        }).catch(error => {
          reject(error)
        })
      })
    },

    /**
     * 退出登录。
     *
     * <p>无论接口调用成功与否都要清掉本地状态：令牌已经过期时，
     * `/logout` 本身会返回 401，如果只在成功分支里清理，
     * 用户会卡在「点了退出却还留在页面上」的状态。</p>
     */
    LogOut({ commit }) {
      const clear = () => {
        commit('SET_TOKEN', '')
        commit('SET_PRISONER_NUMBER', '')
        commit('SET_NICK_NAME', '')
        removeToken()
      }
      return new Promise(resolve => {
        logout().then(() => {
          clear()
          resolve()
        }).catch(() => {
          clear()
          resolve()
        })
      })
    },

    /**
     * 只清本地状态、不调接口。
     *
     * <p>令牌已被判定无效时（例如请求返回 401）用这个，
     * 避免再发一个注定失败的请求。</p>
     */
    FedLogOut({ commit }) {
      return new Promise(resolve => {
        commit('SET_TOKEN', '')
        commit('SET_PRISONER_NUMBER', '')
        commit('SET_NICK_NAME', '')
        removeToken()
        resolve()
      })
    }
  }
}

export default user
