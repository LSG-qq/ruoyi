/**
 * 全局取值的快捷入口。
 *
 * <p>终端只需要「我是谁、我有没有登录」这三个值，
 * 页面上用 `this.$store.getters.xxx` 或 mapGetters 取值。</p>
 */
const getters = {
  /** 终端令牌 */
  token: state => state.user.token,
  /** 当前登录囚号 */
  prisonerNumber: state => state.user.prisonerNumber,
  /** 当前登录人姓名 */
  nickName: state => state.user.nickName
}

export default getters
