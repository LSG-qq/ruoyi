<template>
  <el-dialog
    title="提交借阅申请"
    :visible.sync="visible"
    width="440px"
    append-to-body
    @closed="reset"
  >
    <p class="apply-book">《{{ book.name }}》</p>

    <el-form label-width="90px">
      <el-form-item label="借阅天数">
        <el-input-number
          v-model="borrowDays"
          :min="dayLimits.minDays"
          :max="dayLimits.maxDays"
          :step="1"
          controls-position="right"
        />
        <span class="day-tip">可填 {{ dayLimits.minDays }} ~ {{ dayLimits.maxDays }} 天</span>
      </el-form-item>
    </el-form>

    <div slot="footer">
      <el-button @click="visible = false">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { createBorrowRequest, borrowDayLimits } from '@/api/client/borrowRequest'

/**
 * 借阅申请弹窗。
 *
 * <p>按 client-front 代码规范单独封装为组件：对外只暴露 {@link #open}，父页面通过
 * `ref` 调用它来打开窗口（`this.$refs.applyDialog.open(book)`），
 * 而不是在父组件里直接改 visible。这样「申请一本书」所需的全部状态
 * （当前书、借阅天数、提交中标志、天数上下限）都封在本组件内部，
 * 父页面只关心「打开申请」这一件事。</p>
 *
 * <p>组件目录沿用工程既有样板：放在页面目录下的 components/ 子目录
 * （对齐 ruoyi-ui 的 `views/ssk/<模块>/components/XxxForm.vue`），
 * 弹窗与使用它的页面待在一起，比堆到顶层 src/components/ 更好找。</p>
 *
 * <p>提交成功只发一个 `success` 事件、不主动刷新父页面列表：提交申请不占用库存，
 * 图书列表无需重查（用户可在「我的申请」页看到这条新记录）。</p>
 */
export default {
  name: 'BookApplyDialog',

  data() {
    return {
      /** 弹窗是否可见，仅本组件内部维护 */
      visible: false,

      /** 当前要申请的书，由 open() 传入 */
      book: {},

      /** 用户选定的借阅天数 */
      borrowDays: undefined,

      /** 提交中标志，防止重复点击 */
      submitting: false,

      /**
       * 借阅天数的默认值与上下限。
       *
       * <p>初值与后端保持一致的保守值，真实值由 /borrowDayLimits 覆盖 ——
       * 拿不到接口时输入框仍然可用（这只是一个输入边界，真正的校验在后端），
       * 不该因为一个辅助接口失败就让用户没法提交申请。</p>
       */
      dayLimits: {
        defaultDays: 7,
        minDays: 1,
        maxDays: 365
      }
    }
  },

  created() {
    this.loadDayLimits()
  },

  methods: {
    /**
     * 打开弹窗（唯一的对外入口）。
     *
     * @param book 要申请的图书对象，至少含 id 与 name
     */
    open(book) {
      this.book = book || {}
      this.borrowDays = this.dayLimits.defaultDays
      this.visible = true
    },

    /**
     * 取天数的默认值与上下限。
     *
     * <p>失败时保留本地默认值（见 data 里的注释），因此这里静默处理，
     * 不弹提示打扰用户 —— 全局拦截器已经会把网络类错误提示出来。</p>
     */
    loadDayLimits() {
      borrowDayLimits().then(res => {
        this.dayLimits = res.data
      }).catch(() => {})
    },

    /** 关闭后清掉本次的选择，避免下次打开还留着上一本书的名字 */
    reset() {
      this.book = {}
      this.borrowDays = undefined
    },

    /** 提交申请。失败提示（库存为 0、重复申请、天数越界）由全局拦截器统一弹出 */
    submit() {
      this.submitting = true
      createBorrowRequest(this.book.id, this.borrowDays).then(() => {
        this.$message.success('申请已提交，请等待管理员处理')
        this.visible = false
        // 通知父页面（父页面据此决定是否要做后续处理，目前无需刷新列表）
        this.$emit('success')
      }).finally(() => {
        // 无论成功失败都要复位，否则按钮会一直转圈
        this.submitting = false
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.apply-book {
  margin: 0 0 18px;
  font-size: var(--client-font-md);
  font-weight: 600;
  color: var(--client-text-primary);
}

.day-tip {
  margin-left: 12px;
  font-size: var(--client-font-xs);
  color: var(--client-text-secondary);
}
</style>
