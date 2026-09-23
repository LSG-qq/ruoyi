<template>
  <!-- 借出图书弹窗：选书、填囚号、确认借出时间与应还时间 -->
  <el-dialog
    title="借出图书"
    :visible.sync="visible"
    width="540px"
    append-to-body
    :close-on-click-modal="false"
    @closed="reset"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="书籍" prop="bookId">
        <el-select
          v-model="form.bookId"
          placeholder="请选择要借出的书籍，可输入关键字搜索"
          filterable
          clearable
          :loading="bookLoading"
          style="width: 100%"
        >
          <el-option
            v-for="book in bookOptions"
            :key="book.id"
            :label="bookLabel(book)"
            :value="book.id"
            :disabled="isSoldOut(book)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="囚号" prop="prisonerNumber">
        <el-input
          v-model="form.prisonerNumber"
          placeholder="请输入囚号"
          maxlength="255"
          clearable
          @keyup.enter.native="submitForm"
        />
      </el-form-item>
      <el-form-item label="借出时间" prop="borrowTime">
        <el-date-picker
          v-model="form.borrowTime"
          type="datetime"
          placeholder="默认当前时间"
          value-format="yyyy-MM-dd HH:mm:ss"
          :picker-options="borrowTimePickerOptions"
          style="width: 100%"
          @change="handleBorrowTimeChange"
        />
      </el-form-item>
      <el-form-item label="应还时间" prop="returnTime">
        <el-date-picker
          v-model="form.returnTime"
          type="datetime"
          placeholder="默认借出时间后 30 天"
          value-format="yyyy-MM-dd HH:mm:ss"
          :picker-options="returnTimePickerOptions"
          style="width: 100%"
          @change="handleReturnTimeChange"
        />
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="借出后该书籍库存自动减 1；同一囚号对同一本书尚未归还时不允许重复借出。"
      />
    </el-form>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      <el-button @click="visible = false">取 消</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { listBorrowBookOptions, addBorrowRecord } from "@/api/ssk/borrowRecord"

/** 应还时间的默认借期天数，与后端 BorrowRecordBiz.DEFAULT_RETURN_DAYS 保持一致 */
const DEFAULT_RETURN_DAYS = 30

/** 借出时间允许往前补录的最大天数，与后端 BORROW_TIME_MAX_BACK_DAYS 保持一致 */
const BORROW_TIME_MAX_BACK_DAYS = 90

