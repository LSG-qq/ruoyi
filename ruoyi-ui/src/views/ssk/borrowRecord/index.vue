<template>
  <div class="app-container">
    <!-- 搜索条件：囚号、书籍名称、借阅状态 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="囚号" prop="prisonerNumber">
        <el-input
          v-model="queryParams.prisonerNumber"
          placeholder="请输入囚号"
          clearable
          style="width: 180px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="书籍名称" prop="bookName">
        <el-input
          v-model="queryParams.bookName"
          placeholder="请输入书籍名称"
          clearable
          style="width: 180px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="借阅状态" prop="status">
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

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleBorrow"
          v-hasPermi="['ssk:borrowRecord:borrow']"
        >借出图书</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="recordList">
      <el-table-column label="囚号" align="center" prop="prisonerNumber" width="120" :show-overflow-tooltip="true" />
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
      <el-table-column label="书籍名称" align="center" prop="bookName" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="作者" align="center" prop="bookAuthor" width="110" :show-overflow-tooltip="true" />
      <el-table-column label="书架号" align="center" width="100">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.book_shelfs" :value="scope.row.bookShelfCode" />
        </template>
      </el-table-column>
      <el-table-column label="借出时间" align="center" prop="borrowTime" width="160" />
      <el-table-column label="应还时间" align="center" prop="returnTime" width="160">
        <template slot-scope="scope">
          <!-- 逾期未还的应还时间标红，便于一眼看出超期记录 -->
          <span :class="{ 'text-overdue': scope.row.status === 'overdue' }">{{ scope.row.returnTime }}</span>
        </template>
      </el-table-column>
      <el-table-column label="实际归还时间" align="center" prop="actualReturnTime" width="160">
        <template slot-scope="scope">{{ scope.row.actualReturnTime || "-" }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template slot-scope="scope">
          <el-tag :type="statusInfo(scope.row.status).type">{{ statusInfo(scope.row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="100" fixed="right" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-refresh-left"
            :disabled="scope.row.status === 'returned'"
            @click="handleReturn(scope.row)"
            v-hasPermi="['ssk:borrowRecord:return']"
          >还书</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 借出图书弹窗 -->
    <borrow-form ref="borrowForm" @success="getList" />
  </div>
</template>

<script>
import { listBorrowRecord, returnBorrowRecord } from "@/api/ssk/borrowRecord"
import BorrowForm from "./components/BorrowForm"

/** 借阅状态下拉与标签的显示配置，value 与后端 BorrowRecordBiz 中的状态常量保持一致 */
const STATUS_MAP = {
  borrowing: { label: "借出中", type: "" },
  returned: { label: "已归还", type: "success" },
  overdue: { label: "已逾期", type: "danger" }
}

export default {
  name: "BorrowRecord",
  components: { BorrowForm },
  // 书架号取自字典 book_shelfs
  dicts: ["book_shelfs"],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 借阅记录表格数据
      recordList: [],
      // 状态下拉选项
      statusOptions: [
        { value: "borrowing", label: "借出中" },
        { value: "returned", label: "已归还" },
        { value: "overdue", label: "已逾期" }
      ],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        // 囚号，模糊匹配
        prisonerNumber: null,
        // 书籍名称，模糊匹配 ssk_book.name
        bookName: null,
        // 借阅状态，为空表示全部
        status: null
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询借阅记录列表 */
    getList() {
      this.loading = true
      listBorrowRecord(this.queryParams).then(response => {
        this.recordList = response.rows
        this.total = response.total
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      // resetForm 会把 queryParams 上带 prop 的字段复位成初始值（均为 null），无需再手工清空
      this.resetForm("queryForm")
      this.handleQuery()
    },
    /** 借出图书 */
    handleBorrow() {
      this.$refs.borrowForm.open()
    },
    /**
     * 还书操作
     * 归还时间固定取当前系统时间，因此这里明确告知使用者，并要求二次确认。
     * @param {Object} row 当前行数据
     */
    handleReturn(row) {
      const tip = "是否确认归还囚号「" + row.prisonerNumber + "」借阅的《" + row.bookName + "》？"
        + "归还时间将取当前系统时间，归还后该书籍库存自动加 1。"
      this.$modal.confirm(tip).then(() => {
        return returnBorrowRecord(row.id)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("归还成功")
      }).catch(() => {})
    },
    /**
     * 借阅状态的显示文本与标签样式
     * @param {String} status 状态值
     */
    statusInfo(status) {
      return STATUS_MAP[status] || { label: "-", type: "info" }
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

.text-overdue {
  color: #f56c6c;
  font-weight: 600;
}
</style>
