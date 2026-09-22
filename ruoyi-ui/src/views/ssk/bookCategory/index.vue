<template>
  <div class="app-container">
    <!-- 操作栏：新增顶级类目、批量删除、保存排序、展开折叠 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAddRoot"
          v-hasPermi="['ssk:bookCategory:add']"
        >新增类目</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multipleSelection.length === 0"
          @click="handleBatchDelete"
          v-hasPermi="['ssk:bookCategory:remove']"
        >批量删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-check"
          size="mini"
          @click="handleSaveSort"
          v-hasPermi="['ssk:bookCategory:edit']"
        >保存排序</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-sort"
          size="mini"
          @click="toggleExpandAll"
        >展开/折叠</el-button>
      </el-col>
    </el-row>

    <!-- 类目树形表格，全量数据一次性展示 -->
    <el-table
      v-if="refreshTable"
      ref="table"
      v-loading="loading"
      :data="categoryList"
      row-key="id"
      :default-expand-all="isExpandAll"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="title" label="类目名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="orderNum" label="显示排序" width="150" align="center">
        <template slot-scope="scope">
          <el-input-number
            v-model="scope.row.orderNum"
            controls-position="right"
            :min="0"
            size="mini"
            style="width: 110px"
          />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
        <template slot-scope="scope">
          <span>{{ scope.row.remark || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createdAt" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="290" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-plus"
            @click="handleAddChild(scope.row)"
            v-hasPermi="['ssk:bookCategory:add']"
          >新增子类目</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['ssk:bookCategory:edit']"
          >编辑</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['ssk:bookCategory:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <book-category-form ref="categoryForm" @success="getList" />
  </div>
</template>

<script>
import { listBookCategory, delBookCategory, updateBookCategorySort } from "@/api/ssk/bookCategory"
import BookCategoryForm from "./components/BookCategoryForm"

export default {
  name: "BookCategory",
  components: { BookCategoryForm },
  data() {
    return {
      // 表格加载状态
      loading: true,
      // 类目树数据
      categoryList: [],
      // 表格勾选中的类目
      multipleSelection: [],
      // 是否展开全部节点
      isExpandAll: true,
      // 重新渲染表格状态，用于切换展开/折叠
      refreshTable: true,
      // 记录原始排序，用于对比出发生变更的节点
      originalOrders: {}
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询类目列表并组装为树结构 */
    getList() {
      this.loading = true
      listBookCategory().then(response => {
        this.categoryList = this.handleTree(response.data, "id", "parentId", "children")
        this.recordOriginalOrders(this.categoryList)
        this.multipleSelection = []
        this.loading = false
        this.$nextTick(() => {
          if (this.$refs.table) {
            this.$refs.table.clearSelection()
          }
        })
      }).catch(() => {
        this.loading = false
      })
    },
    /** 递归记录原始排序值 */
    recordOriginalOrders(list) {
      list.forEach(item => {
        this.originalOrders[item.id] = item.orderNum
        if (item.children && item.children.length) {
          this.recordOriginalOrders(item.children)
        }
      })
    },
    /** 递归收集排序发生变化的节点 */
    collectChangedOrders(list, result) {
      list.forEach(item => {
        if (String(this.originalOrders[item.id]) !== String(item.orderNum)) {
          result.push({ id: item.id, orderNum: item.orderNum })
        }
        if (item.children && item.children.length) {
          this.collectChangedOrders(item.children, result)
        }
      })
      return result
    },
    /** 保存排序，只提交发生变更的节点 */
    handleSaveSort() {
      const changed = this.collectChangedOrders(this.categoryList, [])
      if (changed.length === 0) {
        this.$modal.msgWarning("未检测到排序修改")
        return
      }
      updateBookCategorySort(changed).then(() => {
        this.$modal.msgSuccess("排序保存成功")
        this.getList()
      })
    },
    /** 表格勾选变化 */
    handleSelectionChange(selection) {
      this.multipleSelection = selection
    },
    /** 新增顶级类目 */
    handleAddRoot() {
      this.$refs.categoryForm.open()
    },
    /** 新增子类目 */
    handleAddChild(row) {
      this.$refs.categoryForm.open(null, row)
    },
    /** 编辑类目 */
    handleUpdate(row) {
      this.$refs.categoryForm.open(row)
    },
    /** 展开/折叠全部节点 */
    toggleExpandAll() {
      this.refreshTable = false
      this.isExpandAll = !this.isExpandAll
      this.$nextTick(() => {
        this.refreshTable = true
      })
    },
    /**
     * 递归收集节点自身及其全部下级的ID
     * @param {Object} row 类目节点
     * @param {Array} result 结果数组
     */
    collectNodeIds(row, result) {
      result.push(row.id)
      const children = row.children || []
      children.forEach(child => {
        this.collectNodeIds(child, result)
      })
      return result
    },
    /**
     * 计算级联删除实际会影响的类目数量
     * @param {Array} rows 本次操作的类目节点
     * @returns {Number} 除本级外会被一并删除的下级类目数量
     */
    countCascadeChildren(rows) {
      const collected = []
      rows.forEach(row => {
        this.collectNodeIds(row, collected)
      })
      // 勾选的父子节点会产出重复ID，去重后再减去本级数量
      const uniqueCount = new Set(collected).size
      return uniqueCount - rows.length
    },
    /**
     * 删除确认：存在下级类目时追加一次级联删除的二次确认
     * @param {String} message 首次确认提示
     * @param {Number} cascadeCount 本次操作会级联删除的下级类目数量
     * @returns {Promise} 两次确认均通过后 resolve
     */
    confirmCascadeDelete(message, cascadeCount) {
      return this.$modal.confirm(message).then(() => {
        if (cascadeCount === 0) {
          return Promise.resolve()
        }
        // 级联删除不可恢复，改用红色警示样式并改写按钮文案，避免误点
        return this.$confirm(
          "该操作会连同 " + cascadeCount + " 个下级类目一起删除，删除后不可恢复，请再次确认。",
          "级联删除二次确认",
          {
            confirmButtonText: "确认级联删除",
            cancelButtonText: "我再想想",
            type: "error"
          }
        )
      })
    },
    /** 删除单个类目（存在下级时级联删除） */
    handleDelete(row) {
      const cascadeCount = this.countCascadeChildren([row])
      this.confirmCascadeDelete('是否确认删除图书类目"' + row.title + '"？', cascadeCount).then(() => {
        return delBookCategory(row.id)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 批量删除已勾选的类目（存在下级时级联删除） */
    handleBatchDelete() {
      const rows = this.multipleSelection
      if (rows.length === 0) {
        this.$modal.msgWarning("请选择要删除的图书类目")
        return
      }
      const ids = rows.map(item => item.id)
      const cascadeCount = this.countCascadeChildren(rows)
      this.confirmCascadeDelete('是否确认删除已选中的 ' + ids.length + ' 个图书类目？', cascadeCount).then(() => {
        return delBookCategory(ids.join(","))
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    }
  }
}
</script>