export default {
  name: "BorrowForm",
  // 书架号取自字典 book_shelfs，用于把 ssk_book.shelf_code 还原成字典标签
  dicts: ["book_shelfs"],
  data() {
    return {
      // 弹窗显示状态
      visible: false,
      // 书籍下拉加载中
      bookLoading: false,
      // 提交中
      submitting: false,
      // 书籍下拉数据
      bookOptions: [],
      // 使用者是否手动调整过应还时间，调整过之后不再随借出时间自动重算
      returnTimeEdited: false,
      // 表单数据
      form: {
        bookId: null,
        prisonerNumber: null,
        borrowTime: null,
        returnTime: null
      },
      // 校验规则
      rules: {
        bookId: [{ required: true, message: "请选择要借出的书籍", trigger: "change" }],
        prisonerNumber: [
          { required: true, message: "囚号不能为空", trigger: "blur" },
          { max: 255, message: "囚号长度不能超过255个字符", trigger: "blur" }
        ],
        borrowTime: [{ required: true, message: "请选择借出时间", trigger: "change" }],
        returnTime: [
          { required: true, message: "请选择应还时间", trigger: "change" },
          { validator: this.validateReturnTime, trigger: "change" }
        ]
      }
    }
  },
  computed: {
    // 借出时间只允许选近 90 天内到今天，与服务端校验保持一致
    borrowTimePickerOptions() {
      const todayEnd = new Date()
      todayEnd.setHours(23, 59, 59, 999)
      const earliest = new Date()
      earliest.setDate(earliest.getDate() - BORROW_TIME_MAX_BACK_DAYS)
      earliest.setHours(0, 0, 0, 0)
      return {
        disabledDate(time) {
          return time.getTime() > todayEnd.getTime() || time.getTime() < earliest.getTime()
        }
      }
    },
    // 应还时间不得早于借出时间当天
    returnTimePickerOptions() {
      const borrowDay = this.parseDateTime(this.form.borrowTime)
      if (!borrowDay) {
        return {}
      }
      const borrowDayStart = new Date(borrowDay.getTime())
      borrowDayStart.setHours(0, 0, 0, 0)
      return {
        disabledDate(time) {
          return time.getTime() < borrowDayStart.getTime()
        }
      }
    }
  },
  methods: {
    /**
     * 打开弹窗
     * 借出时间默认当前时间，应还时间默认借出时间后 30 天，两者都允许在本月范围内自行调整。
     */
    open() {
      this.resetFormData()
      const now = new Date()
      this.form.borrowTime = this.formatDateTime(now)
      this.form.returnTime = this.formatDateTime(this.addDays(now, DEFAULT_RETURN_DAYS))
      this.visible = true
      this.$nextTick(() => {
        if (this.$refs.form) {
          this.$refs.form.clearValidate()
        }
      })
      // 每次打开都重新拉取书籍与库存，避免借出多本后库存显示滞后
      this.loadBookOptions()
    },
    /** 查询书籍下拉数据 */
    loadBookOptions() {
      this.bookLoading = true
      listBorrowBookOptions().then(response => {
        this.bookOptions = response.data || []
        this.bookLoading = false
      }).catch(() => {
        // 失败提示由全局请求拦截器统一弹出，这里只保证不残留加载状态
        this.bookOptions = []
        this.bookLoading = false
      })
    },
    /** 关闭后还原表单，避免下次打开残留上一次的输入 */
    reset() {
      this.resetFormData()
      this.bookOptions = []
      this.bookLoading = false
      this.submitting = false
    },
    /** 清空表单与内部标记 */
    resetFormData() {
      this.form = {
        bookId: null,
        prisonerNumber: null,
        borrowTime: null,
        returnTime: null
      }
      this.returnTimeEdited = false
    },
    /**
     * 借出时间变化时重算应还时间
     * 使用者已经手动改过应还时间的话就不再覆盖，避免把调整结果冲掉。
     * @param {String} val 借出时间文本
     */
    handleBorrowTimeChange(val) {
      if (this.returnTimeEdited) {
        return
      }
      const borrowTime = this.parseDateTime(val)
      this.form.returnTime = borrowTime
        ? this.formatDateTime(this.addDays(borrowTime, DEFAULT_RETURN_DAYS))
        : null
    },
    /** 标记应还时间已被手动调整 */
    handleReturnTimeChange() {
      this.returnTimeEdited = true
    },
    /**
     * 校验应还时间必须晚于借出时间
     * @param {Object} rule     校验规则
     * @param {String} value    应还时间文本
     * @param {Function} callback 回调
     */
    validateReturnTime(rule, value, callback) {
      const returnTime = this.parseDateTime(value)
      const borrowTime = this.parseDateTime(this.form.borrowTime)
      if (returnTime && borrowTime && returnTime.getTime() <= borrowTime.getTime()) {
        callback(new Error("应还时间必须晚于借出时间"))
        return
      }
      callback()
    },
    /**
     * 判断书籍是否已无可借库存
     * @param {Object} book 书籍数据
     */
    isSoldOut(book) {
      return !book || !book.stockQuantity || Number(book.stockQuantity) <= 0
    },
    /**
     * 拼接书籍下拉的显示文本
     * @param {Object} book 书籍数据
     */
    bookLabel(book) {
      const parts = [book.name]
      if (book.author) {
        parts.push(book.author)
      }
      parts.push(this.shelfLabel(book.shelfCode))
      // 无库存的书仍然列出但置灰，让使用者看得出「书存在，只是被借完了」
      parts.push(this.isSoldOut(book) ? "无库存" : "库存 " + book.stockQuantity)
      return parts.join(" ｜ ")
    },
    /**
     * 书架号按字典 book_shelfs 显示，字典中查不到时回退为原始值
     * @param {String} code 书架号
     */
    shelfLabel(code) {
      if (!code) {
        return "未设书架"
      }
      const options = (this.dict && this.dict.type && this.dict.type.book_shelfs) || []
      const hit = options.find(item => String(item.value) === String(code))
      return hit ? hit.label : code
    },
    /** 提交借出 */
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        this.submitting = true
        addBorrowRecord({
          bookId: this.form.bookId,
          prisonerNumber: this.form.prisonerNumber,
          borrowTime: this.form.borrowTime,
          returnTime: this.form.returnTime
        }).then(() => {
          this.$modal.msgSuccess("借出成功")
          this.visible = false
          this.$emit("success")
        }).catch(() => {
          // 失败提示由全局请求拦截器统一弹出
        }).then(() => {
          this.submitting = false
        })
      })
    },
    /**
     * 把 "yyyy-MM-dd HH:mm:ss" 文本解析为 Date
     * 统一把 "-" 换成 "/"，兼容仅支持该格式的浏览器。
     * @param {String} text 时间文本
     */
    parseDateTime(text) {
      if (!text) {
        return null
      }
      const date = new Date(String(text).replace(/-/g, "/"))
      return isNaN(date.getTime()) ? null : date
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
     * 格式化为 el-date-picker 的 value-format 所需文本
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
    }
  }
}
</script>
