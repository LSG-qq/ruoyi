<template>
  <div class="book-page">
    <!-- 查询条件：书名 + 类目（类目走 el-cascader，数据由后端下发扁平列表本地组树） -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="queryParams" @submit.native.prevent>
        <el-form-item label="书名">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入书名"
            clearable
            style="width: 210px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="类目">
          <el-cascader
            v-model="queryParams.categoryId"
            :options="categoryOptions"
            :props="cascaderProps"
            :disabled="categoryDisabled"
            clearable
            :placeholder="categoryPlaceholder"
            style="width: 210px"
            @change="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">查询</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!--
      图书卡片。终端多是触摸屏，卡片比表格更好点选；
      库存为 0 的书仍然展示（用户能知道这本书存在），只是按钮不可点。
    -->
    <div v-loading="loading" class="book-grid">
      <div v-if="!loading && bookList.length === 0" class="empty-tip">
        没有找到符合条件的图书
      </div>
      <el-row v-else :gutter="16">
        <el-col v-for="book in bookList" :key="book.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card class="book-card" shadow="hover">
            <!-- 封面：库内存的是 /profile/upload/... 相对路径，展示时再拼上接口前缀 -->
            <div class="book-cover">
              <img v-if="book.cover" :src="coverUrl(book.cover)" :alt="book.name">
              <div v-else class="no-cover"><i class="el-icon-picture-outline"></i></div>
            </div>
            <div class="book-info">
              <h4 class="book-name" :title="book.name">{{ book.name }}</h4>
              <p class="book-meta">作者：{{ book.author || '—' }}</p>
              <p class="book-meta">书架：{{ book.shelfCode || '—' }}</p>
              <p class="book-stock" :class="{ 'is-empty': book.stockQuantity <= 0 }">
                可借库存：{{ book.stockQuantity }}
              </p>
            </div>
            <el-button
              type="primary"
              class="apply-btn"
              :disabled="book.stockQuantity <= 0"
              @click="openApply(book)"
            >
              {{ book.stockQuantity > 0 ? '申请借阅' : '暂无可借' }}
            </el-button>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 分页。总数只有 1 页时也显示，便于用户看清一共有多少本书 -->
    <el-pagination
      v-show="total > 0"
      :current-page.sync="queryParams.pageNum"
      :page-size.sync="queryParams.pageSize"
      :page-sizes="[8, 12, 24, 48]"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @size-change="getList"
      @current-change="getList"
    />

    <!--
      申请弹窗。按规范单独封装为组件，父页面通过 ref 调用它的 open() 打开，
      父页面不再持有「当前申请哪本书 / 借多少天 / 是否提交中」这些状态。
    -->
    <apply-dialog ref="applyDialog" />
  </div>
</template>

<script>
import { listBook, listCategory } from '@/api/client/book'
import { handleTree } from '@/utils/ruoyi'
import ApplyDialog from './components/ApplyDialog'

/**
 * 图书浏览。
 *
 * <p>囚犯在这里做两件事：看有哪些书、对某本书提交借阅申请。
 * 「申请」是终端唯一的写操作，且已经被抽到 ApplyDialog 组件里，
 * 所以本页只负责查询与展示，是纯只读页面。</p>
 *
 * <p>几条刻意为之的地方：</p>
 * <ul>
 *   <li>库存为 0 时按钮直接禁用并显示「暂无可借」——后端也会拒，
 *       但让用户点下去再报错是糟糕的体验。</li>
 *   <li>借阅天数的上下限与默认值由弹窗组件从 /borrowDayLimits 取，前端**不写死数字**。
 *       否则后端把上限从 365 调成 90 之后，界面还允许填 300，提交才被拒。</li>
 *   <li>类目下拉用 el-cascader + 扁平数据本地组树。后端下发扁平列表，
 *       组树是纯展示行为，这一层不占用接口。</li>
 *   <li>类目取不到时禁用下拉，而不是丢一个空下拉在那儿让人误以为「没有类目」。</li>
 * </ul>
 */
