<template>
  <!-- 搜索区域：上方是胶囊型搜索框，下方是一行友好提示 -->
  <section class="search-bar">
    <div class="search-bar__box" :class="{ 'is-focus': focused }">
      <!--
        输入框交给 el-input：v-model、回车、聚焦态这些行为不用自己实现，
        外层的 .search-bar__box 只负责「胶囊外观 + 聚焦光晕」，两者职责不重叠。
      -->
      <el-input
        v-model="keyword"
        class="search-bar__input"
        :placeholder="placeholder"
        @focus="focused = true"
        @blur="focused = false"
        @keyup.enter.native="handleSearch"
      >
        <i slot="prefix" class="el-icon-search search-bar__icon"></i>
        <!-- 有内容时才出现的清空按钮；点它会「清空 + 重新查询」，不是只清文字 -->
        <i
          v-show="keyword"
          slot="suffix"
          class="el-icon-circle-close search-bar__icon search-bar__clear"
          title="清空"
          @click="handleClear"
        ></i>
      </el-input>

      <button type="button" class="search-bar__btn" @click="handleSearch">
        <i class="el-icon-search"></i>
        <span>搜 索</span>
      </button>
    </div>

    <!-- 友好提示：把「能搜什么」和「搜索范围跟着谁走」一次说清楚，省得用户试错 -->
    <p class="search-bar__hint">
      <i class="el-icon-info"></i>
      <span>{{ hint }}</span>
    </p>
  </section>
</template>

<script>
/**
 * 图书搜索框。
 *
 * <p>只负责「输入 + 触发查询」，**不持有查询结果**：组件把用户输入的词通过
 * `input` 事件同步给父页面（父页面用 `v-model` 接），回车或点「搜索」时再抛
 * `search` 事件。这样父页面拿到的永远是「当前输入框里的词」，
 * 于是「先输入、再点左侧类目」也能按刚输入的内容去查。</p>
 *
 * <p>刻意不做输入即查（防抖搜索）：终端装在公共区域，用户多半是慢慢敲一个完整书名，
 * 边敲边发请求既浪费后端，又会让列表在用户眼皮底下反复重排。</p>
 *
 * <p>提示语是动态的，会带上当前选中的类目名 —— 用户在搜索框里就能确认
 * 「我这次的搜索范围是什么」，不必回头看左侧树。</p>
 */
export default {
  name: 'BookSearchBar',

  props: {
    /** 输入框内容（v-model） */
    value: {
      type: String,
      default: ''
    },

    /** 当前选中的类目名称，用于拼提示语 */
    categoryName: {
      type: String,
      default: '全部'
    }
  },

  data() {
    return {
      /** 输入框是否聚焦，用于给胶囊加一圈光晕 */
      focused: false
    }
  },

  computed: {
    /** 输入内容的读写代理：读的是 prop，写出去是 input 事件（即 v-model） */
    keyword: {
      get() {
        return this.value
      },
      set(val) {
        this.$emit('input', val)
      }
    },

    /** 占位提示：选中具体类目时把类目名念出来，比干巴巴的「请输入书名」更有用 */
    placeholder() {
      return this.isAllCategory
        ? '搜索书名，例如「三体」「活着」…'
        : `在「${this.categoryName}」中搜索书名…`
    },

    /** 是否处于「全部类目」（虚拟根节点）状态 */
    isAllCategory() {
      return !this.categoryName || this.categoryName === '全部'
    },

    /** 下方那行提示文案 */
    hint() {
      return this.isAllCategory
        ? '当前搜索范围：全部图书；输入书名后按回车或点「搜索」'
        : `当前搜索范围：${this.categoryName}；点左侧「全部」可搜全馆`
    }
  },

  methods: {
    /** 触发查询。真正的分页重置在父页面里做 */
    handleSearch() {
      this.$emit('search', this.value)
    },

    /** 清空并立即重新查询 */
    handleClear() {
      this.$emit('input', '')
      this.$emit('search', '')
    }
  }
}
</script>

