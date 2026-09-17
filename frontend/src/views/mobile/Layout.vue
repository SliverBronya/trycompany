<template>
  <div class="m-app">
    <!-- 内容区：底部留出 tab 的高度，最后一条记录不会被挡住 -->
    <div class="m-body">
      <router-view v-slot="{ Component }">
        <keep-alive :include="['MHome', 'MRecords']">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </div>

    <nav class="m-tabbar">
      <div
        v-for="tab in tabs"
        :key="tab.path"
        class="m-tabbar-item"
        :class="{ 'is-active': isActive(tab.path) }"
        @click="go(tab.path)"
      >
        <el-icon :size="22"><component :is="tab.icon" /></el-icon>
        <span>{{ tab.label }}</span>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled, List, Clock, Reading, ChatDotRound } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/m/home', label: '首页', icon: 'HomeFilled' },
  { path: '/m/records', label: '巡田', icon: 'List' },
  { path: '/m/tasks', label: '复查', icon: 'Clock' },
  { path: '/m/knowledge', label: '知识库', icon: 'Reading' },
  { path: '/m/qa', label: '问答', icon: 'ChatDotRound' }
]

// 子页面（如新建巡田 / 记录详情）也要让所属 tab 保持高亮，
// 否则一进详情页底部就全灭了，用户会以为点错了
function isActive(path) {
  return route.path === path || route.path.indexOf(path + '/') === 0
}

function go(path) {
  if (route.path === path) return
  router.replace(path)
}
</script>

<style scoped>
.m-app {
  /* dvh：手机上地址栏/键盘会改变可视高度，vh 不会跟着变，用 dvh 更稳 */
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--tz-paper);
}
.m-body {
  padding-bottom: calc(58px + env(safe-area-inset-bottom));
}
.m-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  background: var(--tz-surface);
  border-top: 1px solid var(--tz-line);
  padding-bottom: env(safe-area-inset-bottom);
  /* 阴影带上背景色调（不用纯灰黑），在米白底上才不显脏 */
  box-shadow: 0 -1px 8px rgba(22, 48, 42, 0.06);
}
.m-tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  height: 58px;
  color: var(--tz-ink-3);
  font-size: 11px;
  -webkit-tap-highlight-color: transparent;
}
.m-tabbar-item.is-active {
  color: var(--tz-primary);
  font-weight: 600;
}
</style>
