<template>
  <!--
    图书详情弹窗：左侧文字信息、右侧图片轮播。
    用弹窗而不是新路由，是因为终端只有三个页面、没有浏览器前进后退可用，
    跳走之后「回到原来那一屏」要靠自己记滚动位置与分页状态 —— 弹窗天然没有这个问题。
    居中大卡片（980px）在内容很少时也撑得住版面，不至于显得空。
  -->
  <el-dialog
    :visible.sync="visible"
    width="980px"
    top="6vh"
    custom-class="book-detail"
    append-to-body
    :show-close="false"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div v-loading="loading && !detail.name" class="detail">
      <!-- ------------------------------------------------------------------
           左侧：文字信息。撑满剩余宽度，长简介在内部滚动，
           不把弹窗越撑越高（否则按钮会被顶出屏幕）。
           ------------------------------------------------------------------ -->
      <div class="detail__info">
        <h2 class="detail__name">{{ detail.name || '—' }}</h2>

        <!-- 书名与作者之间的一条渐变小横线，作为唯一的装饰元素，克制但能撑起版面 -->
        <span class="detail__accent"></span>

        <p class="detail__author">
          <i class="el-icon-user"></i>
          <span>{{ authorText }}</span>
        </p>

        <h3 class="detail__section">内容简介</h3>
        <p class="detail__desc">{{ descriptionText }}</p>

        <div class="detail__actions">
          <!-- 没库存时同样置灰而不是隐藏，与列表卡片的口径保持一致：书还在，只是借完了 -->
          <el-button
            class="detail__btn"
            :disabled="!available"
            @click="$emit('apply', detail)"
          >
            {{ available ? '申请借阅' : '暂无可借' }}
          </el-button>
          <span class="detail__stock" :class="available ? 'is-available' : 'is-empty'">
            <i :class="available ? 'el-icon-circle-check' : 'el-icon-circle-close'"></i>
            {{ stockText }}
          </span>
        </div>
      </div>

      <!-- 右侧图片面板：底色是淡主色到灰的斜向渐变，把白色书封衬托出来 -->
      <div class="detail__media">
        <book-detail-gallery
          :images="detail.images"
          :cover="detail.cover"
          :title="detail.name"
          :active="visible"
        />
      </div>

      <button type="button" class="detail__close" title="关闭" @click="visible = false">
        <i class="el-icon-close"></i>
      </button>
    </div>
  </el-dialog>
</template>

<script>
import { getBookDetail } from '@/api/client/book'
import BookDetailGallery from './BookDetailGallery'

/**
 * 图书详情弹窗。
 *
 * <p>对外只暴露 {@link #open}，父页面通过 `ref` 调用（`this.$refs.detailDialog.open(book)`），
 * 与工程既有的弹窗套路一致 —— 打开所需的内部状态（加载中、当前这本书）全封在本组件里，
 * 父页面只关心「打开某一本书的详情」这一件事。</p>
 *
 * <p>图片轮播交给 {@link BookDetailGallery}：图片集的解析、自动轮播、手动切换、
 * 加载失败的剔除都是它自己的事。本组件只把「图片集 / 封面 / 书名」三个字符串传下去，
 * 不去关心它内部停在第几张。</p>
 *
 * <p><b>两段式渲染</b>：先用列表里那份数据把弹窗填上，再请求详情接口覆盖。
 * 列表数据里本来就有书名、作者、封面，先渲染能让弹窗「一点就出来」而不是先空一下；
 * 详情接口随后补齐简介与完整图片集，并保证看到的是库里的当前值
 * （用户停在列表页期间这本书可能被管理员改过或被下架）。</p>
 *
 * <p><b>不负责「能不能借」</b>：点「申请借阅」只把图书抛给父页面，
 * 是否登录、要不要跳登录页、申请弹窗怎么开都由父页面决定 ——
 * 与列表卡片的分工完全一致，详情弹窗不需要知道自己是在登录态下被打开的。</p>
 */