export default {
  name: 'ClientBook',

  components: { ApplyDialog },

  data() {
    return {
      /** 列表加载中标志 */
      loading: false,

      /** 当前页的图书 */
      bookList: [],

      /** 符合条件的总条数 */
      total: 0,

      /** 类目下拉的树形数据 */
      categoryOptions: [],

      /** 类目接口是否失败过（失败则禁用下拉，避免用户对着空下拉反复点） */
      categoryFailed: false,

      /** 查询条件，pageNum/pageSize 由分页组件双向绑定 */
      queryParams: {
        pageNum: 1,
        pageSize: 8,
        name: null,
        categoryId: null
      },

      /** el-cascader 的字段映射；emitPath: false 让 v-model 是单个 id 而不是路径数组 */
      cascaderProps: {
        value: 'id',
        label: 'title',
        children: 'children',
        emitPath: false,
        checkStrictly: true
      }
    }
  },

  computed: {
    /** 没有类目可用时禁用下拉，并改用「类目加载失败」的提示文案 */
    categoryDisabled() {
      return this.categoryFailed || this.categoryOptions.length === 0
    },

    /** 类目下拉的占位文案：区分「还没加载出来」与「本来就不可用」 */
    categoryPlaceholder() {
      return this.categoryFailed ? '类目加载失败' : '全部类目'
    }
  },

  created() {
    this.getCategories()
    this.getList()
  },

  methods: {
    /**
     * 查询图书列表。
     *
     * <p>用 finally 复位 loading：请求失败时若不复位，表格会永久转圈，
     * 用户既看不到数据也不知道出了什么事。</p>
     */
    getList() {
      this.loading = true
      listBook(this.queryParams).then(res => {
        this.bookList = res.rows
        this.total = res.total
      }).finally(() => {
        this.loading = false
      })
    },

    /**
     * 查询类目并组装成树。
     *
     * <p>失败时必须显式标记出来：类目只是一个辅助筛选条件，
     * 接口挂了不该让整页不可用，但也不能静默留一个空下拉 ——
     * 那样用户会以为「系统里就是没有类目」，属于把故障说成业务事实。</p>
     */
    getCategories() {
      listCategory().then(res => {
        this.categoryOptions = handleTree(res.data, 'id', 'parentId', 'children')
        this.categoryFailed = false
      }).catch(() => {
        this.categoryOptions = []
        this.categoryFailed = true
      })
    },

    /** 封面地址：后端给的是 /profile/upload/... 相对路径，要走 dev-api 代理取图 */
    coverUrl(cover) {
      return process.env.VUE_APP_BASE_API + cover
    },

    /** 打开申请弹窗（状态都封在弹窗组件里，这里只把书传进去） */
    openApply(book) {
      this.$refs.applyDialog.open(book)
    },

    /** 查询：条件变化后必须回到第 1 页，否则可能停在超出范围的页码上 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },

    /** 重置查询条件 */
    resetQuery() {
      this.queryParams.name = null
      this.queryParams.categoryId = null
      this.handleQuery()
    }
  }
}
</script>

<style lang="scss" scoped>
.search-card {
  margin-bottom: var(--client-gap);
}

/* 列表区最小高度：首次加载时页面不会因为内容未到而塌陷，减少跳动感 */
.book-grid {
  min-height: 200px;
}

.book-card {
  margin-bottom: var(--client-gap);

  .book-cover {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 160px;
    margin-bottom: 12px;
    background-color: var(--client-bg-muted);
    border-radius: var(--client-radius);
    overflow: hidden;

    img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
    }

    /* 没有封面时的占位图标，避免卡片高度忽高忽低 */
    .no-cover {
      font-size: 40px;
      color: #c0c4cc;
    }
  }

  .book-name {
    margin: 0 0 var(--client-gap-sm);
    font-size: var(--client-font-md);
    font-weight: 600;
    /* 书名可能很长，单行省略并把全文放进 title，悬停可看 */
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .book-meta {
    margin: 4px 0;
    font-size: var(--client-font-sm);
    color: var(--client-text-regular);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* 库存：有货用成功色，为 0 时转成危险色，不用额度提示也能一眼看出来 */
  .book-stock {
    margin: var(--client-gap-sm) 0 0;
    font-size: var(--client-font-sm);
    color: var(--client-success);

    &.is-empty {
      color: var(--client-danger);
    }
  }

  .apply-btn {
    width: 100%;
    margin-top: 12px;
    /* 触摸屏上按钮要够高才好点 */
    min-height: var(--client-tap-height);
  }
}

.pagination {
  margin-top: var(--client-gap-sm);
  text-align: right;
}
</style>
