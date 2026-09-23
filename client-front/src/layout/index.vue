<template>
  <div class="client-layout">
    <!-- 顶部栏：左边是终端名称，右边是当前登录的囚号与退出入口 -->
    <header class="client-header">
      <div class="header-title">
        <i class="el-icon-reading"></i>
        <span>图书借阅终端</span>
      </div>
      <div class="header-user">
        <span class="user-text">
          {{ nickName || '—' }}
          <em v-if="prisonerNumber">（囚号 {{ prisonerNumber }}）</em>
        </span>
        <el-button type="text" icon="el-icon-switch-button" @click="handleLogout">退出</el-button>
      </div>
    </header>

    <!-- 顶部菜单：终端只有三页，横向排布比侧边栏更省空间 -->
    <el-menu
      :default-active="activeMenu"
      class="client-menu"
      mode="horizontal"
      background-color="#ffffff"
      text-color="#303133"
      active-text-color="#409eff"
      router
    >
      <el-menu-item v-for="item in menuRoutes" :key="item.path" :index="'/' + item.path">
        <i :class="item.meta.icon"></i>
        <span slot="title">{{ item.meta.title }}</span>
      </el-menu-item>
    </el-menu>

    <!-- 内容区：三个业务页共用这一层壳，具体内容由 router-view 渲染 -->
    <main class="client-main">
      <router-view />
    </main>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { constantRoutes } from '@/router'

/**
 * 终端布局：顶部栏 + 横向菜单 + 内容区。
 *
 * <p>菜单项直接从路由表派生，不额外维护一份菜单配置 ——
 * 多一份配置就多一处会忘记同步的地方。终端路由是写死的（见 router/index.js），
 * 因此这里也不需要权限过滤：囚犯角色没有任何权限位，能进哪几页由路由表决定。</p>
 *
 * <p>顶栏显示的是「姓名（囚号 20260001）」而不是登录名 —— 姓名便于现场核对，
 * 囚号才是业务上的唯一标识，两个都放在眼前省得来回对。</p>
 *
 * <p>顶栏高度取 62px：比 element-ui 默认的导航栏略高，长时间盯屏时头部不那么局促；
 * 终端屏幕上这点高度很划算，不必为了省几十像素把信息挤在一起。</p>
 */
export default {
  name: 'ClientLayout',

  computed: {
    ...mapGetters(['prisonerNumber', 'nickName']),

    /** 菜单数据：取根路由的 children（/login 那种 hidden 的路由不在里面） */
    menuRoutes() {
      const root = constantRoutes.find(route => route.path === '/')
      return root ? root.children : []
    },

    /** 当前高亮的菜单项：直接用当前路径，与菜单项的 index（'/' + path）一致 */
    activeMenu() {
      return this.$route.path
    }
  },

  created() {
    // 刷新页面后 store 是空的（令牌在 Cookie 里还在），
    // 囚号与姓名需要重新拉一次，否则顶栏会显示成空
    if (!this.prisonerNumber) {
      this.$store.dispatch('GetInfo').catch(() => {
        // 拉不到不算致命错误（例如令牌刚过期），
        // 后续任何一次业务请求拿到 401 时都会统一跳回登录页
      })
    }
  },

  methods: {
    /**
     * 退出登录。
     *
     * <p>先二次确认再退：终端放在公共区域，误触退出会打断正在办理的人。
     * 具体的清理逻辑在 store 的 LogOut 里（它会保证接口失败也清本地状态）。</p>
     */
    handleLogout() {
      this.$confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$store.dispatch('LogOut').then(() => {
          this.$router.push('/login')
        })
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
/* 纵向三栏：顶部栏与菜单固定高度，内容区吃掉剩余空间并自己滚动 */
.client-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.client-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 62px;
  padding: 0 24px;
  color: var(--client-bg-card);
  background-color: var(--client-primary);

  .header-title {
    font-size: var(--client-font-lg);
    font-weight: 600;
    letter-spacing: 1px;

    i {
      margin-right: var(--client-gap-sm);
    }
  }

  .header-user {
    display: flex;
    align-items: center;
    font-size: var(--client-font-md);

    /* 囚号用略低的透明度做次级信息，不与姓名抢注意力 */
    .user-text em {
      font-style: normal;
      opacity: 0.85;
    }

    /* 顶栏是蓝底，element-ui 的 text 按钮默认色在蓝底上看不清，这里改成白色 */
    .el-button--text {
      margin-left: 18px;
      color: var(--client-bg-card);
      font-size: var(--client-font-md);
    }
  }
}

.client-menu {
  /* flex: none 让菜单保持自身高度，不被内容区挤压 */
  flex: none;

  i {
    margin-right: 6px;
  }
}

.client-main {
  /* 撑满剩余高度并单独滚动，顶部栏与菜单始终留在视野里 */
  flex: 1;
  overflow: auto;
  padding: 18px;
}
</style>
