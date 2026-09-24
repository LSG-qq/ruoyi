<template>
  <!--
    图片轮播。容器取满外层面板宽度，书封舞台（300×420）在容器里居中 ——
    这样左右各留出约 42px 给切换箭头，箭头不会压在图片上。
    箭头只在两张以上时才出现（arrow='always' / 'never'）：只有一张图时 loop 会让
    左右箭头都跳回同一张，留着两个点了没反应的按钮，比没有箭头更让人困惑。
  -->
  <div class="gallery">
    <el-carousel
      v-if="slides.length"
      ref="carousel"
      class="gallery__carousel"
      height="420px"
      :arrow="slides.length > 1 ? 'always' : 'never'"
      indicator-position="none"
      :autoplay="autoPlay"
      :interval="4500"
      @change="handleChange"
    >
      <el-carousel-item v-for="src in slides" :key="src">
        <div class="gallery__item">
          <div class="gallery__stage">
            <!--
              contain 而非 cover：书的封面常有标题压在边上，
              裁掉一点就可能把书名切没，因此宁可留白也要把整张图展示全。
            -->
            <img :src="fullUrl(src)" :alt="title" @error="markBroken(src)">
          </div>
        </div>
      </el-carousel-item>
    </el-carousel>

    <!-- 一张图都没有（或全部加载失败）时的占位，撑住同一块版面高度 -->
    <div v-else class="gallery__item">
      <div class="gallery__stage gallery__stage--empty">
        <i class="el-icon-picture-outline"></i>
        <span>暂无图片</span>
      </div>
    </div>

    <!--
      手动切换的另一种入口：点圆点直接跳到第几张。
      el-carousel 自带的指示器是白色横条，在浅色面板上看不清，所以关掉它自己画一套；
      当前项拉长成胶囊（宽度动画），不用变色也能一眼看出停在第几张。
    -->
    <div v-if="slides.length > 1" class="gallery__dots">
      <button
        v-for="(src, index) in slides"
        :key="src"
        type="button"
        class="gallery__dot"
        :class="{ 'is-active': index === activeIndex }"
        :title="`第 ${index + 1} 张`"
        @click="goTo(index)"
      ></button>
    </div>
  </div>
</template>

<script>
/**
 * 图书图片轮播（自动播放 + 手动切换）。
 *
 * <p>只管图片这一件事：解析图片集、渲染轮播、切换、以及把加载失败的图摘掉。
 * 书名、简介、要不要登录这些都与它无关，因此本组件不接收整个图书对象，
 * 只要三个字符串（图片集、封面、书名）—— 依赖面小，将来别的页面要放同样的
 * 轮播（例如后台图书详情预览）可以直接复用。</p>
 *
 * <p><b>为什么自己画指示器</b>：element-ui 的轮播指示器是一排白色横条，
 * 它假设背景是深色的。本组件放在浅色面板上，白条几乎看不见，
 * 于是关掉自带指示器、自己画一排圆点，当前项拉长成胶囊。</p>
 *
 * <p><b>加载失败的图直接摘掉</b>，而不是原地留一个破图占位：
 * 否则轮播会在一张坏图上停留 4.5 秒才切走，看起来像卡住了。</p>
 */
export default {
  name: 'BookDetailGallery',

  props: {
    /** 图片集，多张以英文逗号分隔（与库里 images 字段同格式） */
    images: {
      type: String,
      default: ''
    },

    /** 封面路径。没有图片集时用它兜底 */
    cover: {
      type: String,
      default: ''
    },

    /** 图片的替代文本，通常是书名 */
    title: {
      type: String,
      default: ''
    },

    /** 外层弹窗是否打开。关掉之后没必要继续自动轮播 */
    active: {
      type: Boolean,
      default: false
    }
  },

  data() {
    return {
      /** 要展示的图片相对路径，已剔除加载失败与重复的 */
      slides: [],

      /** 当前停在第几张，用于高亮圆点 */
      activeIndex: 0,

      /**
       * 加载失败的图片路径，键为路径本身。
       * 用对象而不是数组：判断「这张失败过吗」是 O(1)，而且增删不用维护下标。
       */
      brokenImages: {}
    }
  },

  computed: {
    /**
     * 候选图片列表。
     *
     * <p>优先用图片集，没有图片集时才退回封面 —— 与后端「封面由图片集第一张派生」
     * 的约定一致，因此正常情况下两者不会打架。空串与重复项在这里就滤掉。</p>
     */
    sources() {
      const paths = String(this.images || '')
        .split(',')
        .map(item => item.trim())
        .filter(item => item)
      if (!paths.length && this.cover) {
        paths.push(this.cover)
      }
      // 去重：后台图片集里同一张被传两次的话，轮播会出现两屏一模一样的图
      return paths.filter((item, index) => paths.indexOf(item) === index)
    },

    /**
     * 是否自动轮播。
     *
     * <p>两个条件：外层打开着（关掉之后没必要继续转）且不止一张图
     * （一张图自动轮播等于每隔几秒重画一次同一张，白耗性能，还容易看出闪烁）。</p>
     */
    autoPlay() {
      return this.active && this.slides.length > 1
    }
  },

  watch: {
    /**
     * 换了一本书就重建轮播。
     *
     * <p>必须重建而不是自动适配：详情弹窗是同一个实例反复 open()，
     * 不重置的话会出现「上一本停在第 3 张、点开新的一本直接从第 3 张开始」，
     * 而新书可能只有 2 张图，下标直接越界。</p>
     *
     * <p>immediate 是为了首屏：挂载时 sources 已经是最终值，不会再变，
     * 只靠「变化时触发」的话第一本书永远没有图。</p>
     */
    sources: {
      immediate: true,
      handler() {
        this.rebuild()
      }
    }
  },

  methods: {
    /** 重建：清掉失败记录、重置下标、回到第一张 */
    rebuild() {
      this.brokenImages = {}
      this.slides = this.sources.slice()
      this.activeIndex = 0
      // 等 DOM 更新后再操作轮播实例，否则拿到的还是上一份数据的实例
      this.$nextTick(this.resetCarousel)
    },

    /** 拼出图片完整地址。库里存的是 /profile/upload/... 这类相对路径 */
    fullUrl(path) {
      return process.env.VUE_APP_BASE_API + path
    },

    /**
     * 某张图加载失败：记下来并把它从轮播里摘掉。
     *
     * @param src 失败的图片相对路径
     */
    markBroken(src) {
      this.$set(this.brokenImages, src, true)
      this.slides = this.slides.filter(item => item !== src)
      // 删掉一张之后原来的下标可能已经越界，统一回到第一张
      this.activeIndex = 0
      this.$nextTick(this.resetCarousel)
    },

    /** 跳到指定的一张（点圆点触发，即「手动切换」） */
    goTo(index) {
      const carousel = this.$refs.carousel
      if (carousel && typeof carousel.setActiveItem === 'function') {
        carousel.setActiveItem(index)
      }
    },

    /**
     * 回到第一张。
     *
     * <p>两张以上才调用：只有一张时轮播本来就在第一张，没必要去碰它内部的分支逻辑；
     * 没有图时连实例都不存在。两个提前返回把「没意义又可能失败」的调用挡在外面。</p>
     */
    resetCarousel() {
      if (this.slides.length < 2) {
        return
      }
      this.goTo(0)
    },

    /** 轮播自己切换时同步下标，圆点才能跟着高亮 */
    handleChange(index) {
      this.activeIndex = index
    }
  }
}
</script>

