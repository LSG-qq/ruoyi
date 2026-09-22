<template>
  <!-- 图书类目 新增/编辑弹窗，通过父组件 ref 调用 open 方法打开 -->
  <el-dialog
    :title="title"
    :visible.sync="visible"
    width="600px"
    append-to-body
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="上级类目" prop="parentId">
        <treeselect
          v-model="form.parentId"
          :options="categoryOptions"
          :normalizer="normalizer"
          :default-expand-level="Infinity"
          placeholder="请选择上级类目"
        />
      </el-form-item>
      <el-row>
        <el-col :span="14">
          <el-form-item label="类目名称" prop="title">
            <el-input v-model="form.title" placeholder="请输入类目名称" maxlength="50" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="显示排序" prop="orderNum">
            <el-input-number v-model="form.orderNum" controls-position="right" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入备注"
          maxlength="255"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      <el-button @click="visible = false">取 消</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { listBookCategory, listBookCategoryExcludeSelf, getBookCategory, addBookCategory, updateBookCategory } from "@/api/ssk/bookCategory"
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"

export default {
  name: "BookCategoryForm",
  components: { Treeselect },
  data() {
    return {
      // 弹窗标题
      title: "",
      // 弹窗显示状态
      visible: false,
      // 提交中状态，防止重复点击提交
      submitting: false,
      // 上级类目下拉数据
      categoryOptions: [],
      // 表单数据
      form: {},
      // 表单校验规则
      rules: {
        title: [
          { required: true, message: "类目名称不能为空", trigger: "blur" },
          { max: 50, message: "类目名称长度不能超过50个字符", trigger: "blur" }
        ],
        orderNum: [
          { required: true, message: "显示排序不能为空", trigger: "blur" }
        ],
        remark: [
          { max: 255, message: "备注长度不能超过255个字符", trigger: "blur" }
        ]
      }
    }
  },
  methods: {
    /**
     * 打开弹窗
     * @param {Object} row 编辑时传入当前行数据，新增时传 null
     * @param {Object} parentRow 新增子类目时传入上级行数据，新增顶级类目时不传
     */
    open(row, parentRow) {
      this.reset()
      if (row) {
        this.title = "编辑图书类目"
        // 先回填表单再加载上级下拉，确保下拉能正确选中当前上级类目。
        // 两步都成功后才打开弹窗：若失败（如该类目已被他人删除），保持关闭状态，
        // 避免弹出空表单让用户以为可以编辑，提交后却报"修改失败"。
        this.loadDetail(row.id)
          .then(() => this.loadOptions(row.id))
          .then(() => {
            this.visible = true
          })
          .catch(() => {
            this.visible = false
          })
        return
      }
      this.title = parentRow ? "新增子类目" : "新增图书类目"
      this.form.parentId = parentRow ? parentRow.id : 0
      this.loadOptions(null)
      this.visible = true
    },
    /** 表单重置 */
    reset() {
      this.form = {
        id: undefined,
        // 下拉中使用 0 表示顶级类目，提交时再换算为 null
        parentId: 0,
        // 显示排序默认 0，排在最前
        orderNum: 0,
        title: undefined,
        remark: undefined
      }
      this.submitting = false
      this.$nextTick(() => {
        if (this.$refs.form) {
          this.$refs.form.clearValidate()
        }
      })
    },
    /**
     * 加载上级类目下拉数据
     * @param {Number} excludeId 编辑时传入当前类目ID，用于排除自身及其下级
     */
    loadOptions(excludeId) {
      const request = excludeId ? listBookCategoryExcludeSelf(excludeId) : listBookCategory()
      return request.then(response => {
        const tree = this.handleTree(response.data, "id", "parentId", "children")
        // 构造虚拟顶级节点，便于把类目直接挂在顶级下
        this.categoryOptions = [{ id: 0, title: "顶级类目", children: tree }]
      }).catch(() => {
        // 内部消化异常：上级下拉加载失败时至少保留「顶级类目」节点，
        // 避免下拉整个空掉。此处不再向外抛，弹窗的关闭与否由详情接口决定。
        this.categoryOptions = [{ id: 0, title: "顶级类目", children: [] }]
      })
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
    },
    /**
     * 加载类目详细
     * @param {Number} id 类目ID
     */
    loadDetail(id) {
      return getBookCategory(id).then(response => {
        const data = response.data
        this.form = {
          id: data.id,
          // 后端顶级类目 parentId 为 null，这里转换为下拉中的虚拟顶级节点 0
          parentId: data.parentId === null || data.parentId === undefined ? 0 : data.parentId,
          orderNum: data.orderNum === null || data.orderNum === undefined ? 0 : data.orderNum,
          title: data.title,
          remark: data.remark
        }
      })
    },
    /** 提交表单 */
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        const data = {
          id: this.form.id,
          // 虚拟顶级节点 0 换算为 null，与表结构 parent_id 的默认值保持一致
          parentId: this.form.parentId === 0 ? null : this.form.parentId,
          orderNum: this.form.orderNum === undefined ? 0 : this.form.orderNum,
          title: this.form.title,
          remark: this.form.remark
        }
        this.submitting = true
        const request = data.id ? updateBookCategory(data) : addBookCategory(data)
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
