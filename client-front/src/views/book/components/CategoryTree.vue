<template>
  <!-- 类目树面板：左侧固定宽度，标题固定、树身自己滚动 -->
  <aside class="category-tree">
    <header class="category-tree__head">
      <i class="el-icon-collection-tag"></i>
      <span class="category-tree__title">图书类目</span>
      <!-- 类目数量：让人一眼知道这棵树有多大，不用自己数 -->
      <span v-if="!loading && !failed" class="category-tree__count">{{ categories.length }} 个</span>
    </header>

    <div class="category-tree__body">
      <!-- 加载中：只占位不显示空树，避免「先是空的、然后突然长出一棵树」 -->
      <div v-if="loading" class="category-tree__state">
        <i class="el-icon-loading"></i>
        <span>类目加载中…</span>
      </div>

      <!--
        加载失败：给出重试入口，而不是留一棵空树。
        类目只是筛选项，但它挂掉时用户会以为「系统里就是没有类目」，
        那是把故障说成了业务事实 —— 所以这里必须把失败讲明白。
      -->
      <div v-else-if="failed" class="category-tree__state is-failed">
        <i class="el-icon-warning-outline"></i>
        <span>类目加载失败</span>
        <el-button type="text" @click="$emit('retry')">重新加载</el-button>
      </div>

      <!--
        展开/聚焦策略（两个要求叠加的结果）：
        - 首屏只露**一级类目**：default-expanded-keys 只放虚拟根节点「全部」，
          二级及更深层不预设展开，避免类目多起来糊满一屏。
        - **点节点只聚焦（筛选），不展开**：expand-on-click-node=false。
          展开/收起只认左侧那个三角 —— el-tree 对三角做了 @click.stop，
          所以点它有且只有「展开/收起」一个效果，不会顺带改筛选条件、也不会改聚焦项；
          点节点行则相反，只走 node-click（聚焦 + 筛选），不动展开状态。
          代价是三角成了唯一入口，所以它的可点区域在样式里放大了（默认只有 24px，触摸屏难戳）。
      -->
      <el-tree
        v-else
        ref="tree"
        :data="treeData"
        :props="treeProps"
        node-key="id"
        highlight-current
        :default-expanded-keys="defaultExpandedKeys"
        :expand-on-click-node="false"
        @node-click="handleNodeClick"
      >
        <span slot-scope="{ data }" class="cat-node">
          <!-- 只有「全部」带图标；后端类目没有 icon 字段，这里不补默认图标，保持树身素净 -->
          <i v-if="data.icon" :class="data.icon" class="cat-node__icon"></i>
          <span class="cat-node__label">{{ data.title }}</span>
        </span>
      </el-tree>
    </div>
  </aside>
</template>

<script>
import { handleTree } from '@/utils/ruoyi'

/** 「全部」节点的固定 id。后端把 categoryId 为 0（或空）解释为不过滤类目，两边对齐 */
const ALL_CATEGORY_ID = 0

/**
 * 图书类目树。
 *
 * <p>数据结构是「后端下发扁平列表 → 组件内用 handleTree 组树 → 前面挂一个虚拟根节点」。
 * 之所以在**前端**补这个「全部」根节点，而不是让后端在类目表里真建一条名为「全部」的记录：
 * 它表达的是「不筛选」这个动作，不是一条业务数据。真建一条的话，
 * 每个类目相关的查询（含后台）都要去特判它，改一次数据就得跟着改代码。</p>
 *
 * <p>组件的对外契约只有两条：`props.categories` 传扁平类目，`select` 事件丢出选中的 id。
 * 它不认识「图书」也不发请求，类目列表由父页面取 —— 这样树只负责展示与选中。</p>
 *
 * <p>选中态用的是渐变主色块 + 同色投影，比默认的浅蓝底 + 蓝字更有「当前项」的分量；
 * 同时把 el-tree 自带的展开三角从「叶子节点处也显示」压成透明，
 * 免得一排小三角把简洁的树身弄得很碎。</p>
 */
