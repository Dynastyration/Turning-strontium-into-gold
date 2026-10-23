<template>
  <div class="app" :class="{ 'rail-open': app.menuOpen }">
    <div class="scrim" :class="{ show: app.menuOpen }" @click="app.menuOpen = false" />
    <nav class="rail" :class="{ collapsed: !app.menuOpen }">
      <div class="rail-brand" data-label="结构健康监测平台">BN</div>
      <template v-for="item in navItems" :key="item.to">
        <button
          class="rail-btn"
          :class="{ active: route.path === item.to }"
          :data-label="item.text"
          @click="go(item.to)"
        >
          <span v-html="item.icon" />
          <span v-if="item.to === '/alarms' && app.unread > 0" class="rail-dot" />
        </button>
      </template>
      <div class="rail-spacer" />
      <div class="rail-avatar" :data-label="userStore.displayName">{{ avatarText }}</div>
      <button class="rail-btn" data-label="退出登录" @click="logout">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
          <path d="M9 21H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3" /><path d="M16 17l5-5-5-5" /><path d="M21 12H9" />
        </svg>
      </button>
      <button class="rail-toggle" :title="app.menuOpen ? '收起菜单' : '展开菜单'" @click="app.menuOpen = !app.menuOpen">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9">
          <path d="M15 6l-6 6 6 6" />
        </svg>
      </button>
    </nav>

    <div class="main">
      <header class="topbar">
        <button id="menuBtn" class="btn-icon" @click="app.menuOpen = !app.menuOpen">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
            <path d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <h1 class="topbar-center" :class="{ big: !!route.meta.brand }">
          <span v-if="route.meta.brand" class="t-brand">{{ route.meta.brand }}</span>
          <span class="t-cn">{{ route.meta.fullTitle || route.meta.title || '数据大屏' }}</span>
        </h1>
        <div class="topbar-spacer" />
        <div class="topbar-actions">
          <button class="btn-icon" title="未处理告警" @click="router.push('/alarms')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
              <path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6" /><path d="M10 19a2 2 0 0 0 4 0" />
            </svg>
          </button>
          <button class="btn-icon" title="全屏" @click="toggleFullscreen">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
              <path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" />
            </svg>
          </button>
          <button class="btn-icon" title="切换明暗主题" @click="app.toggleTheme()">
            <svg v-if="app.theme === 'dark'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
              <circle cx="12" cy="12" r="4" />
              <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
              <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
            </svg>
          </button>
        </div>
      </header>

      <main class="viewport">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/format'

const app = useAppStore()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

// 窄屏点导航后自动收回，宽屏保持常驻
const isNarrow = () => window.innerWidth <= 920
watch(
  () => route.path,
  () => {
    if (isNarrow()) app.menuOpen = false
  }
)
function onResize() {
  app.menuOpen = !isNarrow()
}
function go(to: string) {
  router.push(to)
}

// 一条竖列到底，不分段、不加分组标题
const navItems = [
  {
    to: '/dashboard',
    text: '数据大屏',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="3" width="7.5" height="7.5" rx="1.5"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="1.5"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="1.5"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1.5"/></svg>'
  },
  {
    to: '/devices',
    text: '设备管理',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="4" width="18" height="7" rx="1.5"/><rect x="3" y="13" width="18" height="7" rx="1.5"/><path d="M7 7.5h.01M7 16.5h.01"/></svg>'
  },
  {
    to: '/sensors',
    text: '传感器管理',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M2 12h3l2-6 4 12 3-9 2 3h6"/></svg>'
  },
  {
    to: '/data',
    text: '数据管理',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><ellipse cx="12" cy="5.5" rx="7.5" ry="3"/><path d="M4.5 5.5v6c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3v-6"/><path d="M4.5 11.5v6c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3v-6"/></svg>'
  },
  {
    to: '/video',
    text: '视频监控',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="2.5" y="6" width="13" height="12" rx="2"/><path d="M15.5 10.5l6-3.5v10l-6-3.5"/></svg>'
  },
  {
    to: '/alarms',
    text: '告警管理',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6"/><path d="M10 19a2 2 0 0 0 4 0"/></svg>'
  }
]

const avatarText = computed(() => (userStore.displayName || '管').slice(0, 1))

function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {})
  else document.documentElement.requestFullscreen().catch(() => toast('当前环境不支持全屏'))
}

function logout() {
  userStore.logout()
  router.push('/login')
}

let timer = 0
onMounted(() => {
  app.applyTheme()
  app.loadProjects()
  app.loadUnread()
  if (!userStore.user) userStore.loadMe().catch(() => {})
  timer = window.setInterval(() => app.loadUnread(), 30000)
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  window.clearInterval(timer)
  window.removeEventListener('resize', onResize)
})
</script>
