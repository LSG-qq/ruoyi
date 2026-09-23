<template>
  <div class="app-container">
    <!-- 搜索条件：书籍名称、申请状态（默认只看待处理） -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="书籍名称" prop="bookName">
        <el-input
          v-model="queryParams.bookName"
          placeholder="请输入书籍名称"
          clearable
          style="width: 180px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="申请状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏：只保留若依自带的 right-toolbar（刷新图标 + 显隐搜索区），不再另加刷新按钮 -->
    <el-row :gutter="10" class="mb8">
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 申请列表：展示书籍信息与书架号，便于管理员按书架号去取书 -->
    <el-table v-loading="loading" :data="requestList">
      <el-table-column label="封面" align="center" width="76">
        <template slot-scope="scope">
          <el-image
            v-if="scope.row.bookCover"
            :src="imageUrl(scope.row.bookCover)"
            :preview-src-list="[imageUrl(scope.row.bookCover)]"
            fit="contain"
            class="book-cover"
          />
          <span v-else class="book-cover__empty">-</span>
        </template>
      </el-table-column>
      <!-- 囚号：申请人的标识，由终端在提交申请时写入，后台只读。
           放在书籍信息之前，是为了让一行先回答「谁申请的」，再看「借的是哪本书」 -->
      <el-table-column label="囚号" align="center" prop="prisonerNumber" width="120" :show-overflow-tooltip="true" />
      <el-table-column label="书籍名称" align="center" prop="bookName" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="作者" align="center" prop="bookAuthor" width="110" :show-overflow-tooltip="true" />
      <!-- 书架号是取书时的直接依据，单独成列并按字典 book_shelfs 回显 -->
      <el-table-column label="书架号" align="center" width="100">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.book_shelfs" :value="scope.row.bookShelfCode" />
        </template>
      </el-table-column>
      <el-table-column label="申请时间" align="center" prop="createdAt" width="160" />
      <el-table-column label="状态" align="center" width="100">
        <template slot-scope="scope">
          <el-tag :type="statusInfo(scope.row.status).type">{{ statusInfo(scope.row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <!-- 审核人 / 审核时间：审批类数据要能看出是谁在什么时候处理的，两者都留空表示尚未处理 -->
      <el-table-column label="审核人" align="center" prop="updatedByName" width="110">
        <template slot-scope="scope">{{ scope.row.updatedByName || "-" }}</template>
      </el-table-column>
      <el-table-column label="审核时间" align="center" prop="updatedAt" width="160">
        <template slot-scope="scope">{{ scope.row.updatedAt || "-" }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="140" fixed="right" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <!-- 只有「待处理」的申请可以审核，已处理过的按钮置灰，避免点了才报错 -->
          <el-button
            size="mini"
            type="text"
            icon="el-icon-check"
            :disabled="!isPending(scope.row)"
            @click="handleApprove(scope.row)"
            v-hasPermi="['ssk:borrowRequest:approve']"
          >同意</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-close"
            :disabled="!isPending(scope.row)"
            @click="handleReject(scope.row)"
            v-hasPermi="['ssk:borrowRequest:reject']"
          >拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页条：每页条数与页码变化都会重新拉取列表 -->
    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 同意申请弹窗：核对囚号、填写借阅天数，确认后视为同意借出 -->
    <approve-form ref="approveForm" @success="getList" />
  </div>
</template>

<script>
/**
 * 借阅申请管理页面
 *
 * 用于查看囚犯在终端发起的借阅申请，并逐条审核：
 *   同意 —— 弹出确认框核对囚号、填写借阅天数，确认后由后端生成借阅记录并扣减库存；
 *   拒绝 —— 二次确认后仅把申请置为已拒绝，不产生借阅记录。
 * 申请由终端写入数据库，本页面不提供新增、修改与删除入口。
 *
 * 状态的取值与中文文案来自后端 /ssk/borrowRequest/statusOptions（派生自 BorrowRequestStatus 枚举），
 * 前端只额外维护标签配色与一份请求失败时的兜底文案，不另立一套状态清单。
 */
import { listBorrowRequest, listBorrowRequestStatusOptions, rejectBorrowRequest } from "@/api/ssk/borrowRequest"
import ApproveForm from "./components/ApproveForm"

/** 状态标签的配色，只影响观感；状态取值与文案一律以后端返回为准 */
const STATUS_TAG_TYPE = {
  ready: "warning",
  resolved: "success",
  rejected: "danger"
}

/** 「待处理」状态值，用于判断申请能否被审核，与后端 BorrowRequestStatus.READY 保持一致 */
const STATUS_READY = "ready"

/**
 * 状态下拉的兜底选项（含首项「全部」）
 *
 * 正常情况下选项整体来自后端接口，前端不重复维护状态清单。但接口一旦失败或返回空数组，
 * 下拉里就没有「待处理」这一项，el-select 会把当前值原样显示成英文 `ready`，
 * 表格状态列也会跟着变成英文原值 —— 给终端管理员看英文不可接受，所以留这份最小兜底。
 * 仅在接口失败或返回空时使用，取值必须与后端 BorrowRequestStatus 枚举逐一对应。
 * 「全部」属于展示层概念（值为空表示不带该状态条件），不在枚举里。
 */
const FALLBACK_STATUS_OPTIONS = [
  { value: "", label: "全部" },
  { value: "ready", label: "待处理" },
  { value: "resolved", label: "已同意" },
  { value: "rejected", label: "已拒绝" }
]

/** 下拉选项首项固定为「全部」，后端只返回枚举项，这一项由前端补 */
const ALL_STATUS_OPTIONS = [{ value: "", label: "全部" }]

export default {
  name: "BorrowRequest",
  components: { ApproveForm },
  // 书架号取自字典 book_shelfs，申请状态不走字典（由后端枚举统一下发选项）
  dicts: ["book_shelfs"],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 借阅申请表格数据
      requestList: [],
      // 状态下拉选项：先用本地兜底渲染，接口回来后整体替换，避免首屏把 ready 显示成英文
      statusOptions: FALLBACK_STATUS_OPTIONS.slice(),
      // 查询参数，状态默认选中「待处理」，与管理员日常待办一致
      queryParams: {
        // 当前页码
        pageNum: 1,
        // 每页条数
        pageSize: 10,
        // 书籍名称，模糊匹配 ssk_book.name
        bookName: null,
        // 申请状态，为空表示全部
        status: STATUS_READY
      }
    }
  },
  created() {
    // 选项与列表互不依赖，并行加载；选项失败时下拉只剩「全部」，列表仍可用
    this.loadStatusOptions()
    this.getList()
  },
  methods: {
    /** 查询借阅申请列表 */
    getList() {
      this.loading = true
      listBorrowRequest(this.queryParams).then(response => {
        this.requestList = response.rows
        this.total = response.total
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    /**
     * 加载状态下拉选项
     * 选项由后端枚举派生，前端只是原样使用；拿不到时保持本地兜底，
     * 不清空下拉（清空会让 el-select 与表格状态列都退回英文原值）。
     */
    loadStatusOptions() {
      listBorrowRequestStatusOptions().then(response => {
        const options = response.data || []
        // 返回空数组时同样视作「没拿到」，继续用兜底，避免下拉只剩「全部」
        this.statusOptions = options.length
          ? ALL_STATUS_OPTIONS.concat(options)
          : FALLBACK_STATUS_OPTIONS.slice()
      }).catch(() => {
        // 失败提示由全局请求拦截器统一弹出，这里只负责把下拉维持在可用的兜底状态
        this.statusOptions = FALLBACK_STATUS_OPTIONS.slice()
      })
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      // resetForm 会把带 prop 的字段复位成初始值（状态回到「待处理」，与进页面时一致）
      this.resetForm("queryForm")
      this.handleQuery()
    },
    /** 同意申请：打开借阅表单窗口，确认后由后端生成借阅记录 */
    handleApprove(row) {
      this.$refs.approveForm.open(row)
    },
    /** 拒绝申请：二次确认后调整申请状态 */
    handleReject(row) {
      this.$modal.confirm("确认拒绝本次借阅申请吗？", "拒绝《" + row.bookName + "》的借阅申请").then(() => {
        return rejectBorrowRequest(row.id)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("已拒绝该借阅申请")
      }).catch(() => {})
    },
    /**
     * 判断申请是否仍可审核
     * @param {Object} row 当前行数据
     */
    isPending(row) {
      return row.status === STATUS_READY
    },
    /**
     * 状态的显示文本与标签样式
     * 文案取自后端下发的选项，查不到时回退成状态原值，便于暴露脏数据
     * @param {String} status 状态值
     */
    statusInfo(status) {
      const hit = this.statusOptions.find(item => item.value === status)
      return {
        label: hit ? hit.label : status || "-",
        type: STATUS_TAG_TYPE[status] || "info"
      }
    },
    /**
     * 图片相对路径转可访问地址
     * @param {String} path 图片相对路径
     */
    imageUrl(path) {
      if (!path) {
        return ""
      }
      if (/^https?:\/\//.test(path)) {
        return path
      }
      return process.env.VUE_APP_BASE_API + path
    }
  }
}
</script>

<style scoped lang="scss">
.book-cover {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  background-color: #f5f7fa;

  &__empty {
    color: #c0c4cc;
  }
}
</style>
