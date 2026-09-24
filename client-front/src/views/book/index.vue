<template>
  <!--
    终端首页（图书浏览）。
    三区一列布局：顶部搜索、左侧类目树、右侧图书列表。
    整页高度固定为容器高度，滚动只发生在右侧列表内部 —— 这样搜索框与类目树
    在翻阅图书时始终留在视野里，不需要每次都滑回顶部去改条件。
  -->
  <div class="book-page">
    <!-- 搜索区：v-model 接输入框内容，search 事件表示「用户要求查询」 -->
    <book-search-bar
      v-model="keyword"
      class="book-page__search"
      :category-name="currentCategoryName"
      @search="handleSearch"
    />

    <div class="book-page__body">
      <!-- 类目树：只抛选中的 id，类目数据与查询都由本页负责 -->
      <book-category-tree
        class="book-page__aside"
        :categories="categories"
        :current-id="categoryId"
        :loading="categoryLoading"
        :failed="categoryFailed"
        @select="handleCategorySelect"
        @retry="getCategories"
      />

      <!-- 图书列表：滚动到底自动抛 load-more，由本页去取下一页 -->
      <book-list
        ref="bookList"
        class="book-page__main"
        :books="books"
        :loading="loading"
        :total="total"
        :finished="finished"
        @load-more="handleLoadMore"
        @detail="handleDetail"
        @apply="handleApply"
      />
    </div>

    <!--
      详情弹窗免登录挂载：详情接口与列表一样标了 @Anonymous，
      未登录的人点开一本书也要能看到简介与图片。
      它只在被 open() 时才发请求，挂在这里不会给匿名访问带来任何请求。
    -->
    <book-detail-dialog ref="detailDialog" @apply="handleApply" />

    <!--
      申请弹窗只在已登录时挂载。
      它 created 时就会请求 /borrowRequest/borrowDayLimits，而那是**需要登录**的接口；
      未登录时把它挂在页面上，首页一进来就会拿到一个 401，
      进而弹出全局的「登录状态已过期」确认框 —— 匿名浏览的用户会莫名其妙被拦一次。
    -->
    <apply-dialog v-if="isLoggedIn" ref="applyDialog" />
  </div>
</template>

<script>
import { listBook, listCategory } from '@/api/client/book'
import BookSearchBar from './components/SearchBar'
import BookCategoryTree from './components/CategoryTree'
import BookList from './components/BookList'
import BookDetailDialog from './components/BookDetailDialog'
import ApplyDialog from './components/ApplyDialog'

/** 每页图书数：一行 3 本，正好 3 行 */
const PAGE_SIZE = 9

/** 「全部」类目的 id，与 CategoryTree 的虚拟根节点、后端对 categoryId=0 的解释对齐 */
const ALL_CATEGORY_ID = 0

/**
 * 终端首页 —— 图书浏览。
 *
 * <p>本页面是**编排者**：自己不发请求给任何子组件，只做四件事 ——
 * 持有查询与分页状态、调接口、把结果传下去、把子组件抛上来的动作翻译成查询。
 * 搜索框、类目树、图书列表各自的展示逻辑都在 `components/` 下的独立组件里。</p>
 *
 * <p>几个刻意的设计：</p>
 * <ul>
 *   <li><b>免登录浏览</b>：图书列表、图书详情与类目接口在后端都标了 `@Anonymous`，未登录也能看。
 *       点「申请借阅」时才引导去登录，登录后会带着 redirect 回到本页。
 *       唯一的例外是那条申请接口 —— 申请人（囚号）要从登录令牌里取，匿名提交就没法确认是谁提的。</li>
 *   <li><b>分页是「累积」而不是「翻页」</b>：滚动到底取下一页并拼到已有列表后面。
 *       因此任何会换结果集的动作（搜索、切类目）都必须把页码归 1 并清空旧数据，
 *       否则新旧结果会混在一起 —— 这一条统一收敛在 getList(reset) 里。</li>
 *   <li><b>同名接口只请求一次</b>：重复点已选中的类目直接返回，不发无意义的请求。</li>
 * </ul>
 */
