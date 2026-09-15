<template>
  <router-view />
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import useSettingsStore from '@/store/modules/settings'
import { handleThemeStyle } from '@/utils/theme'

const route = useRoute()
const router = useRouter()

/**
 * 窄屏时自动进入手机版（/m）。
 *
 * 判断的是**视口宽度**而不是 UA：同一个手机浏览器切到「桌面版网站」时 UA 可能
 * 还是手机，但视口已经变宽，那时应该给桌面版。反过来同理。
 *
 * 用户可以显式选一次：手机版里「切换到电脑版」会写 localStorage，之后不再自动跳。
 * 不做这个开关的话，窄屏用户想看电脑版就永远看不到了。
 */
const MOBILE_WIDTH = 768

function shouldUseMobile() {
  if (localStorage.getItem('tz-prefer') === 'desktop') return false
  const p = route.path
  // 登录/注册/错误页与手机版自身不参与跳转，否则会来回弹
  if (p.indexOf('/m/') === 0 || p === '/login' || p === '/register' || p === '/logout') return false
  return window.innerWidth <= MOBILE_WIDTH
}

function syncLayout() {
  if (shouldUseMobile()) {
    router.replace('/m/home')
  }
}

onMounted(() => {
  nextTick(() => {
    // 初始化主题样式
    handleThemeStyle(useSettingsStore().theme)
  })
  syncLayout()
  // 只监听「跨过断点」的那一刻，不做防抖：尺寸变化很频繁，每次都跑判断没必要
  let wasMobile = window.innerWidth <= MOBILE_WIDTH
  window.addEventListener('resize', () => {
    const isMobile = window.innerWidth <= MOBILE_WIDTH
    if (isMobile !== wasMobile) {
      wasMobile = isMobile
      syncLayout()
    }
  })
})
</script>
