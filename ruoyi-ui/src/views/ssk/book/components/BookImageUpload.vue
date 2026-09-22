<template>
  <div class="book-image-upload">
    <!-- 图片集上传：横向排列、正方形占位、可删除、可拖拽排序，添加按钮始终在最后 -->
    <el-upload
      ref="imageUpload"
      :action="uploadUrl"
      :headers="headers"
      list-type="picture-card"
      :multiple="true"
      :limit="limit"
      :file-list="fileList"
      :disabled="disabled"
      :class="{ hide: fileList.length >= limit }"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :on-preview="handlePreview"
      :on-exceed="handleExceed"
      :before-upload="handleBeforeUpload"
    >
      <i class="el-icon-plus"></i>
    </el-upload>

    <div class="book-image-upload__tip">
      最多上传 {{ limit }} 张图片，单张不超过 {{ fileSize }}M；<b>第一张图为封面图</b>，按住图片可拖拽调整顺序。
    </div>

    <!-- 图片预览 -->
    <el-dialog :visible.sync="previewVisible" title="图片预览" width="700px" append-to-body>
      <img :src="previewUrl" style="display: block; max-width: 100%; margin: 0 auto" />
    </el-dialog>
  </div>
</template>

<script>
import { getToken } from "@/utils/auth"
import { isExternal } from "@/utils/validate"
import Sortable from "sortablejs"

/**
 * 图书图片集上传组件
 *
 * 对外取值约定：v-model 为英文逗号分隔的图片相对路径字符串（如 "/profile/upload/xxx.png"），
 * 顺序即图片集顺序，第一张图即为封面。图片数量达到上限后自动隐藏添加按钮。
 */
