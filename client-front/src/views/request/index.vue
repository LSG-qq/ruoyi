<template>
  <div class="request-page">
    <!-- 查询条件：只提供「申请状态」一个筛选，囚号由后端从令牌取，页面上没有也不该有 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.native.prevent>
        <el-form-item label="申请状态">
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

    <!-- 申请列表。只读：终端不能修改或删除申请，审批属于管理后台 -->
    <el-table v-loading="loading" :data="requestList" border stripe>
      <el-table-column label="书名" prop="bookName" min-width="180" show-overflow-tooltip />
      <el-table-column label="书架" prop="bookShelfCode" width="110" align="center">
        <template slot-scope="scope">{{ scope.row.bookShelfCode || '—' }}</template>
      </el-table-column>
      <el-table-column label="借阅天数" prop="borrowDays" width="110" align="center">
        <template slot-scope="scope">
          {{ scope.row.borrowDays ? scope.row.borrowDays + ' 天' : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="申请时间" prop="createdAt" width="180" align="center" />
      <el-table-column label="状态" width="120" align="center">
        <template slot-scope="scope">
          <el-tag :type="statusTagType(scope.row.status)">
            {{ statusMap[scope.row.status] || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理时间" width="180" align="center">
        <template slot-scope="scope">{{ scope.row.updatedAt || '—' }}</template>
      </el-table-column>
      <el-table-column label="处理人" width="120" align="center">
        <template slot-scope="scope">{{ scope.row.updatedByName || '—' }}</template>
      </el-table-column>

      <!-- 空态：Element UI 2.x 没有 el-empty，统一用 .empty-tip（见 app.scss） -->
      <template slot="empty">
        <div class="empty-tip">还没有提交过借阅申请</div>
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
import { listMyBorrowRequest, borrowRequestStatusOptions } from '@/api/client/borrowRequest'

/**
 * 我的借阅申请。
 *
 * <p>只能看自己的：囚号由后端从登录令牌解析，接口没有「查谁」这个参数，
 * 前端也就无从传错。这里刻意不加「借阅人」筛选框 —— 加了反而让人以为能查别人。</p>
 *
 * <p>状态下拉与列表里的状态标签同源：选项来自 /statusOptions，
 * 前端只负责给每个值配一个颜色。状态的中文标签不由前端维护，
 * 否则后端改文案（或加状态）前端就显示成英文值了。</p>
 *
 * <p>页面是**只读**的：终端不提供申请的修改与删除，「同意/拒绝」由管理员在后台完成，
 * 因此这里既没有操作列，也没有编辑弹窗。</p>
 */
export default {
  name: 'ClientBorrowRequest',

  data() {
    return {
      /** 表格加载中标志 */
      loading: false,

      /** 当前页的申请 */
      requestList: [],

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
    /**
     * 状态值 → 中文标签。
     *
     * <p>用 computed 而不是在每次渲染时线性查找：一张表可能有几十行，
     * 先把它摊平成对象，模板里就是一次哈希取值。</p>
     */
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
     * 查询我的借阅申请。
     *
     * <p>用 finally 复位 loading：失败时若不复位，表格会永久转圈。</p>
     */
    getList() {
      this.loading = true
      listMyBorrowRequest(this.queryParams).then(res => {
        this.requestList = res.rows
        this.total = res.total
      }).finally(() => {
        this.loading = false
      })
    },

    /**
     * 查询状态选项。
     *
     * <p>失败时静默处理：状态文案只是展示用的元数据，拿不到时列表照常显示
     * （状态列会回落成接口返回的原始值，见模板里的 `|| scope.row.status`），
     * 不该因为这一个辅助接口失败就把「我的申请」整页卡住。
     * 但不写 catch 会产生一个未处理的 Promise rejection，控制台报错、
     * 真正的故障被噪音掩盖，所以这个空 catch 是必须的。</p>
     */
    getStatusOptions() {
      borrowRequestStatusOptions().then(res => {
        this.statusOptions = res.data
      }).catch(() => {})
    },

    /**
     * 状态对应的标签颜色。
     *
     * <p>颜色是纯展示属性，后端不关心，所以放在前端 ——
     * 与上面「标签文案必须来自后端」并不矛盾：文案会变、颜色不会影响业务。</p>
     *
     * @param status 状态值，如 ready
     * @returns {string} el-tag 的 type，未知状态回落成 info（灰色）
     */
    statusTagType(status) {
      const types = {
        ready: 'warning',
        resolved: 'success',
        rejected: 'danger'
      }
      return types[status] || 'info'
    },

    /** 查询：条件变化后回到第 1 页，避免停在超范围的页码上 */
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