<style lang="scss" scoped>
// ---------------------------------------------------------------
// 胶囊外框。边框与底色都在这一层，内层 el-input 要剥掉自己那套，
// 否则会出现「双层边框」；聚焦光晕也只有这一个元素需要管。
// ---------------------------------------------------------------
.search-bar__box {
  display: flex;
  align-items: center;
  padding: 6px 6px 6px 16px;
  background-color: var(--client-bg-card);
  border: 1px solid var(--client-border);
  border-radius: var(--client-radius-pill);
  box-shadow: var(--client-shadow-card);
  transition: border-color 0.2s, box-shadow 0.2s;

  // 聚焦时用主色描边 + 一圈光晕，代替默认的细边框，视觉上更「在场」
  &.is-focus {
    border-color: var(--client-primary);
    box-shadow: 0 0 0 4px var(--client-primary-glow), var(--client-shadow-card);
  }
}

// ---------------------------------------------------------------
// 内层输入框。三个 ::v-deep 块分别处理图标、输入域、清空按钮的位置，
// 它们都在 element-ui 的组件内部，普通选择器够不到。
// ---------------------------------------------------------------
.search-bar__input {
  flex: 1;
  min-width: 0;

  // 让图标垂直居中：不依赖 element-ui 给 .el-input__icon 写死的 line-height
  ::v-deep .el-input__prefix,
  ::v-deep .el-input__suffix {
    display: flex;
    align-items: center;
    height: 100%;
  }

  // 内层输入框去掉边框与底色 —— 边框已经由外层胶囊承担，留着会出现双框
  ::v-deep .el-input__inner {
    height: 42px;
    padding-left: 26px;
    padding-right: 26px;
    font-size: var(--client-font-md);
    background-color: transparent;
    border: none;
  }

  // 清空按钮与输入文字之间留一点距离，避免紧贴最后一个字
  ::v-deep .el-input__suffix {
    right: 10px;
  }
}

// 搜索图标与清空图标共用的基础样式
.search-bar__icon {
  width: auto;
  height: auto;
  font-size: 18px;
  line-height: 1;
  color: var(--client-text-secondary);
}

// 清空图标：比搜索图标小一号，悬停加深，暗示「可以点」
.search-bar__clear {
  font-size: 17px;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: var(--client-text-regular);
  }
}

// ---------------------------------------------------------------
// 主按钮。用原生 button 而不是 el-button：这里要的是「渐变 + 同色投影」的
// 定制外观，套一层 el-button 反而要一路覆盖它的默认样式。
// ---------------------------------------------------------------
.search-bar__btn {
  flex: none;
  height: 42px;
  padding: 0 28px;
  margin-left: 10px;
  font-size: var(--client-font-md);
  font-weight: 500;
  color: var(--client-bg-card);
  letter-spacing: 2px;
  cursor: pointer;
  border: none;
  border-radius: var(--client-radius-pill);
  // 主色渐变 + 同色投影，是现代后台里最常见的「主动作」表达
  background-image: linear-gradient(
    135deg,
    var(--client-primary-light) 0%,
    var(--client-primary) 55%,
    var(--client-primary-dark) 100%
  );
  box-shadow: var(--client-shadow-primary);
  transition: filter 0.2s, transform 0.1s;

  i {
    margin-right: 6px;
  }

  &:hover {
    filter: brightness(1.06);
  }

  // 触屏上「按下去」要有反馈，只靠 hover 是看不出来的
  &:active {
    transform: scale(0.98);
  }
}

// 输入框下方的提示行：字号最小、颜色最淡，只做说明不抢注意力
.search-bar__hint {
  display: flex;
  align-items: center;
  margin: 10px 2px 0;
  font-size: var(--client-font-xs);
  color: var(--client-text-secondary);

  i {
    margin-right: 6px;
    font-size: 14px;
  }
}
</style>
