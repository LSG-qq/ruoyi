<template>
  <!-- 同意借阅申请弹窗：核对书籍与囚号 → 填写借阅天数 → 确认即视为同意借出 -->
  <el-dialog
    title="同意借阅申请"
    :visible.sync="visible"
    width="560px"
    append-to-body
    :close-on-click-modal="false"
    @closed="reset"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="90px">
      <!-- 申请对应的书籍信息只读展示，避免在审核环节再选一次书，造成申请与实际借出的书不一致 -->
      <el-form-item label="书籍">
        <div class="book-info">
          <el-image
            v-if="book.bookCover"
            :src="imageUrl(book.bookCover)"
            fit="contain"
            class="book-info__cover"
          />
          <div class="book-info__text">
            <div class="book-info__name">{{ book.bookName || "-" }}</div>
            <div class="book-info__meta">
              <span>作者：{{ book.bookAuthor || "-" }}</span>
              <!-- 书架号用于管理员按架位取书，因此在这里也要能直接看到 -->
              <span class="book-info__shelf">
                书架号：<dict-tag :options="dict.type.book_shelfs" :value="book.bookShelfCode" />
              </span>
            </div>
          </div>
        </div>
      </el-form-item>
      <!-- 囚号取自申请记录（终端提交申请时写入登录的那个囚号），这里只读展示：
           审核环节只决定「批不批」、不决定「借给谁」，能改就会出现「申请人与借阅人对不上」
           而且事后无从追查。缺号只可能是补列之前的历史数据，给出提示但不在这里做补救。 -->
      <el-form-item label="囚号">
        <span class="prisoner-number">{{ prisonerNumberText }}</span>
        <span v-if="!hasPrisonerNumber" class="prisoner-number__warn">该申请没有记录囚号，无法生成借阅记录</span>
      </el-form-item>
      <!-- 借阅天数：默认值与上下限由后端 /borrowDayLimits 下发，提交前即可看到应还时间 -->
      <el-form-item label="借阅天数" prop="borrowDays">
        <el-input-number
          v-model="form.borrowDays"
          :min="borrowDayLimits.minDays"
          :max="borrowDayLimits.maxDays"
          :step="1"
          step-strictly
          controls-position="right"
          style="width: 160px"
        />
        <span class="day-hint">天，默认 {{ borrowDayLimits.defaultDays }} 天</span>
      </el-form-item>
      <!-- 应还时间预览，只读：由借出时间与借阅天数算得，不接受手工填写 -->
      <el-form-item label="应还时间">
        <span class="return-time">{{ returnTimeText }}</span>
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="确认后即视为同意借出：系统会生成一条借阅记录，并扣减该书籍 1 本库存。"
      />
    </el-form>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" :loading="submitting" @click="submitForm">确 认</el-button>
      <el-button @click="visible = false">取 消</el-button>
    </div>
  </el-dialog>
</template>

<script>
/**
 * 同意借阅申请弹窗
 *
 * 由列表页通过 ref 调用 open(row) 打开，row 是列表中的申请行（含申请ID、囚号与书籍信息）。
 * 弹窗内只让使用者补一样信息：借阅天数 —— 书籍与囚号都由申请决定，不能在这里改：
 * 改了书籍会出现「申请的是 A 书、实际借出的是 B 书」，改了囚号会出现「申请人与借阅人对不上」，
 * 两者事后都无从追查。因此囚号只读展示，不作为表单输入项。
 * 借阅天数的默认值与上下限由后端 /ssk/borrowRequest/borrowDayLimits 下发，
 * 本组件不维护自己的数字，免得后端调整上限后界面还在放行超限值。
 *
 * 借出时间在打开弹窗的那一刻固定下来、不随时间跳动，这样「应还时间」的预览
 * 与提交后后端算出的结果一定一致，不会出现看着是 7 天、落库变成 8 天的情况。
 *
 * 点击「确 认」即视为同意借出：后端会在同一个事务内生成借阅记录并扣减 1 本库存，
 * 库存不足、该囚号已借了同一本书未还、或该申请缺囚号时，会整体回滚并给出可读提示。
 */
import { approveBorrowRequest, getBorrowRequestDayLimits } from "@/api/ssk/borrowRequest"

/**
 * 借阅天数的兜底值（默认值与上下限）
 *
 * 正常情况这三个值来自后端 /ssk/borrowRequest/borrowDayLimits，前端不维护自己的数字，
 * 免得只改后端不改前端时出现「界面允许填、提交被拒绝」。
 * 但接口失败或返回不全时，输入框的 :min / :max 与「默认 N 天」就没有值可渲染，
 * 因此留一份最小兜底，保证弹窗在任何情况下都能打开并使用；
 * 一旦接口有返回就整体以后端为准，兜底只在拿不到数据时生效。
 */
