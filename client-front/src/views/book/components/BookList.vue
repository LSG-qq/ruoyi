<template>
  <!--
    图书列表区。
    v-infinite-scroll 挂在本元素上：Element 会从自身往上找第一个可滚动祖先，
    本元素自己就是（overflow-y: auto），所以「滚动到距离底部 120px」即可触发加载。
    immediate 保持默认 true —— 首屏数据不足一屏时（例如某个类目只有 3 本书），
    光靠滚动永远触发不了下一次加载，Element 的 MutationObserver 会在内容变化后
    重新判断一次，把「不够一屏就再取一页」这件事自动兜住。
  -->
  <section
    ref="scroller"
    v-loading="loading && books.length === 0"
    element-loading-text="正在检索图书…"
    class="book-list"
    v-infinite-scroll="handleReachBottom"
    :infinite-scroll-disabled="scrollDisabled"
    :infinite-scroll-distance="120"
  >
    <!-- 空态：区分「还没查」与「查了但没有」，这里只承担后者 -->
    <div v-if="!books.length && !loading" class="book-list__empty">
      <i class="el-icon-search"></i>
      <p class="book-list__empty-title">没有找到符合条件的图书</p>
      <span class="book-list__empty-tip">换个书名试试，或点左侧「全部」看看全馆藏书</span>
    </div>

    <!-- 一行三本。minmax(0, 1fr) 而非 1fr：防止卡片里的长文本把列撑宽 -->
    <div v-else class="book-list__grid">
      <book-card
        v-for="book in books"
        :key="book.id"
        :book="book"
        @detail="$emit('detail', $event)"
        @apply="$emit('apply', $event)"
      />
    </div>

    <!-- 底部状态条：让用户知道「在加载 / 已经到底了」，不用猜还能不能继续滑 -->
    <footer v-if="books.length" class="book-list__footer">
      <span v-if="loading"><i class="el-icon-loading"></i> 正在加载更多…</span>
      <span v-else-if="finished">已显示全部 {{ total }} 本，没有更多了</span>
      <span v-else>继续下滑查看更多</span>
    </footer>
  </section>
</template>

<script>
import BookCard from './BookCard'

/**
 * 图书列表区（滚动自动分页）。
 *
 * <p>组件本身**不发请求**，只做两件事：把 `books` 渲染成一行三本的卡片网格；
 * 滑到底部时抛 `load-more` 让父页面去取下一页。卡片上的两个动作（`detail` 看详情、
 * `apply` 申请借阅）也只是原样向上转发 —— 列表不判断登录态、不打开任何弹窗，
 * 那是页面的职责。分页状态（页码、总数、是否加载完）
 * 由父页面持有 —— 因为搜索、切换类目都会重置分页，这些动作只有父页面知道。</p>
 *
 * <p>页码累积（把新一页拼到已有列表后面）也在父页面做：本组件只管把拿到的数组画出来，
 * 拿到的就是「当前应该显示的全部图书」。这样组件不持有可变的分页游标，
 * 将来要在其它地方复用（例如「借阅排行」只看前 9 本）不用改这里。</p>
 */
export default {
  name: 'BookList',

  components: { BookCard },

  props: {
    /** 当前已加载的全部图书（父页面负责累加） */
    books: {
      type: Array,
      default: () => []
    },

    /** 是否正在加载 */
    loading: {
      type: Boolean,
      default: false
    },

    /** 符合条件的总条数，用于底部「已显示全部 N 本」 */
    total: {
      type: Number,
      default: 0
    },

    /** 是否已加载完（没有下一页） */
    finished: {
      type: Boolean,
      default: false
    }
  },

  computed: {
    /**
     * 是否停用滚动加载。
     *
     * <p>加载中或已到底都必须停用：不停用的话，Element 的滚动回调在一次滑动里
     * 会被反复触发，同一页会被请求多次 —— 列表里会出现重复的书。</p>
     */
    scrollDisabled() {
      return this.loading || this.finished
    }
  },

  methods: {
    /** 滚动到底部附近：交给父页面判断还能不能取下一页 */
    handleReachBottom() {
      if (this.scrollDisabled) {
        return
      }
      this.$emit('load-more')
    },

    /**
     * 回到列表顶部。
     *
     * <p>重新搜索或切换类目时由父页面调用：内容已经换了一批，
     * 若还停在原来的滚动位置，用户看到的会是新结果的中间一段，
     * 甚至以为「搜索结果变了但页面没动」。</p>
     */
    scrollToTop() {
      const scroller = this.$refs.scroller
      if (scroller) {
        scroller.scrollTop = 0
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.book-list {
  /* 撑满右侧栏高度并自己滚动；页面整体不滚动，搜索框与类目树因此始终可见 */
  height: 100%;
  min-height: 0;
  padding-right: 4px;
  overflow-y: auto;
}

.book-list__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--client-gap);
}

/*
  窄屏回落：终端一体机通常是 1080p 及以上，默认就是一行三本；
  这两档只是兜底，避免在小屏上把卡片挤成一条。
*/
@media (max-width: 1280px) {
  .book-list__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 880px) {
  .book-list__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.book-list__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 70px 20px;
  text-align: center;

  > i {
    margin-bottom: 16px;
    font-size: 46px;
    color: var(--client-border);
  }
}

.book-list__empty-title {
  margin: 0 0 var(--client-gap-sm);
  font-size: var(--client-font-md);
  color: var(--client-text-regular);
}

.book-list__empty-tip {
  font-size: var(--client-font-sm);
  color: var(--client-text-secondary);
}

.book-list__footer {
  padding: 18px 0 4px;
  font-size: var(--client-font-sm);
  color: var(--client-text-secondary);
  text-align: center;

  i {
    margin-right: 4px;
  }
}
</style>
