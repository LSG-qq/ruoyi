<template>
  <!--
    单本图书卡片：左侧封面、右侧信息 + 操作，横向排布更适合「一行三本」的宽度。
    整张卡片可点，点开的是详情弹窗（抛 detail 事件给父页面）；
    「申请借阅」按钮用 click.stop 拦下冒泡，避免点按钮时连带把详情也打开。
  -->
  <article
    class="book-card"
    role="button"
    tabindex="0"
    :title="`查看《${book.name}》详情`"
    @click="$emit('detail', book)"
    @keyup.enter="$emit('detail', book)"
  >
    <div class="book-card__cover">
      <img v-if="showCover" :src="coverUrl" :alt="book.name" @error="coverFailed = true">
      <!-- 没有封面（或图片加载失败）时给一个占位图标，卡片高度不会因此忽高忽低 -->
      <div v-else class="book-card__placeholder">
        <i class="el-icon-picture-outline"></i>
      </div>
    </div>

    <div class="book-card__body">
      <h4 class="book-card__name">{{ book.name }}</h4>

      <p class="book-card__meta">
        <i class="el-icon-user"></i>
        <span>{{ book.author || '佚名' }}</span>
      </p>
      <p class="book-card__meta">
        <i class="el-icon-location-outline"></i>
        <span>{{ book.shelfCode || '未标注书架' }}</span>
      </p>

      <!-- 库存：够借用成功色、为 0 用危险色，不靠按钮文案也能一眼看出能不能借 -->
      <p class="book-card__stock" :class="available ? 'is-available' : 'is-empty'">
        <i :class="available ? 'el-icon-circle-check' : 'el-icon-circle-close'"></i>
        <span>{{ stockText }}</span>
      </p>

      <!--
        没库存时按钮置灰而不是隐藏：书还在架上（只是被借完了），
        让用户知道「这本书存在、以后会还回来」，比直接消失更有信息量。
      -->
      <el-button
        class="book-card__btn"
        type="primary"
        :disabled="!available"
        @click.stop="$emit('apply', book)"
      >
        {{ available ? '申请借阅' : '暂无可借' }}
      </el-button>
    </div>
  </article>
</template>

<script>
/**
 * 图书卡片（纯展示组件）。
 *
 * <p>不请求任何接口、不持有业务状态：给一本图书对象就渲染一张卡片，
 * 点卡片抛 `detail`（看详情）、点按钮抛 `apply`（申请借阅）。
 * **是否登录、要不要跳登录页、弹窗怎么开** 都属于父页面的判断，
 * 卡片不参与 —— 卡片一旦知道「登录」这件事，
 * 就没法被复用到将来可能出现的「已登录专用列表」里了。</p>
 *
 * <p>两件事拆成两个事件而不是合成一个「点了卡片」：详情是只读的、谁都能看，
 * 申请借阅要登录、要确认，两者的准入条件完全不同。合在一起的话，
 * 父页面就得靠「点的是哪块区域」去反推意图，边界很容易写歪。</p>
 *
 * <p>「没库存」在这里被当作**正常状态**而不是异常：后端会把库存为 0 的书照样下发，
 * 卡片只负责把它描述清楚（灰色按钮 + 红色「暂无库存」），不做任何过滤。
 * 没库存仍然可以点开详情 —— 简介与图片是该知道的信息，与能不能借无关。</p>
 */
export default {
  name: 'BookCard',

  props: {
    /** 图书对象，字段见后端 ClientBookVo */
    book: {
      type: Object,
      required: true
    }
  },

  data() {
    return {
      /** 图片地址失效时置 true，回落到占位图标（前端也不出现破图） */
      coverFailed: false
    }
  },

  computed: {
    /** 是否还有可借库存 */
    available() {
      return (this.book.stockQuantity || 0) > 0
    },

    /** 是否展示封面：没地址或加载失败都不展示 */
    showCover() {
      return !!this.book.cover && !this.coverFailed
    },

    /** 封面完整地址。库里存的是 /profile/upload/... 这类相对路径，要拼上接口前缀 */
    coverUrl() {
      return process.env.VUE_APP_BASE_API + this.book.cover
    },

    /** 库存文案 */
    stockText() {
      return this.available ? `可借 ${this.book.stockQuantity} 本` : '暂无库存'
    }
  }
}
</script>

<style lang="scss" scoped>
// ---------------------------------------------------------------
// 卡片整体。宽度由列表的栅格列决定（一行三本），这里只定义内部结构，
// 因此不写任何固定宽度 —— 换个每行本数，卡片自己就跟着变宽变窄。
// ---------------------------------------------------------------
.book-card {
  display: flex;
  height: 100%;
  padding: 14px;
  cursor: pointer;
  background-color: var(--client-bg-card);
  border: 1px solid var(--client-border-light);
  border-radius: var(--client-radius-card);
  box-shadow: var(--client-shadow-card);
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;

  // 悬停时轻轻抬起：触屏上不一定用得到，但鼠标操作用的机器也不少
  &:hover {
    transform: translateY(-3px);
    border-color: transparent;
    box-shadow: var(--client-shadow-hover);
  }

  // 卡片带 tabindex，键盘也能打开详情。默认的聚焦外框在圆角卡片上很难看，
  // 这里换成主色描边 —— 该有的可见反馈还在，只是换了个样子。
  &:focus {
    border-color: var(--client-primary);
    outline: none;
    box-shadow: var(--client-shadow-hover);
  }
}

// ---------------------------------------------------------------
// 封面区。尺寸写死成竖版书封的比例，卡片高度才不会因图而异。
// ---------------------------------------------------------------
.book-card__cover {
  flex: none;
  width: 108px;
  height: 148px;
  margin-right: 14px;
  overflow: hidden;
  background-color: var(--client-bg-muted);
  border-radius: var(--client-radius);

  img {
    display: block;
    width: 100%;
    height: 100%;
    // cover：书封多为竖版，填满盒子比留白更好看
    object-fit: cover;
  }
}

// 没有封面时的占位：一个居中的灰色图标，撑住封面区的高度
.book-card__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 30px;
  color: var(--client-text-secondary);
}

// ---------------------------------------------------------------
// 文字信息区
// ---------------------------------------------------------------
.book-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  // min-width: 0 是让下面书名/作者的省略号生效的前提，缺了它 flex 子项会被文字撑开
  min-width: 0;
}

// 书名：单行省略，全文放在 title 属性里，悬停可看
.book-card__name {
  margin: 0 0 12px;
  overflow: hidden;
  font-size: var(--client-font-md);
  font-weight: 600;
  color: var(--client-text-primary);
  white-space: nowrap;
  text-overflow: ellipsis;
}

// 作者、书架号这类辅助信息：图标 + 文本一行，过长同样省略
.book-card__meta {
  display: flex;
  align-items: center;
  margin: 0 0 7px;
  font-size: var(--client-font-sm);
  color: var(--client-text-regular);

  i {
    flex: none;
    margin-right: 6px;
    color: var(--client-text-secondary);
  }

  span {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

// 库存行：颜色由 is-available / is-empty 决定，语义色统一取自主题变量
.book-card__stock {
  display: flex;
  align-items: center;
  margin: 2px 0 0;
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
// 操作区。按钮宽度撑满文字区，高度取主题里的最小可点高度（触摸屏要够大）。
// ---------------------------------------------------------------
.book-card__btn {
  width: 100%;
  height: var(--client-tap-height);
  // margin-top: auto 让按钮贴到卡片底部：书名长短不一时，一排里的按钮也能对齐
  margin-top: auto;
  margin-left: 0;
  font-size: var(--client-font-base);
}
</style>