const FALLBACK_BORROW_DAY_LIMITS = {
  defaultDays: 7,
  minDays: 1,
  maxDays: 365
}

export default {
  name: "ApproveForm",
  // 书架号取自字典 book_shelfs，用于把 ssk_book.shelf_code 还原成字典标签
  dicts: ["book_shelfs"],
  data() {
    return {
      // 借阅天数的默认值与上下限：先用兜底渲染，接口回来后整体替换，后端是唯一权威来源
      borrowDayLimits: Object.assign({}, FALLBACK_BORROW_DAY_LIMITS),
      // 弹窗显示状态
      visible: false,
      // 提交中，防止连点「确 认」发出两次同意请求
      submitting: false,
      // 被审核的申请，用于取 id 与只读展示书籍信息
      request: null,
      // 借出时间，打开弹窗时固定
      borrowTime: null,
      // 表单数据（borrowDays 的初值仅是占位，每次 open 都会按 borrowDayLimits.defaultDays 重置）
      // 只收借阅天数：囚号来自申请记录、只读展示，不是表单输入项
      form: {
        borrowDays: FALLBACK_BORROW_DAY_LIMITS.defaultDays
      },
      // 校验规则：只做界面层提示，真正的约束以后端为准
      rules: {
        borrowDays: [{ required: true, message: "请填写借阅天数", trigger: "change" }]
      }
    }
  },
  created() {
    // 天数元数据与列表互不依赖，组件挂载时就取回并缓存，弹窗打开时直接可用；
    // 失败时保持兜底值，弹窗照常打开，不因为一个元数据接口卡住审核流程
    this.loadBorrowDayLimits()
  },
  computed: {
    // 被审核的申请，未打开弹窗前为空对象，模板可以安全取属性而不用层层判空
    book() {
      return this.request || {}
    },
    /**
     * 申请行上的囚号，未打开弹窗、或历史数据缺号时显示「-」
     * @returns {String} 囚号
     */
    prisonerNumberText() {
      return (this.request && this.request.prisonerNumber) || "-"
    },
    /**
     * 申请是否带有囚号
     * 只用来把「这条申请缺囚号」提前摆在界面上（严格说只可能是补列之前的历史数据），
     * 真正的拦截在后端 —— 提交时会给出明确提示，这里不做补救也不会放行。
     * @returns {Boolean} 有囚号返回 true
     */
    hasPrisonerNumber() {
      return !!(this.request && this.request.prisonerNumber)
    },
    // 应还时间预览：借出时间 + 借阅天数；天数越界时退化成只显示借出时间，不显示错误结果
    returnTimeText() {
      if (!this.borrowTime) {
        return "-"
      }
      const days = Number(this.form.borrowDays)
      const returnTime = this.isValidDays(days)
        ? this.addDays(this.borrowTime, days)
        : this.borrowTime
      return this.formatDateTime(this.borrowTime) + " 至 " + this.formatDateTime(returnTime)
    }
  },
  methods: {
    /**
     * 加载借阅天数的默认值与上下限
     *
     * 三个值都由后端下发，前端只做渲染；接口返回不完整时保留兜底，
     * 避免输入框的 :min / :max 变成 undefined（那样会退化成不限制范围）。
     */
    loadBorrowDayLimits() {
      getBorrowRequestDayLimits().then(response => {
        const limits = response.data || {}
        // 逐项判断：任一字段缺失都退回兜底值，不让 undefined 传进模板
        this.borrowDayLimits = {
          defaultDays: this.pickLimit(limits.defaultDays, FALLBACK_BORROW_DAY_LIMITS.defaultDays),
          minDays: this.pickLimit(limits.minDays, FALLBACK_BORROW_DAY_LIMITS.minDays),
          maxDays: this.pickLimit(limits.maxDays, FALLBACK_BORROW_DAY_LIMITS.maxDays)
        }
      }).catch(() => {
        // 失败提示由全局请求拦截器统一弹出，这里只把兜底值留住
        this.borrowDayLimits = Object.assign({}, FALLBACK_BORROW_DAY_LIMITS)
      })
    },
    /**
     * 取接口返回的天数配置，非法值退回兜底
     *
     * 注意 null / undefined / 空串要单独判：Number(null) 与 Number("") 都得 0 且不是 NaN，
     * 只判 isNaN 会让「没返回」被当成「上限是 0」，下拉框直接锁死。
     * @param {*} value 接口返回值
     * @param {Number} fallback 兜底值
     */
    pickLimit(value, fallback) {
      if (value === null || value === undefined || value === "") {
        return fallback
      }
      const num = Number(value)
      return isNaN(num) ? fallback : num
    },
    /**
     * 打开弹窗
     * @param {Object} row 列表中的申请行数据（含申请ID、书籍信息、状态）
     */
    open(row) {
      this.resetFormData()
      this.request = row || null
      this.borrowTime = new Date()
      this.visible = true
      this.$nextTick(() => {
        if (this.$refs.form) {
          this.$refs.form.clearValidate()
        }
      })
    },
    /** 关闭后还原表单，避免下次打开残留上一次的输入 */
    reset() {
      this.resetFormData()
      this.request = null
      this.borrowTime = null
      this.submitting = false
    },
    /** 清空表单数据，两处入口（打开与关闭）共用，保证状态一致 */
    resetFormData() {
      this.form = {
        borrowDays: this.borrowDayLimits.defaultDays
      }
    },
    /** 提交：确认即同意借出 */
    submitForm() {
      if (!this.request || !this.request.id) {
        this.$modal.msgWarning("未获取到借阅申请信息，请刷新列表后重试")
        return
      }
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        this.submitting = true
        approveBorrowRequest(this.request.id, this.form.borrowDays).then(() => {
          this.$modal.msgSuccess("已同意借出，借阅记录已生成")
          this.visible = false
          this.$emit("success")
        }).catch(() => {
          // 失败提示由全局请求拦截器统一弹出（如库存不足、该申请已被处理、该申请缺囚号），
          // 这里不做处理，也不关闭弹窗，方便管理员调整借阅天数后重试
        }).then(() => {
          this.submitting = false
        })
      })
    },
    /**
     * 判断借阅天数是否在允许范围内
     * 范围取后端下发的上下限，避免这里再写一遍数字
     * @param {Number} days 借阅天数
     */
    isValidDays(days) {
      return !isNaN(days) && days >= this.borrowDayLimits.minDays && days <= this.borrowDayLimits.maxDays
    },
    /**
     * 在指定时间上偏移天数
     * @param {Date} base 基准时间
     * @param {Number} days 偏移天数
     */
    addDays(base, days) {
      const date = new Date(base.getTime())
      date.setDate(date.getDate() + days)
      return date
    },
    /**
     * 格式化为「yyyy-MM-dd HH:mm:ss」
     * @param {Date} date 时间
     */
    formatDateTime(date) {
      const pad = num => (num < 10 ? "0" + num : "" + num)
      return (
        date.getFullYear() +
        "-" + pad(date.getMonth() + 1) +
        "-" + pad(date.getDate()) +
        " " + pad(date.getHours()) +
        ":" + pad(date.getMinutes()) +
        ":" + pad(date.getSeconds())
      )
    },
    /**
     * 图片相对路径转可访问地址
     * @param {String} path 图片相对路径
     */
    imageUrl(path) {
      if (!path) {
        return ""
      }
      // 已经带协议头的（如字典里配的外链）直接用，其余拼上接口前缀走后端静态资源
      if (/^https?:\/\//.test(path)) {
        return path
      }
      return process.env.VUE_APP_BASE_API + path
    }
  }
}
</script>

<style scoped lang="scss">
/* 书籍信息只读区：浅底卡片 + 小封面，与下面的表单控件在视觉上区分开 */
.book-info {
  display: flex;
  align-items: center;
  padding: 8px;
  border-radius: 4px;
  background-color: #f5f7fa;

  &__cover {
    width: 48px;
    height: 48px;
    flex-shrink: 0;
    margin-right: 10px;
    border-radius: 4px;
    background-color: #ffffff;
  }

  &__text {
    min-width: 0;
  }

  &__name {
    color: #303133;
    font-weight: 600;
  }

  &__meta {
    margin-top: 4px;
    color: #909399;
    font-size: 12px;
  }

  &__shelf {
    margin-left: 12px;
  }
}

/* 囚号只读展示：用主文字色，与旁边的输入控件在观感上区分开 */
.prisoner-number {
  color: #303133;

  /* 缺囚号时的提示文案（仅历史数据会出现），用错误色提醒这条申请批不了 */
  &__warn {
    margin-left: 8px;
    color: #f56c6c;
    font-size: 12px;
  }
}

/* 借阅天数输入框后的说明文字 */
.day-hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

/* 应还时间预览，用主文字色强调 */
.return-time {
  color: #303133;
}
</style>