export default {
  name: 'BookDetailDialog',

  components: { BookDetailGallery },

  data() {
    return {
      /** 弹窗是否可见，仅本组件内部维护 */
      visible: false,

      /** 详情是否正在请求。首屏已用列表数据渲染，因此遮罩只在完全没数据时出现 */
      loading: false,

      /** 当前展示的图书详情，先来自列表行，再由详情接口覆盖 */
      detail: {}
    }
  },

  computed: {
    /** 是否还有可借库存 */
    available() {
      return (this.detail.stockQuantity || 0) > 0
    },

    /** 库存文案，与列表卡片同一口径 */
    stockText() {
      return this.available ? `可借 ${this.detail.stockQuantity} 本` : '暂无库存'
    },

    /** 作者，缺失时与列表卡片一致地落到「佚名」 */
    authorText() {
      return this.detail.author || '佚名'
    },

    /** 简介，缺失时给一句可读的说明，避免出现一整片空白 */
    descriptionText() {
      return this.detail.description || '暂无简介'
    }
  },

  methods: {
    /**
     * 打开弹窗（唯一的对外入口）。
     *
     * @param book 列表里的图书行，至少含 id；其余字段用于首屏立即渲染
     */
    open(book) {
      const source = book || {}
      this.detail = { ...source }
      this.visible = true
      this.getDetail(source.id)
    },

    /**
     * 请求详情并覆盖首屏数据。
     *
     * <p>失败时不把弹窗清空：列表里那份数据仍然可用（书名、作者、封面都在），
     * 只是简介与图片集可能不全。错误提示由全局拦截器统一弹出，这里不重复打扰。</p>
     *
     * @param id 图书ID
     */
    getDetail(id) {
      if (!id) {
        return
      }
      this.loading = true
      getBookDetail(id).then(res => {
        this.detail = { ...this.detail, ...(res.data || {}) }
      }).catch(() => {}).finally(() => {
        this.loading = false
      })
    },

    /**
     * 关闭后清空本次的数据。
     *
     * <p>不清的话下次打开会先闪一下上一本书的书名与封面，
     * 那是把「上一本书的残留」当成了「这一本书的加载结果」。
     * 轮播内部的重置由它自己的 watch 负责（换图就重建），这里不用管。</p>
     */
    handleClosed() {
      this.detail = {}
    }
  }
}
</script>

<style lang="scss" scoped>
// ---------------------------------------------------------------
// 弹窗外壳。表头与内边距都压掉，让内容区贴着卡片边缘铺满 ——
// 右侧图片面板要一直贴到圆角处，body 自带的那圈内边距会在这里留出白边。
// 这些选择器都在 element-ui 组件内部，普通选择器够不到，必须用 ::v-deep。
// ---------------------------------------------------------------
::v-deep .book-detail {
  overflow: hidden;
  border-radius: var(--client-radius-lg);
  box-shadow: 0 18px 48px 0 rgba(0, 0, 0, 0.18);

  .el-dialog__header {
    // 自带表头是空的（没有 title），留着只会多出一条 20px 的空白带
    display: none;
  }

  .el-dialog__body {
    padding: 0;
  }
}

// ---------------------------------------------------------------
// 左右两栏。右栏固定宽度，左栏吃掉剩余空间；
// min-height 让只有两行简介的书也不至于塌成一个扁条。
// ---------------------------------------------------------------
.detail {
  position: relative;
  display: flex;
  min-height: 500px;
}

.detail__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  // min-width: 0 是让下面简介的滚动生效的前提，缺了它 flex 子项会被长文本撑开
  min-width: 0;
  padding: 48px 40px 40px;
}

// 书名：详情里的主角，字号比列表卡片大两档，行高收紧一点更紧凑
.detail__name {
  margin: 0;
  font-size: var(--client-font-title);
  font-weight: 700;
  line-height: 1.35;
  color: var(--client-text-primary);
}

// 书名下方的渐变小横线：整块版面唯一的装饰，用来把标题与信息区分开
.detail__accent {
  display: block;
  width: 46px;
  height: 4px;
  margin: 14px 0 20px;
  background-image: linear-gradient(90deg, var(--client-primary) 0%, var(--client-primary-light) 100%);
  border-radius: var(--client-radius-pill);
}

