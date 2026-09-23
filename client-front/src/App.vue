<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script>
/**
 * 终端根组件。
 *
 * <p>只放一个路由出口：终端没有后台那种「框架页 + 多页签」的结构，
 * 顶部栏与菜单都在 Layout 里。</p>
 */
export default {
  name: 'App'
}
</script>
