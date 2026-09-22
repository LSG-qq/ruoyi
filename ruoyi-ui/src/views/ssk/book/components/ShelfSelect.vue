<template>
  <!-- 书架号下拉组件：数据来源于字典 book_shelfs，搜索与新建/编辑共用 -->
  <el-select
    v-model="currentValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    style="width: 100%"
    @change="handleChange"
  >
    <el-option
      v-for="dict in shelfOptions"
      :key="dict.value"
      :label="dict.label"
      :value="dict.value"
    />
  </el-select>
</template>

<script>
/**
 * 书架号下拉组件
 *
 * 字典编码固定为 book_shelfs，书架号以 B 开头（如 B001），
 * 字典数据在「系统管理 - 字典管理」中维护，组件会自动读取。
 */
export default {
  name: "ShelfSelect",
  // 声明依赖的字典，由全局字典 mixin 自动按需加载并缓存
  dicts: ["book_shelfs"],
  props: {
    // 已选书架号
    value: {
      type: [String, Number],
      default: undefined
    },
    // 占位提示
    placeholder: {
      type: String,
      default: "请选择书架号"
    },
    // 是否禁用
    disabled: {
      type: Boolean,
      default: false
    },
    // 是否可清空，搜索条件中可清空，表单中保持可清空以便重新选择
    clearable: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      // 组件内部值
      currentValue: this.normalize(this.value)
    }
  },
  computed: {
    // 书架号字典数据
    shelfOptions() {
      return (this.dict && this.dict.type && this.dict.type.book_shelfs) || []
    }
  },
  watch: {
    // 外部值变化时同步到组件内部
    value(val) {
      const normalized = this.normalize(val)
      if (normalized !== this.currentValue) {
        this.currentValue = normalized
      }
    }
  },
  methods: {
    /**
     * 统一把空值转换为 null，避免出现空字符串选项匹配不上的情况
     * @param {String|Number} val 外部传入的值
     */
    normalize(val) {
      return val === undefined || val === null || val === "" ? null : val
    },
    /**
     * 选择结果变化时向外抛出
     * @param {String|Number|null} val 选中的书架号
     */
    handleChange(val) {
      this.$emit("input", val === undefined || val === null || val === "" ? null : val)
    }
  }
}
</script>
