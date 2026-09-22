<template>
  <!-- 图书 新增/编辑弹窗，通过父组件 ref 调用 open 方法打开 -->
  <el-dialog
    :title="title"
    :visible.sync="visible"
    width="720px"
    append-to-body
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="90px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="书籍名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入书籍名称" maxlength="255" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="作者" prop="author">
            <el-input v-model="form.author" placeholder="请输入作者姓名" maxlength="255" show-word-limit />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="书架号" prop="shelfCode">
            <shelf-select v-model="form.shelfCode" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="库存" prop="stockQuantity">
            <el-input-number v-model="form.stockQuantity" :min="0" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="类目" prop="categoryIds">
        <category-tree-select v-model="form.categoryIds" :multiple="true" placeholder="请选择类目，支持多选" />
      </el-form-item>
      <el-form-item label="书籍描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="请输入书籍描述"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="图片集" prop="images">
        <book-image-upload v-model="form.images" :limit="5" :file-size="5" />
      </el-form-item>
    </el-form>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      <el-button @click="visible = false">取 消</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { getBook, addBook, updateBook } from "@/api/ssk/book"
import CategoryTreeSelect from "./CategoryTreeSelect"
import ShelfSelect from "./ShelfSelect"
import BookImageUpload from "./BookImageUpload"

export default {
  name: "BookForm",
  components: { CategoryTreeSelect, ShelfSelect, BookImageUpload },
  data() {
    return {
      // 弹窗标题
      title: "",
      // 弹窗显示状态
      visible: false,
      // 提交中状态，防止重复点击提交
      submitting: false,
      // 表单数据
      form: {},
      // 表单校验规则，字段必填与长度与后端保持一致
      rules: {
        name: [
          { required: true, message: "书籍名称不能为空", trigger: "blur" },
          { max: 255, message: "书籍名称长度不能超过255个字符", trigger: "blur" }
        ],
        author: [
          { required: true, message: "作者不能为空", trigger: "blur" },
          { max: 255, message: "作者长度不能超过255个字符", trigger: "blur" }
        ],
        shelfCode: [
          { required: true, message: "请选择书架号", trigger: "change" }
        ],
        stockQuantity: [
          { required: true, message: "库存不能为空", trigger: "blur" }
        ],
        categoryIds: [
          { required: true, message: "请至少选择一个类目", trigger: "change" }
        ],
        description: [
          { required: true, message: "书籍描述不能为空", trigger: "blur" },
          { max: 500, message: "书籍描述长度不能超过500个字符", trigger: "blur" }
        ]
      }
    }
  },
  methods: {
    /**
     * 打开弹窗
     * @param {Object} row              编辑时传入当前行数据（至少包含 id），新增时传 null
     * @param {Number} defaultCategoryId 新增时左侧类目树选中的类目ID，用于自动带入类目
     */
    open(row, defaultCategoryId) {
      this.reset()
      if (row && row.id) {
        this.title = "编辑图书"
        // 详情加载成功后再打开弹窗。若失败（如该图书已被他人删除），保持关闭状态，
        // 避免弹出一个空表单让用户以为可以编辑，提交后却报"修改失败"。
        this.loadDetail(row.id)
          .then(() => {
            this.visible = true
          })
          .catch(() => {
            this.visible = false
          })
        return
      }
      this.title = "新增图书"
      // 左侧类目树选中具体类目时，自动带入为图书类目
      if (defaultCategoryId) {
        this.form.categoryIds = String(defaultCategoryId)
      }
      this.visible = true
    },
    /** 表单重置 */
    reset() {
      this.form = {
        id: undefined,
        name: undefined,
        author: undefined,
        description: undefined,
        // 库存默认 1，与表结构默认值保持一致
        stockQuantity: 1,
        // 类目为空字符串，组件会解析为空数组
        categoryIds: "",
        shelfCode: null,
        images: ""
      }
      this.submitting = false
      this.$nextTick(() => {
        if (this.$refs.form) {
          this.$refs.form.clearValidate()
        }
      })
    },
    /**
     * 加载图书详细
     * @param {Number} id 图书ID
     */
    loadDetail(id) {
      return getBook(id).then(response => {
        const data = response.data
        this.form = {
          id: data.id,
          name: data.name,
          author: data.author,
          description: data.description,
          stockQuantity: data.stockQuantity === null || data.stockQuantity === undefined ? 1 : data.stockQuantity,
          categoryIds: data.categoryIds || "",
          shelfCode: data.shelfCode || null,
          images: data.images || ""
        }
      })
    },
    /** 提交表单 */
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        // 封面由后端根据图片集的第一张图派生，此处不提交 cover
        const data = {
          id: this.form.id,
          name: this.form.name,
          author: this.form.author,
          description: this.form.description,
          stockQuantity: this.form.stockQuantity,
          categoryIds: this.form.categoryIds,
          shelfCode: this.form.shelfCode,
          images: this.form.images
        }
        this.submitting = true
        const request = data.id ? updateBook(data) : addBook(data)
        request.then(() => {
          this.$modal.msgSuccess(data.id ? "修改成功" : "新增成功")
          this.visible = false
          this.submitting = false
          this.$emit("success")
        }).catch(() => {
          this.submitting = false
        })
      })
    },
    /** 弹窗关闭后复位按钮状态 */
    handleClosed() {
      this.submitting = false
    }
  }
}
</script>
