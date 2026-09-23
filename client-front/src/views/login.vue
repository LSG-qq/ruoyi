<template>
  <div class="login">
    <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="login-form">
      <h3 class="title">图书借阅终端</h3>
      <p class="subtitle">请使用囚号登录</p>

      <!-- 登录名就是囚号，不做「用户名/囚号」两套输入 -->
      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          type="text"
          auto-complete="off"
          placeholder="囚号"
          prefix-icon="el-icon-user"
          size="large"
        />
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          auto-complete="off"
          placeholder="口令"
          prefix-icon="el-icon-lock"
          size="large"
          show-password
          @keyup.enter.native="handleLogin"
        />
      </el-form-item>

      <!-- captchaEnabled 为 false 时后端关掉了验证码，这里整块不渲染 -->
      <el-form-item v-if="captchaEnabled" prop="code">
        <el-input
          v-model="loginForm.code"
          auto-complete="off"
          placeholder="验证码"
          prefix-icon="el-icon-key"
          size="large"
          class="code-input"
          @keyup.enter.native="handleLogin"
        >
          <template slot="append">
            <!-- 点图片换一张；验证码是一次性的，登录失败后也要重取（见 handleLogin） -->
            <div class="login-code" @click="getCode">
              <img :src="codeUrl" class="login-code-img" alt="验证码" />
            </div>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item>
        <el-checkbox v-model="loginForm.rememberMe">记住囚号</el-checkbox>
      </el-form-item>

      <el-form-item style="width: 100%">
        <el-button
          :loading="loading"
          size="large"
          type="primary"
          class="login-button"
          @click.native.prevent="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 终端不提供自助注册：账号由管理员在后台创建并分配「囚犯」角色 -->
    <div class="el-login-footer">
      <span>囚号即登录名；忘记口令请联系管理员</span>
    </div>
  </div>
</template>

<script>
import Cookies from 'js-cookie'
import { getCodeImg } from '@/api/login'

/**
 * 终端登录页。
 *
 * <p>与管理后台登录页的两点不同：一是登录名是囚号；二是**没有注册入口** ——
 * 终端不允许自助注册，账号由管理员在后台建（并分配「囚犯」角色），
 * 否则任何人都能给自己开一个进终端的账号。</p>
 *
 * <p>还有一个容易踩的细节：验证码是**一次性**的（后端校验后立刻从 redis 删掉），
 * 所以登录失败后必须重新取一张，否则用户第二次提交必然报「验证码已失效」，
 * 看起来像是登录功能坏了。</p>
 *
 * <p>「记住囚号」只写 `client-username` 这个 Cookie，**不记口令**：终端多放在公共区域，
 * 浏览器里留一份明文口令等于把账号交出去。名字带 client 前缀是为了与后台的
 * 同名 Cookie 区分开（两个前端可能在同一台机器上被同时打开）。</p>
 */
export default {
  name: 'ClientLogin',

  data() {
    return {
      /** 验证码图片的 base64 data url */
      codeUrl: '',

      /** 后端是否开启验证码；关掉时隐藏输入框、也不做必填校验 */
      captchaEnabled: true,

      /** 提交中标志，防重复点击 */
      loading: false,

      /** 登录表单 */
      loginForm: {
        username: '',
        password: '',
        rememberMe: false,
        code: '',
        uuid: ''
      },

      /** 表单校验规则：三项必填，与后端 LoginBody 的校验口径一致 */
      loginRules: {
        username: [{ required: true, trigger: 'blur', message: '请输入囚号' }],
        password: [{ required: true, trigger: 'blur', message: '请输入口令' }],
        code: [{ required: true, trigger: 'change', message: '请输入验证码' }]
      }
    }
  },

  created() {
    this.getCode()
    this.getCookie()
  },

  methods: {
    /**
     * 取一张验证码（同时刷新图片与 uuid）。
     *
     * <p>图片与 uuid 必须成对刷新：只换图片不换 uuid 会校验失败，
     * 只换 uuid 不换图片则用户看到的字符与后端存的对不上。</p>
     */
    getCode() {
      getCodeImg().then(res => {
        // 后端可能关掉验证码，此时不要求用户填写
        this.captchaEnabled = res.captchaEnabled === undefined ? true : res.captchaEnabled
        if (this.captchaEnabled) {
          this.codeUrl = 'data:image/gif;base64,' + res.img
          this.loginForm.uuid = res.uuid
        }
      })
    },

    /** 回填上次记住的囚号（口令从不回填） */
    getCookie() {
      const username = Cookies.get('client-username')
      if (username) {
        this.loginForm.username = username
        this.loginForm.rememberMe = true
      }
    },

    /**
     * 提交登录。
     *
     * <p>两条分支都要能「回到可再次点击的状态」：</p>
     * <ul>
     *   <li>失败：复位 loading 并**换一张验证码**（当前这张已被后端消费掉）；</li>
     *   <li>成功但跳转失败：同样要复位 loading。跳转失败不常见（多为
     *       NavigationDuplicated），但若不复位，按钮会永远停在「登 录 中...」、
     *       用户既进不去也没法重试，只能刷新页面。</li>
     * </ul>
     */
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (!valid) {
          return
        }
        this.loading = true

        if (this.loginForm.rememberMe) {
          // 只记囚号，不记口令
          Cookies.set('client-username', this.loginForm.username, { expires: 30 })
        } else {
          Cookies.remove('client-username')
        }

        this.$store.dispatch('Login', this.loginForm).then(() => {
          // 登录成功回到原本要去的页面（由路由守卫带上）；没有就回首页
          const redirect = this.$route.query.redirect
          this.$router.push({ path: redirect || '/' }).catch(err => {
            // 不要把这里的错误吞掉：跳转失败时页面会停在登录页，
            // 控制台再一片安静的话，看起来就像「登录成功了但没有任何反应」
            console.error('[client-front] 登录成功，但页面跳转失败：', err)
            this.loading = false
          })
        }).catch(() => {
          this.loading = false
          // 验证码已作废，换一张再让用户重试
          if (this.captchaEnabled) {
            this.getCode()
          }
        })
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.login {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  background-color: var(--client-bg-page);
}

.title {
  margin: 0 0 6px;
  font-size: var(--client-font-title);
  color: var(--client-text-primary);
  text-align: center;
}

.subtitle {
  margin: 0 0 26px;
  font-size: var(--client-font-base);
  color: var(--client-text-secondary);
  text-align: center;
}

.login-form {
  width: 400px;
  padding: 34px 32px 20px;
  background: var(--client-bg-card);
  border-radius: var(--client-radius-card);
  /* 卡片浮在灰底上，用一层极淡的阴影拉开层次，而不是加深边框 */
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.08);

  .el-form-item {
    margin-bottom: 22px;
  }
}

/* 验证码贴在输入框右侧的 append 槽里，去掉它的内边距让图片铺满 */
.code-input ::v-deep .el-input-group__append {
  padding: 0;
  overflow: hidden;
}

.login-code {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: var(--client-tap-height);
  cursor: pointer;

  .login-code-img {
    width: 120px;
    height: var(--client-tap-height);
  }
}

.login-button {
  width: 100%;
  font-size: var(--client-font-md);
  /* 两个字拉开间距，与后台登录页观感一致 */
  letter-spacing: 4px;
}

.el-login-footer {
  margin-top: 22px;
  font-size: var(--client-font-xs);
  color: var(--client-text-secondary);
}
</style>
