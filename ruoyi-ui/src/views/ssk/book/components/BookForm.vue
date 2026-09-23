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
    <!-- 字段与 Book 实体一一对应；校验规则与实体注解、DDL 长度保持一致 -->
    <el-form ref="form" :model="form" :rules="rules" label-width="90px">
      <!-- 基础信息：名称与作者并排展示，长度上限对应 ssk_book 的 varchar(255) -->
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
      <!-- 存放信息：书架号取值于字典 book_shelfs，库存的处理方式见下方注释 -->
      <el-row>
        <el-col :span="12">
          <el-form-item label="书架号" prop="shelfCode">
            <shelf-select v-model="form.shelfCode" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item prop="stockQuantity">
            <span slot="label">
              库存
              <el-tooltip
                v-if="form.id"
                content="库存由借出/归还自动变动，此处显示的是打开弹窗时的值，编辑图书不修改库存"
                placement="top"
              >
                <i class="el-icon-question" />
              </el-tooltip>
            </span>
            <!-- 库存是借阅运营量：新增时可填初始库存，编辑时只读展示。
                 编辑不提交库存，库存的日常变动走借出/归还，避免表单里的过期值覆盖库存。 -->
            <el-input-number
              v-if="!form.id"
              v-model="form.stockQuantity"
              :min="0"
              controls-position="right"
              style="width: 100%"
            />
            <el-input v-else :value="form.stockQuantity" disabled />
          </el-form-item>
        </el-col>
      </el-row>
      <!-- 类目：可多选，提交为英文逗号拼接的 ID 串；后端会校验类目确实存在且至少选一个 -->
      <el-form-item label="类目" prop="categoryIds">
        <category-tree-select v-model="form.categoryIds" :multiple="true" placeholder="请选择类目，支持多选" />
      </el-form-item>
      <!-- 简介：最长 500 字，与实体 @Size(max = 500) 一致 -->
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
      <!-- 图片集：最多 5 张、单张不超过 5MB；封面由后端取第一张，前端不提交 cover -->
      <el-form-item label="图片集" prop="images">
        <book-image-upload v-model="form.images" :limit="5" :file-size="5" />
      </el-form-item>
    </el-form>
    <!-- 提交中禁用按钮，避免重复点击产生两条数据 -->
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
      // 弹窗标题，按新增/编辑切换
      title: "",
      // 弹窗显示状态：新增时直接打开；编辑时等详情加载成功才置 true
      visible: false,
      // 提交中状态，防止重复点击提交
      submitting: false,
      // 表单数据：新增时由 reset 给默认值，编辑时由 loadDetail 整体覆盖
      form: {},
      // 表单校验规则：必填与长度与实体注解、DDL 保持一致。
      // 前端先拦一次只是为了少发一次无效请求，后端仍会独立校验，两边都不能省。
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
          {
            // 只在新增时必填。编辑模式该字段只读展示、不随表单提交（见 submitForm），
            // 若还按必填校验，等于让用户去满足一个他根本改不了也不提交的字段。
            required: true,
            validator: (rule, value, callback) => {
              if (this.form.id || (value !== null && value !== undefined)) {
                callback()
              } else {
                callback(new Error("库存不能为空"))
              }
            },
            trigger: "blur"
          }
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
     * 打开弹窗：按是否传 row 决定进新增模式还是编辑模式
     *
     * <p>编辑模式先加载详情、成功后才打开弹窗；加载失败（如该图书已被他人删除）保持关闭，
     * 避免弹出一个空表单让用户以为可以编辑，提交后却报"修改失败"。</p>
     *
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
    /**
     * 表单重置
     *
     * <p>这里用整体替换 form 对象的方式重置，所以不能依赖 resetFields()：它对动态赋值的字段不可靠。
     * 用 clearValidate() 单独清掉上一次留下的校验红字（需等 DOM 更新完再调）。</p>
     */
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
     * 加载图书详细并填充表单
     *
     * <p>库里的可空字段可能取到 NULL，这里统一兜底成表单能接受的初值（库存兜 1、类目兜空串），
     * 免得 undefined 传进子组件后渲染异常。</p>
     *
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
          // 编辑时库存只读展示当前值，不参与提交
          stockQuantity: data.stockQuantity === null || data.stockQuantity === undefined ? 1 : data.stockQuantity,
          categoryIds: data.categoryIds || "",
          shelfCode: data.shelfCode || null,
          images: data.images || ""
        }
      })
    },
    /**
     * 提交表单：按有无 id 决定调新增接口还是修改接口
     *
     * <p>组装请求体时有意排除两类字段：</p>
     * <ul>
     *     <li>cover：由后端按图片集第一张派生，前端传了反而可能与后端算出的不一致；</li>
     *     <li>stockQuantity：只在新增时提交初始库存；编辑时库存归借出/归还维护，不参与提交，
     *         否则弹窗里打开时的旧值会把停留期间发生的借出/归还结果覆盖掉。</li>
     * </ul>
     */
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        // 封面由后端根据图片集的第一张图派生，此处不提交 cover；
        // 库存只在新增时提交初始值，编辑时库存由借出/归还维护，不随表单提交。
        const data = {
          id: this.form.id,
          name: this.form.name,
          author: this.form.author,
          description: this.form.description,
          categoryIds: this.form.categoryIds,
          shelfCode: this.form.shelfCode,
          images: this.form.images
        }
        if (!data.id) {
          data.stockQuantity = this.form.stockQuantity
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
    /**
     * 弹窗关闭后复位按钮状态
     *
     * <p>点"取消"关闭不会走 submitForm 的回调，这里兜一下，
     * 否则下次打开时确定按钮还停在 loading 上。</p>
     */
    handleClosed() {
      this.submitting = false
    }
  }
}
</script>