export default {
  name: 'ClientBook',

  components: { BookSearchBar, BookCategoryTree, BookList, BookDetailDialog, ApplyDialog },

  data() {
    return {
      /** 搜索框里的书名关键字（与子组件 v-model 绑定，输入即同步，回车才查询） */
      keyword: '',

      /** 当前选中的类目 id，0 表示「全部」 */
      categoryId: ALL_CATEGORY_ID,

      /** 扁平类目列表，交给类目树组件去组树 */
      categories: [],

      /** 类目加载中 */
      categoryLoading: false,

      /** 类目加载失败（失败时树上显示重试，而不是空树） */
      categoryFailed: false,

      /** 已加载的全部图书（跨页累积） */
      books: [],

      /** 符合条件的总条数 */
      total: 0,

      /** 列表加载中 */
      loading: false,

      /** 是否已经取完全部结果（没有下一页） */
      finished: false,

      /** 分页参数。pageSize 固定为 9，页码由 getList/handleLoadMore 维护 */
      pageNum: 1,
      pageSize: PAGE_SIZE
    }
  },

  computed: {
    /** 是否已登录：决定「申请借阅」是开弹窗还是去登录页 */
    isLoggedIn() {
      return !!this.$store.getters.token
    },

    /** 当前类目名称，用于搜索框的动态提示语 */
    currentCategoryName() {
      if (!this.categoryId) {
        return '全部'
      }
      const hit = this.categories.find(item => item.id === this.categoryId)
      // 找不到就按「全部」措辞，避免提示语出现 undefined
      return hit ? hit.title : '全部'
    }
  },

  created() {
    this.getCategories()
    this.getList(true)
  },

  methods: {
    /**
     * 查询类目（扁平列表），失败时标记出来让树上显示重试。
     *
     * <p>类目失败不影响图书列表 —— 用户仍然能浏览全馆图书，
     * 只是没法按类目筛。所以这里只标状态，不弹错误、也不中断页面。</p>
     */
    getCategories() {
      this.categoryLoading = true
      listCategory().then(res => {
        this.categories = res.data || []
        this.categoryFailed = false
      }).catch(() => {
        this.categories = []
        this.categoryFailed = true
      }).finally(() => {
        this.categoryLoading = false
      })
    },

    /**
     * 查询图书列表。
     *
     * @param reset true 表示这是一次新的查询（搜索/切类目/首次进入）：
     *              页码归 1 且丢弃已加载的旧数据；false 表示加载下一页，追加到已有列表后面
     */
    getList(reset) {
      if (reset) {
        this.pageNum = 1
        this.finished = false
      }
      this.loading = true
      listBook({
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        // 空串按「不带条件」处理，避免后端把空字符串当成「匹配书名里含空串」
        name: this.keyword || null,
        categoryId: this.categoryId || null
      }).then(res => {
        const rows = res.rows || []
        this.total = res.total || 0
        this.books = reset ? rows : this.books.concat(rows)
        // 两条「停止加载」的判据：本页没数据，或已加载数追平总数。
        // 前者是兜底 —— 一旦总数报得比实际条数大，只按总数判断会一直翻不完。
        this.finished = rows.length === 0 || this.books.length >= this.total
      }).catch(() => {
        // 请求失败时停止自动加载：错误提示由全局拦截器统一弹出，
        // 若不停下来，用户每滑一下就会再失败一次、再弹一次提示。
        this.finished = true
      }).finally(() => {
        this.loading = false
      })
    },

    /**
     * 搜索。关键字已经通过 v-model 同步进来，这里只负责重置并回到列表顶部。
     *
     * <p>回到顶部是必须的：结果集换了，滚动位置却留在原处，
     * 用户看到的是新结果的中段，很容易以为「搜了但页面没变」。</p>
     */
    handleSearch() {
      this.$refs.bookList.scrollToTop()
      this.getList(true)
    },

    /**
     * 切换类目。
     *
     * @param id 选中的类目 id，0 为「全部」
     */
    handleCategorySelect(id) {
      // 重复点当前类目不必重新请求：结果集不会变，只会白等一次网络往返
      if (id === this.categoryId) {
        return
      }
      this.categoryId = id
      this.$refs.bookList.scrollToTop()
      this.getList(true)
    },

    /** 滚动到列表底部：取下一页并追加 */
    handleLoadMore() {
      if (this.loading || this.finished) {
        return
      }
      this.pageNum += 1
      this.getList(false)
    },

    /**
     * 查看图书详情：把整行数据交给详情弹窗，由它自己去取最新的详情。
     *
     * <p>页面不持有「当前在看哪一本」这类状态：详情弹窗每次打开都是一本独立的书，
     * 关闭即清空。这样重新打开列表里的另一本，不会残留上一本的内容。</p>
     *
     * @param book 被点击的图书行（来自列表）
     */
    handleDetail(book) {
      this.$refs.detailDialog.open(book)
    },

    /**
     * 申请借阅。
     *
     * <p>列表卡片上的按钮与详情弹窗里的按钮最终都走到这里 ——
     * 「要不要登录」只在这一处判断，两个入口的准入条件不会跑偏。</p>
     *
     * <p>未登录时先确认再去登录页，并把当前地址作为 redirect 带上 ——
     * 登录成功后会回到本页，用户原来在看的类目与关键字都还在。</p>
     *
     * @param book 被申请的图书
     */
    handleApply(book) {
      if (!this.isLoggedIn) {
        this.$confirm('申请借阅需要先登录，是否现在去登录？', '需要登录', {
          confirmButtonText: '去登录',
          cancelButtonText: '再看看',
          type: 'warning'
        }).then(() => {
          this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } })
        }).catch(() => {
          // 用户选择继续浏览，什么都不做
        })
        return
      }
      this.$refs.applyDialog.open(book)
    }
  }
}
</script>

<style lang="scss" scoped>
.book-page {
  display: flex;
  flex-direction: column;
  /* 撑满内容区高度：整页不滚动，滚动交给右侧列表 */
  height: 100%;
  min-height: 0;
}

.book-page__search {
  flex: none;
}

.book-page__body {
  display: flex;
  flex: 1;
  gap: var(--client-gap);
  min-height: 0;
  margin-top: var(--client-gap);
}

/* 左侧类目树：固定宽度，树身自己滚 */
.book-page__aside {
  flex: none;
  width: 232px;
}

/* 右侧图书列表：吃掉剩余宽度；min-width: 0 防止长书名把栅格列撑宽 */
.book-page__main {
  flex: 1;
  min-width: 0;
}
</style>
