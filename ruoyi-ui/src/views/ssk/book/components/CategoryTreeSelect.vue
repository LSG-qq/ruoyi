<template>
  <!-- 类目树选择组件：多选用于新建/编辑图书，单选用于搜索条件 -->
  <treeselect
    v-model="currentValue"
    :options="options"
    :multiple="multiple"
    :normalizer="normalizer"
    :default-expand-level="Infinity"
    :show-count="true"
    :limit="3"
    :limit-text="limitText"
    :disabled="disabled"
    :clearable="true"
    :placeholder="placeholder"
    :no-options-text="'暂无可选类目'"
    :no-results-text="'未找到匹配的类目'"
    :loading-text="'加载中...'"
    @input="handleInput"
  />
</template>

<script>
import { listBookCategory } from "@/api/ssk/bookCategory"
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"

/**
 * 类目树选择组件
 *
 * 对外取值约定：
 * - multiple 为 true（默认）：v-model 为英文逗号分隔的类目ID字符串，如 "3,7,12"，多选结果按勾选顺序保存；
 * - multiple 为 false：v-model 为单个类目ID，未选择时为 null。
 */
export default {
  name: "CategoryTreeSelect",
  components: { Treeselect },
  props: {
    // 已选类目：多选为逗号分隔字符串，单选为类目ID
    value: {
      type: [String, Number, Array],
      default: undefined
    },
    // 是否多选，默认多选
    multiple: {
      type: Boolean,
      default: true
    },
    // 是否禁用
    disabled: {
      type: Boolean,
      default: false
    },
    // 占位提示
    placeholder: {
      type: String,
      default: "请选择类目"
    }
  },
  data() {
    return {
      // 组件内部使用数组承载多选结果
      currentValue: this.multiple ? [] : null,
      // 类目树数据
      options: [],
      // 多选超出展示上限时的提示文案
      limitText: (count) => `还有 ${count} 个类目`
    }
  },
  watch: {
    // 外部值变化时同步到组件内部
    value: {
      handler(val) {
        this.currentValue = this.parseValue(val)
      },
      immediate: true
    },
    // 多选/单选模式切换时重新解析外部值
    multiple() {
      this.currentValue = this.parseValue(this.value)
    }
  },
  created() {
    // 加载失败时 options 保持空数组即可，错误提示由全局请求拦截器统一给出；
    // 这里补 catch 是为了避免产生未处理的 promise 拒绝。
    this.loadOptions().catch(() => {})
  },
  methods: {
    /** 加载类目树数据 */
    loadOptions() {
      return listBookCategory().then(response => {
        this.options = this.handleTree(response.data, "id", "parentId", "children")
      })
    },
    /**
     * 把外部值解析为组件内部需要的格式
     * @param {String|Number|Array} val 外部传入的值
     */
    parseValue(val) {
      if (this.multiple) {
        if (Array.isArray(val)) {
          return val.filter(item => item !== null && item !== undefined && item !== "")
        }
        if (val === null || val === undefined || val === "") {
          return []
        }
        return String(val)
          .split(",")
          .map(item => item.trim())
          .filter(item => item !== "")
          .map(item => this.toCategoryId(item))
      }
      if (val === null || val === undefined || val === "" || Array.isArray(val)) {
        return Array.isArray(val) && val.length ? this.toCategoryId(val[0]) : null
      }
      return this.toCategoryId(val)
    },
    /**
     * 类目ID统一按数字处理，与树数据中的节点ID类型保持一致
     * @param {String|Number} val 类目ID
     */
    toCategoryId(val) {
      const num = Number(val)
      return isNaN(num) ? val : num
    },
    /**
     * 选择结果变化时向外抛出
     * @param {Array|Number|null} val treeselect 的值
     */
    handleInput(val) {
      if (this.multiple) {
        const list = Array.isArray(val) ? val : []
        this.$emit("input", list.join(","))
        return
      }
      this.$emit("input", val === undefined || val === null || val === "" ? null : val)
    },
    /** 转换组件所需的下拉树结构 */
    normalizer(node) {
      if (node.children && !node.children.length) {
        delete node.children
      }
      return {
        id: node.id,
        label: node.title,
        children: node.children
      }
    }
  }
}
</script>