<style lang="scss" scoped>
// ---------------------------------------------------------------
// 轮播整体：纵向排布「舞台 + 圆点」，自身不加背景与内边距 ——
// 背景属于外层图片面板，本组件只负责内容，方便在别的底色上复用。
// ---------------------------------------------------------------
.gallery {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

// 轮播容器取满宽度，中间的 300px 舞台两侧各留约 42px 给箭头
.gallery__carousel {
  width: 100%;
}

// 每一屏：把舞台在容器里居中（左右留白即箭头的活动区域）
.gallery__item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

// ---------------------------------------------------------------
// 书封舞台。300×420 是常规图书封面的比例（约 1 : 1.4），
// 无论图片本身是什么比例，版面都不会跳来跳去。
// ---------------------------------------------------------------
.gallery__stage {
  width: 300px;
  height: 420px;
  overflow: hidden;
  background-color: var(--client-bg-card);
  border-radius: var(--client-radius);
  box-shadow: 0 10px 26px 0 rgba(0, 0, 0, 0.14);

  img {
    display: block;
    width: 100%;
    height: 100%;
    // 完整展示，宁可上下留白也不裁切（见模板里的说明）
    object-fit: contain;
  }
}

// 没有图片时的占位：与舞台同尺寸，图标居中，避免版面高度突然塌掉
.gallery__stage--empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: var(--client-font-sm);
  color: var(--client-text-secondary);
  background-color: transparent;
  box-shadow: none;

  i {
    margin-bottom: 12px;
    font-size: 44px;
    color: var(--client-border);
  }
}

// ---------------------------------------------------------------
// 切换箭头。element-ui 默认是半透明黑底 + 白色图标，
// 在这块浅色面板上几乎看不见，所以改成白底描边卡片的样子。
// 位置贴着容器边缘（左右各约 42px 的留白区），不会压到书封。
// ---------------------------------------------------------------
::v-deep .gallery__carousel .el-carousel__arrow {
  left: 2px;
  width: 36px;
  height: 36px;
  font-size: 15px;
  color: var(--client-text-regular);
  background-color: var(--client-bg-card);
  border: 1px solid var(--client-border-light);
  box-shadow: var(--client-shadow-card);
  transition: color 0.2s, background-color 0.2s, border-color 0.2s;

  &:hover {
    color: var(--client-bg-card);
    background-color: var(--client-primary);
    border-color: transparent;
  }
}

// 右箭头要单独把 left 还原，否则会跟着上面那条规则一起贴到左边
::v-deep .gallery__carousel .el-carousel__arrow--right {
  right: 2px;
  left: auto;
}

// ---------------------------------------------------------------
// 圆点指示器（自己画的那套）。当前项从圆点拉长成胶囊，
// 靠形状而不只是颜色表达「停在第几张」—— 浅色底上更醒目。
// ---------------------------------------------------------------
.gallery__dots {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 20px;
}

.gallery__dot {
  width: 8px;
  height: 8px;
  padding: 0;
  margin: 0 4px;
  cursor: pointer;
  background-color: var(--client-border);
  border: none;
  border-radius: var(--client-radius-pill);
  transition: width 0.25s, background-color 0.25s;

  &:hover {
    background-color: var(--client-text-secondary);
  }

  &.is-active {
    width: 22px;
    background-image: linear-gradient(135deg, var(--client-primary-light) 0%, var(--client-primary) 100%);
  }
}
</style>
