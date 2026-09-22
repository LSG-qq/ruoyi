<template>
  <div class="app-container">
    <el-row :gutter="12">
      <!-- 左侧：图书类目树，点击节点查看该节点及其子节点下的图书 -->
      <el-col :span="5">
        <div class="category-panel">
          <div class="category-panel__title">图书类目</div>
          <el-input
            v-model="categoryFilter"
            placeholder="输入类目名称筛选"
            size="small"
            clearable
            prefix-icon="el-icon-search"
            class="category-panel__filter"
          />
          <el-tree
            ref="categoryTree"
            :data="categoryTree"
            :props="treeProps"
            node-key="id"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            :filter-node-method="filterCategoryNode"
            @node-click="handleCategoryClick"
          />
        </div>
      </el-col>

      <!-- 右侧：搜索条件、操作按钮、图书列表 -->
      <el-col :span="19">
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
          <el-form-item label="类目" prop="categoryId">
            <category-tree-select
              v-model="queryParams.categoryId"
              :multiple="false"
              placeholder="全部类目"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="书架号" prop="shelfCode">
            <shelf-select v-model="queryParams.shelfCode" placeholder="全部书架" style="width: 160px" />
          </el-form-item>
          <el-form-item label="书籍名称" prop="name">
            <el-input
              v-model="queryParams.name"
              placeholder="请输入书籍名称"
              clearable
              style="width: 180px"
              @keyup.enter.native="handleQuery"
            />
          </el-form-item>
          <el-form-item label="作者姓名" prop="author">
            <el-input
              v-model="queryParams.author"
              placeholder="请输入作者姓名"
              clearable
              style="width: 180px"
              @keyup.enter.native="handleQuery"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="primary"
              plain
              icon="el-icon-plus"
              size="mini"
              @click="handleAdd"
              v-hasPermi="['ssk:book:add']"
            >新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="success"
              plain
              icon="el-icon-edit"
              size="mini"
              :disabled="single"
              @click="handleUpdate"
              v-hasPermi="['ssk:book:edit']"
            >修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="danger"
              plain
              icon="el-icon-delete"
              size="mini"
              :disabled="multiple"
              @click="handleDelete"
              v-hasPermi="['ssk:book:remove']"
            >删除</el-button>
          </el-col>
          <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" :data="bookList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="封面" align="center" width="76">
            <template slot-scope="scope">
              <el-image
                v-if="scope.row.cover"
                :src="imageUrl(scope.row.cover)"
                :preview-src-list="previewImages(scope.row)"
                fit="contain"
                class="book-cover"
              />
              <span v-else class="book-cover__empty">-</span>
            </template>
          </el-table-column>
          <el-table-column label="书籍名称" align="center" prop="name" min-width="140" :show-overflow-tooltip="true" />
          <el-table-column label="作者" align="center" prop="author" width="110" :show-overflow-tooltip="true" />
          <el-table-column label="类目" align="center" min-width="140" :show-overflow-tooltip="true">
            <template slot-scope="scope">{{ categoryNames(scope.row.categoryIds) }}</template>
          </el-table-column>
          <el-table-column label="书架号" align="center" width="100">
            <template slot-scope="scope">
              <dict-tag :options="dict.type.book_shelfs" :value="scope.row.shelfCode" />
            </template>
          </el-table-column>
          <el-table-column label="库存" align="center" prop="stockQuantity" width="70" />
          <el-table-column label="书籍描述" align="center" prop="description" min-width="160" :show-overflow-tooltip="true" />
          <el-table-column label="创建人" align="center" prop="createdByName" width="100" :show-overflow-tooltip="true" />
          <el-table-column label="创建时间" align="center" prop="createdAt" width="160" />
          <el-table-column label="更新人" align="center" prop="updatedByName" width="100" :show-overflow-tooltip="true" />
          <el-table-column label="更新时间" align="center" prop="updatedAt" width="160" />
          <el-table-column label="操作" align="center" width="130" fixed="right" class-name="small-padding fixed-width">
            <template slot-scope="scope">
              <el-button
                size="mini"
                type="text"
                icon="el-icon-edit"
                @click="handleUpdate(scope.row)"
                v-hasPermi="['ssk:book:edit']"
              >修改</el-button>
              <el-button
                size="mini"
                type="text"
                icon="el-icon-delete"
                @click="handleDelete(scope.row)"
                v-hasPermi="['ssk:book:remove']"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          :page.sync="queryParams.pageNum"
          :limit.sync="queryParams.pageSize"
          @pagination="getList"
        />
      </el-col>
    </el-row>

    <!-- 图书新增/编辑弹窗 -->
    <book-form ref="bookForm" @success="getList" />
  </div>
</template>

<script>
import { listBook, delBook } from "@/api/ssk/book"
import { listBookCategory } from "@/api/ssk/bookCategory"
import BookForm from "./components/BookForm"
import CategoryTreeSelect from "./components/CategoryTreeSelect"
import ShelfSelect from "./components/ShelfSelect"