export default {
  name: 'BookCategoryTree',

  props: {
    /** 扁平类目列表，元素形如 { id, parentId, title, orderNum } */
    categories: {
      type: Array,
      default: () => []
    },

    /** 当前选中的类目 id，0 表示「全部」 */
    currentId: {
      type: Number,
      default: ALL_CATEGORY_ID
    },

    /** 类目是否正在加载 */
    loading: {
      type: Boolean,
      default: false
    },

    /** 类目是否加载失败（失败时显示重试，而不是空树） */
    failed: {
      type: Boolean,
      default: false
    }
  },

  data() {
    return {
      /** el-tree 的字段映射：标签取 title，子节点取 children */
      treeProps: {
        label: 'title',
        children: 'children'
      },

      /**
       * 首屏默认展开的节点：只放虚拟根节点「全部」。
       *
       * <p>展开「全部」等于露出 **一级类目** —— 正好是用户首先要看到的那一层。
       * 二级及更深的层级不预设展开，交给用户按需点开：
       * 类目多起来时全展开会把左栏铺成一整屏，想找的类目反而被淹没。
       * 写成常量数组是刻意的 —— el-tree 只在树首次渲染时读一次这个值，
       * 后续用户自己的展开/收起状态由组件内部维护，这里不需要也不应该跟着变。</p>
       */
      defaultExpandedKeys: [ALL_CATEGORY_ID]
    }
  },

  computed: {
    /**
     * 树上真正的数据：虚拟根节点「全部」+ 后端类目组出来的树。
     *
     * <p>根节点的 icon 与子节点不同，顺手在这里挂上，省得模板里写三元判断。</p>
     */
    treeData() {
      // handleTree 是就地修改（会给每个节点挂 children），所以先浅拷贝一份再组树 ——
      // categories 是父页面传进来的 prop，直接改它等于在子组件里动了父组件的数据
      const source = (this.categories || []).map(item => Object.assign({}, item))
      const children = handleTree(source, 'id', 'parentId', 'children')
      return [
        {
          id: ALL_CATEGORY_ID,
          title: '全部',
          icon: 'el-icon-menu',
          children
        }
      ]
    }
  },

  watch: {
    /**
     * 跟随外部传入的 currentId 高亮节点。
     *
     * <p>不用 el-tree 的 `current-node-key` 属性：那个属性只在节点首次渲染时生效，
     * 父页面把选中项重置回「全部」时（例如重置搜索），高亮不会跟着回去，
     * 会出现「列表已回到全部图书，但树里还高亮着某个类目」这种对不上的状态。</p>
     */
    currentId: {
      immediate: true,
      handler(id) {
        this.syncCurrentKey(id)
      }
    },

    /**
     * 加载结束后补一次高亮。
     *
     * <p>首次进入时 currentId 的 watch 是同步触发的，那一刻树还在 loading、
     * 根本没渲染出来（模板走的是 v-if 的加载态），这一次同步就白做了。
     * 等 loading 落回 false、树真正渲染出来之后，必须再补一次 ——
     * 否则首屏会出现「列表显示的是全部图书，但树上一个节点都没高亮」。</p>
     */
    loading(val) {
      if (!val) {
        this.syncCurrentKey(this.currentId)
      }
    }
  },

  methods: {
    /**
     * 把高亮同步到树上的指定节点。
     *
     * <p>数据是本组件内组出来的，跨组件的 ref 调用要等子组件渲染完，
     * 所以放到 nextTick 里；树还没渲染时（加载中/失败态）静默跳过即可，
     * 等 v-else 分支渲染出来时 watch 不会再触发，因此这里也用不着重试逻辑。</p>
     */
    syncCurrentKey(id) {
      this.$nextTick(() => {
        const tree = this.$refs.tree
        if (tree && typeof tree.setCurrentKey === 'function') {
          tree.setCurrentKey(id)
        }
      })
    },

    /** 点节点即筛选。父页面负责把 id 转成查询条件并重新拉列表 */
    handleNodeClick(data) {
      this.$emit('select', data.id)
    }
  }
}
</script>