.detail__author {
  display: flex;
  align-items: center;
  margin: 0;
  font-size: var(--client-font-md);
  color: var(--client-text-regular);

  i {
    margin-right: 8px;
    color: var(--client-text-secondary);
  }
}

// 「内容简介」小标题：比正文略小、颜色更淡，只做分区提示，不抢书名
.detail__section {
  margin: 30px 0 10px;
  font-size: var(--client-font-sm);
  font-weight: 500;
  color: var(--client-text-secondary);
  letter-spacing: 1px;
}

// 简介正文。长简介在本区域内滚动，而不是把整个弹窗越撑越高 ——
// 否则底部那颗「申请借阅」会被顶到屏幕外面，用户根本够不到。
.detail__desc {
  flex: 1;
  min-height: 60px;
  margin: 0;
  padding-right: 6px;
  overflow-y: auto;
  font-size: var(--client-font-base);
  line-height: 1.9;
  color: var(--client-text-regular);
  // 简介里的换行原样保留：管理员排版时的分段是有意义的
  white-space: pre-wrap;
}

// ---------------------------------------------------------------
// 操作区：主按钮 + 库存说明。
// 说明文字紧跟在按钮右侧，是因为按钮置灰时必须就地解释「为什么点不了」。
// ---------------------------------------------------------------
.detail__actions {
  display: flex;
  flex: none;
  align-items: center;
  margin-top: 32px;
}

// 主按钮：渐变胶囊 + 同色投影，与搜索框那颗按钮同一套视觉语言。
// 用 el-button 而不是原生 button，是为了直接拿到 disabled 的语义与键盘行为。
.detail__btn {
  min-width: 186px;
  height: 46px;
  padding: 0 30px;
  font-size: var(--client-font-md);
  font-weight: 500;
  letter-spacing: 1px;
  color: var(--client-bg-card);
  border: none;
  border-radius: var(--client-radius-pill);
  background-image: linear-gradient(
    135deg,
    var(--client-primary-light) 0%,
    var(--client-primary) 55%,
    var(--client-primary-dark) 100%
  );
  box-shadow: var(--client-shadow-primary);
  transition: filter 0.2s, transform 0.1s;

  // 用 :not(.is-disabled) 限定，否则禁用态也会跟着变亮
  &:not(.is-disabled):hover {
    color: var(--client-bg-card);
    filter: brightness(1.06);
  }

  // 触屏上「按下去」要有反馈，只靠 hover 是看不出来的
  &:not(.is-disabled):active {
    transform: scale(0.98);
  }

  // 禁用态：退回灰底，同时摘掉渐变与彩色投影，让「不可点」一眼可见
  &.is-disabled {
    color: var(--client-text-secondary);
    background-color: var(--client-bg-muted);
    background-image: none;
    box-shadow: none;
  }
}

.detail__stock {
  display: flex;
  align-items: center;
  margin-left: 18px;
  font-size: var(--client-font-sm);

  i {
    margin-right: 6px;
  }

  &.is-available {
    color: var(--client-success);
  }

  &.is-empty {
    color: var(--client-danger);
  }
}

// ---------------------------------------------------------------
// 右侧图片面板。一层很淡的主色到灰的斜向渐变，把白色书封衬托出来，
// 同时与左侧纯白内容区做出区分，不用分割线也能看出两栏。
// ---------------------------------------------------------------
.detail__media {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 384px;
  padding: 30px 0 26px;
  background-image: linear-gradient(160deg, var(--client-primary-soft) 0%, var(--client-bg-muted) 100%);
}

// ---------------------------------------------------------------
// 关闭按钮。做成浮在图片面板右上角的小圆片，比 element-ui 默认那个
// 贴边的灰叉更显眼，也不用额外占一行版面。
// ---------------------------------------------------------------
.detail__close {
  position: absolute;
  top: 14px;
  right: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--client-text-regular);
  cursor: pointer;
  background-color: var(--client-bg-card);
  border: none;
  border-radius: 50%;
  box-shadow: var(--client-shadow-card);
  transition: color 0.25s, transform 0.25s;

  &:hover {
    color: var(--client-primary);
    // 转 90 度：让「关闭」这个动作有一点反馈，又不至于晃眼
    transform: rotate(90deg);
  }
}
</style>
