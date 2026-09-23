import Vue from 'vue'
import Vuex from 'vuex'
import user from './modules/user'
import getters from './getters'

Vue.use(Vuex)

/**
 * 终端的状态管理。
 *
 * <p>只有一个 user 模块 —— 管理后台那套 dict / permission / tagsView / settings
 * 在这里都没有意义：终端没有字典下拉、没有权限位、没有多页签、没有动态菜单。
 * 状态越少，越不容易出现「后台改了这里忘了那里」的不一致。</p>
 */
const store = new Vuex.Store({
  modules: {
    user
  },
  getters
})

export default store
