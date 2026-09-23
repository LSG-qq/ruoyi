<template>
  <div class="record-page">
    <!-- 查询条件：只按借阅状态筛选；囚号不可能作为筛选项（后端按令牌等值匹配） -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.native.prevent>
        <el-form-item label="借阅状态">
          <el-select
            v-model="queryParams.status"
            placeholder="全部状态"
            clearable
            style="width: 180px"
            @change="handleQuery"
          >
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">查询</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!--
      借阅记录。刻意**没有**操作列：借出与归还都由管理员在后台完成，
      终端不提供这两个接口，前端也就不放按钮 —— 免得出现「按钮点了没反应」的困惑。
    -->
    <el-table v-loading="loading" :data="recordList" border stripe>
      <el-table-column label="书名" prop="bookName" min-width="180" show-overflow-tooltip />
      <el-table-column label="书架" prop="bookShelfCode" width="110" align="center">
        <template slot-scope="scope">{{ scope.row.bookShelfCode || '—' }}</template>
      </el-table-column>
      <el-table-column label="借出时间" prop="borrowTime" width="180" align="center" />
      <el-table-column label="应还时间" prop="returnTime" width="180" align="center" />
      <el-table-column label="实际归还" width="180" align="center">
        <template slot-scope="scope">{{ scope.row.actualReturnTime || '未归还' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template slot-scope="scope">
          <el-tag :type="statusTagType(scope.row.status)">
            {{ statusMap[scope.row.status] || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>

      <!-- 空态：Element UI 2.x 没有 el-empty，统一用 .empty-tip（见 app.scss） -->
      <template slot="empty">
        <div class="empty-tip">还没有借阅中的图书</div>
      </template>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-show="total > 0"
      :current-page.sync="queryParams.pageNum"
      :page-size.sync="queryParams.pageSize"
      :page-sizes="[10, 20, 50]"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @size-change="getList"
      @current-change="getList"
    />
  </div>
</template>

<script>
import { listMyBorrowRecord, borrowRecordStatusOptions } from '@/api/client/borrowRecord'

/**
 * 我的借阅。
 *
 * <p>只读页面：囚犯能看到自己借了哪些书、什么时候该还，但**不能**在这里借书或还书 ——
 * 借出与归还都由管理员在后台操作。终端不提供这两个接口，前端也就不放这两个按钮。</p>
 *
 * <p>状态（借出中 / 已逾期 / 已归还）由后端根据「实际归还时间是否为空、应还时间是否已过」
 * 计算得出，不是表里的一个字段；筛选同样在后端完成。
 * 前端只做两件事：把状态值翻成中文标签、给标签配颜色。</p>
 *
 * <p>列名与后端字段的对应关系容易记错，列在这儿备查：`borrowTime` 是借出时间、
 * `returnTime` 是**应还**时间、`actualReturnTime` 才是真正归还的时间（可能为空）。</p>
 */
export default {
  name: 'ClientBorrowRecord',

  data() {
    return {
      /** 表格加载中标志 */
      loading: false,

      /** 当前页的借阅记录 */
      recordList: [],

      /** 符合条件的总条数 */
      total: 0,

      /** 状态下拉选项，形如 [{ value, label }] */
      statusOptions: [],

      /** 查询条件，pageNum/pageSize 由分页组件双向绑定 */
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        status: null
      }
    }
  },

  computed: {
    /** 状态值 → 中文标签，先摊平成对象，模板里就是一次哈希取值 */
    statusMap() {
      const map = {}
      this.statusOptions.forEach(item => {
        map[item.value] = item.label
      })
      return map
    }
  },

  created() {
    this.getStatusOptions()
    this.getList()
  },

  methods: {
    /**
     * 查询我的借阅记录。
     *
     * <p>用 finally 复位 loading，失败时表格不会永久转圈。</p>
     */
    getList() {
      this.loading = true
      listMyBorrowRecord(this.queryParams).then(res => {
        this.recordList = res.rows
        this.total = res.total
      }).finally(() => {
        this.loading = false
      })
    },

    /**
     * 查询状态选项。
     *
     * <p>与「我的申请」页处理方式一致：失败时静默，列表照常展示
     * （状态列回落成原始值），但必须写 catch —— 否则会产生未处理的 Promise rejection，
     * 控制台里真正的故障会被噪声掩盖。</p>
     */
    getStatusOptions() {
      borrowRecordStatusOptions().then(res => {
        this.statusOptions = res.data
      }).catch(() => {})
    },

    /**
     * 状态对应的标签颜色。
     *
     * <p>逾期用红色、借出中用蓝色、已归还用灰色 —— 越接近「需要注意」越显眼。
     * 未知状态回落成 info，不至于没颜色。</p>
     *
     * @param status 状态值，如 borrowing
     * @returns {string} el-tag 的 type
     */
    statusTagType(status) {
      const types = {
        borrowing: 'primary',
        overdue: 'danger',
        returned: 'info'
      }
      return types[status] || 'info'
    },

    /** 查询：条件变化后回到第 1 页 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },

    /** 重置查询条件 */
    resetQuery() {
      this.queryParams.status = null
      this.handleQuery()
    }
  }
}
</script>

<style lang="scss" scoped>
.search-card {
  margin-bottom: var(--client-gap);
}

.pagination {
  margin-top: var(--client-gap);
  text-align: right;
}
</style>