export default {
  name: "BookImageUpload",
  props: {
    // 图片集，英文逗号分隔
    value: {
      type: [String, Array],
      default: ""
    },
    // 最多上传张数
    limit: {
      type: Number,
      default: 5
    },
    // 单张图片大小上限（MB）
    fileSize: {
      type: Number,
      default: 5
    },
    // 是否禁用
    disabled: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      baseUrl: process.env.VUE_APP_BASE_API,
      uploadUrl: process.env.VUE_APP_BASE_API + "/common/upload",
      headers: {
        Authorization: "Bearer " + getToken()
      },
      // 当前图片列表，元素形如 { name: 相对路径, url: 可访问地址 }
      fileList: [],
      // 预览相关
      previewVisible: false,
      previewUrl: "",
      // 拖拽实例
      sortable: null
    }
  },
  watch: {
    // 外部值变化时同步图片列表
    value: {
      handler(val) {
        this.fileList = this.toFileList(val)
      },
      immediate: true
    }
  },
  mounted() {
    this.$nextTick(() => this.initSortable())
  },
  beforeDestroy() {
    // 组件销毁时释放拖拽实例，避免残留监听
    if (this.sortable) {
      this.sortable.destroy()
      this.sortable = null
    }
  },
  methods: {
    /**
     * 把逗号分隔的图片集转换为上传组件需要的列表
     * @param {String|Array} val 图片集
     */
    toFileList(val) {
      const paths = Array.isArray(val) ? val : String(val || "").split(",")
      const list = []
      paths.forEach(path => {
        const trimmed = String(path || "").trim()
        if (trimmed === "") {
          return
        }
        list.push({ name: trimmed, url: this.toDisplayUrl(trimmed) })
      })
      return list
    },
    /**
     * 相对路径转可访问地址
     * @param {String} path 图片相对路径
     */
    toDisplayUrl(path) {
      if (isExternal(path) || path.indexOf(this.baseUrl) === 0) {
        return path
      }
      return this.baseUrl + path
    },
    /**
     * 可访问地址转相对路径（入库时只存相对路径）
     * @param {String} url 图片地址
     */
    toRelativeUrl(url) {
      const text = String(url || "")
      return text.indexOf(this.baseUrl) === 0 ? text.substring(this.baseUrl.length) : text
    },
    /** 向外抛出图片集，顺序即当前列表顺序 */
    emitInput() {
      const paths = this.fileList
        .map(item => this.toRelativeUrl(item.url))
        .filter(path => path !== "")
      this.$emit("input", paths.join(","))
    },
    /** 初始化拖拽排序 */
    initSortable() {
      if (this.disabled || this.sortable) {
        return
      }
      const wrapper = this.$refs.imageUpload
      const element = wrapper && wrapper.$el ? wrapper.$el.querySelector(".el-upload-list") : null
      if (!element) {
        return
      }
      this.sortable = Sortable.create(element, {
        animation: 150,
        onEnd: evt => {
          const list = this.fileList.slice()
          const moved = list.splice(evt.oldIndex, 1)[0]
          if (!moved) {
            return
          }
          list.splice(evt.newIndex, 0, moved)
          this.fileList = list
          // 顺序变化后第一张图即新的封面
          this.emitInput()
        }
      })
    },
    /**
     * 上传前校验：图片格式、文件名、大小
     * @param {Object} file 待上传文件
     */
    handleBeforeUpload(file) {
      const isImage = (file.type && file.type.indexOf("image") > -1) ||
        /\.(png|jpe?g|gif|bmp|webp)$/i.test(file.name)
      if (!isImage) {
        this.$modal.msgError("只能上传图片文件")
        return false
      }
      if (file.name.indexOf(",") > -1) {
        this.$modal.msgError("图片文件名不能包含英文逗号")
        return false
      }
      if (file.size / 1024 / 1024 > this.fileSize) {
        this.$modal.msgError(`单张图片大小不能超过 ${this.fileSize}M`)
        return false
      }
      if (this.fileList.length >= this.limit) {
        this.$modal.msgError(`最多上传 ${this.limit} 张图片`)
        return false
      }
      return true
    },
    /**
     * 上传成功回调
     * @param {Object} res  后端返回结果
     * @param {Object} file 当前文件
     */
    handleSuccess(res, file) {
      if (res && res.code === 200 && (res.fileName || res.url)) {
        const path = res.fileName || res.url
        const list = this.fileList.slice()
        list.push({ name: path, url: this.toDisplayUrl(path) })
        this.fileList = list
        this.emitInput()
        // 首次上传后列表元素才渲染出来，此时补一次拖拽初始化
        this.$nextTick(() => this.initSortable())
      } else {
        this.$modal.msgError((res && res.msg) || "图片上传失败，请重试")
        // 重置列表，去掉上传失败的占位项
        this.fileList = this.fileList.slice()
      }
    },
    /** 上传失败回调 */
    handleError() {
      this.$modal.msgError("图片上传失败，请重试")
      this.fileList = this.fileList.slice()
    },
    /**
     * 删除图片
     * @param {Object} file  被删除的文件
     * @param {Array}  files 删除后的文件列表
     */
    handleRemove(file, files) {
      if (Array.isArray(files)) {
        this.fileList = files.slice()
      } else {
        this.fileList = this.fileList.filter(item => item.name !== file.name)
      }
      this.emitInput()
    },
    /** 超出数量限制 */
    handleExceed() {
      this.$modal.msgError(`最多上传 ${this.limit} 张图片`)
    },
    /**
     * 预览图片
     * @param {Object} file 当前文件
     */
    handlePreview(file) {
      this.previewUrl = file.url
      this.previewVisible = true
    }
  }
}
</script>

<style scoped lang="scss">
.book-image-upload {
  // 每个图片占位为正方形
  ::v-deep .el-upload-list--picture-card .el-upload-list__item,
  ::v-deep .el-upload--picture-card {
    width: 100px;
    height: 100px;
    line-height: 100px;
  }

  // 上传后的图片按比例完整展示，不裁剪
  ::v-deep .el-upload-list--picture-card .el-upload-list__item-thumbnail {
    width: 100%;
    height: 100%;
    object-fit: contain;
    background-color: #f5f7fa;
  }

  // 拖拽排序时给出可拖动提示
  ::v-deep .el-upload-list--picture-card .el-upload-list__item {
    cursor: move;
  }

  &__tip {
    margin-top: 6px;
    font-size: 12px;
    line-height: 20px;
    color: #909399;

    b {
      color: #f56c6c;
    }
  }
}

// 图片数量达到上限后隐藏添加按钮
::v-deep.hide .el-upload--picture-card {
  display: none;
}
</style>