export default {
  name: "Book",
  components: { BookForm, CategoryTreeSelect, ShelfSelect },
  // 书架号取自字典 book_shelfs
  dicts: ["book_shelfs"],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 图书表格数据
      bookList: [],
      // 类目树数据，首节点为「全部」
      categoryTree: [],
      // 类目树筛选关键字
      categoryFilter: "",
      // 类目ID到名称的映射，用于列表展示类目名称
      categoryNameMap: {},
      // 类目树节点字段映射
      treeProps: { label: "title", children: "children" },
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        // 类目ID，为空表示全部
        categoryId: null,
        shelfCode: null,
        name: null,
        author: null
      }
    }
  },
  created() {
    this.getCategoryTree()
    this.getList()
  },
  watch: {
    // 搜索条件里的类目变化时，同步左侧树的选中状态
    "queryParams.categoryId"(val) {
      this.$nextTick(() => this.setCurrentCategory(val))
    },
    // 类目名称筛选
    categoryFilter(val) {
      if (this.$refs.categoryTree) {
        this.$refs.categoryTree.filter(val)
      }
    }
  },
  methods: {
    /** 查询图书列表 */
    getList() {
      this.loading = true
      listBook(this.queryParams).then(response => {
        this.bookList = response.rows
        this.total = response.total
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    /** 查询类目树 */
    getCategoryTree() {
      listBookCategory().then(response => {
        const tree = this.handleTree(response.data, "id", "parentId", "children")
        this.buildCategoryNameMap(tree)
        // 首节点固定为「全部」，默认选中该节点
        this.categoryTree = [{ id: 0, title: "全部", children: tree }]
        this.$nextTick(() => this.setCurrentCategory(this.queryParams.categoryId))
      })
    },
    /**
     * 组装类目ID到名称的映射
     * @param {Array} nodes 类目树
     */
    buildCategoryNameMap(nodes) {
      const map = {}
      const walk = list => {
        (list || []).forEach(node => {
          map[node.id] = node.title
          walk(node.children)
        })
      }
      walk(nodes)
      this.categoryNameMap = map
    },
    /**
     * 设置左侧类目树的选中节点
     * @param {Number} categoryId 类目ID，为空时选中「全部」
     */
    setCurrentCategory(categoryId) {
      const tree = this.$refs.categoryTree
      if (tree) {
        tree.setCurrentKey(categoryId || 0)
      }
    },
    /**
     * 类目树节点点击
     * @param {Object} data 节点数据
     */
    handleCategoryClick(data) {
      // 「全部」节点不附加类目条件，其余节点查询该节点及其子节点下的图书
      this.queryParams.categoryId = data.id === 0 ? null : data.id
      this.handleQuery()
    },
    /**
     * 类目树节点过滤
     * @param {String} value 关键字
     * @param {Object} data  节点数据
     */
    filterCategoryNode(value, data) {
      if (!value) {
        return true
      }
      return (data.title || "").indexOf(value) !== -1
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.queryParams.categoryId = null
      this.queryParams.shelfCode = null
      this.queryParams.name = null
      this.queryParams.author = null
      this.handleQuery()
    },
    /**
     * 多选框选中数据
     * @param {Array} selection 选中的行
     */
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    /** 新增按钮操作，左侧选中了具体类目时自动带入 */
    handleAdd() {
      this.$refs.bookForm.open(null, this.queryParams.categoryId)
    },
    /**
     * 修改按钮操作
     * @param {Object} row 当前行数据，批量操作时不传
     */
    handleUpdate(row) {
      this.$refs.bookForm.open({ id: row && row.id ? row.id : this.ids[0] })
    },
    /**
     * 删除按钮操作
     * @param {Object} row 当前行数据，批量操作时不传
     */
    handleDelete(row) {
      const ids = row && row.id ? [row.id] : this.ids
      const tip = row && row.id
        ? `是否确认删除图书「${row.name}」？`
        : `是否确认删除选中的 ${ids.length} 本图书？`
      this.$modal.confirm(tip).then(() => {
        return delBook(ids.join(","))
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /**
     * 类目名称展示，多个类目以顿号分隔
     * @param {String} categoryIds 逗号分隔的类目ID集
     */
    categoryNames(categoryIds) {
      if (!categoryIds) {
        return "-"
      }
      const names = String(categoryIds)
        .split(",")
        .map(id => this.categoryNameMap[id.trim()])
        .filter(name => name)
      return names.length ? names.join("、") : "-"
    },
    /**
     * 图片相对路径转可访问地址
     * @param {String} path 图片相对路径
     */
    imageUrl(path) {
      if (!path) {
        return ""
      }
      if (/^https?:\/\//.test(path)) {
        return path
      }
      return process.env.VUE_APP_BASE_API + path
    },
    /**
     * 图片集预览地址列表
     * @param {Object} row 图书数据
     */
    previewImages(row) {
      return String(row.images || "")
        .split(",")
        .filter(path => path)
        .map(path => this.imageUrl(path.trim()))
    }
  }
}
</script>

<style scoped lang="scss">
.category-panel {
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  min-height: 520px;
  max-height: 760px;
  overflow: auto;

  &__title {
    margin-bottom: 8px;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  &__filter {
    margin-bottom: 8px;
  }
}

.book-cover {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  background-color: #f5f7fa;

  &__empty {
    color: #c0c4cc;
  }
}
</style>