<style lang="scss" scoped>
.category-tree {
  display: flex;
  flex-direction: column;
  /* 撑满左侧栏高度，让树身成为独立的滚动区域（页面本身不滚） */
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background-color: var(--client-bg-card);
  border-radius: var(--client-radius-card);
  box-shadow: var(--client-shadow-card);
}

.category-tree__head {
  display: flex;
  align-items: center;
  flex: none;
  padding: 14px 16px;
  font-size: var(--client-font-md);
  font-weight: 600;
  color: var(--client-text-primary);
  /* 极淡的分隔线：比边框色弱一档，只用来断开标题与树身 */
  border-bottom: 1px solid var(--client-border-light);

  i {
    margin-right: var(--client-gap-sm);
    color: var(--client-primary);
  }
}

.category-tree__count {
  margin-left: auto;
  font-size: var(--client-font-xs);
  font-weight: 400;
  color: var(--client-text-secondary);
}

.category-tree__body {
  flex: 1;
  min-height: 0;
  padding: var(--client-gap-sm);
  overflow-y: auto;
}

.category-tree__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px 8px;
  font-size: var(--client-font-sm);
  color: var(--client-text-secondary);

  i {
    margin-bottom: 10px;
    font-size: 22px;
  }

  &.is-failed i {
    color: var(--client-warning);
  }
}

/* 树身：节点高一点、圆一点，触摸屏上更好点 */
.category-tree__body ::v-deep .el-tree {
  color: var(--client-text-regular);
  background-color: transparent;

  .el-tree-node__content {
    height: 44px;
    margin-bottom: 4px;
    padding-right: 10px;
    border-radius: var(--client-radius-card);
    transition: background-color 0.2s, color 0.2s, box-shadow 0.2s;

    &:hover {
      background-color: var(--client-primary-soft);
    }
  }

  /* 选中态：渐变主色块 + 同色投影 + 文字转白加粗，与「当前看的是哪个类目」一一对应 */
  .el-tree-node.is-current > .el-tree-node__content {
    color: var(--client-bg-card);
    background-image: linear-gradient(
      135deg,
      var(--client-primary-light) 0%,
      var(--client-primary) 55%,
      var(--client-primary-dark) 100%
    );
    box-shadow: var(--client-shadow-primary);

    .cat-node__label {
      font-weight: 600;
    }

    .cat-node__icon,
    .el-tree-node__expand-icon {
      color: var(--client-bg-card);
    }
  }

  /*
    展开三角：现在它是**唯一的展开入口**（点节点只聚焦），所以可点区域要够大 ——
    默认 padding 6px 让热区只有 24px 见方，触摸屏上很难戳中，这里撑到约 32×42。
    el-tree 对每个节点都渲染这个 span（含叶子，无 v-if），所以叶子节点的三角保留占位、
    只把颜色压透明 —— 直接藏掉会让叶子节点整体左移、与父级对不齐；
    它上面的点击也被 handleExpandIconClick 开头的 `if (node.isLeaf) return` 挡住，点了没反应。
  */
  .el-tree-node__expand-icon {
    padding: 14px 10px;
    color: var(--client-text-secondary);

    /* 只有真能展开的三角给 hover 反馈，把「这里才是展开入口」讲明白 */
    &:not(.is-leaf):hover {
      color: var(--client-primary);
    }

    &.is-leaf {
      color: transparent;
    }
  }
}

.cat-node {
  display: flex;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  font-size: var(--client-font-sm);
}

.cat-node__icon {
  flex: none;
  margin-right: 6px;
  font-size: 15px;
  color: var(--client-info);
}

.cat-node__label {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
