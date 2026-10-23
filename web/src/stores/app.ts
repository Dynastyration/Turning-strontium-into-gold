import { defineStore } from 'pinia'
import { metaApi, alarmApi } from '@/api'

export const useAppStore = defineStore('app', {
  state: () => ({
    theme: (localStorage.getItem('bn-theme') || 'dark') as 'dark' | 'light',
    projects: [] as any[],
    // 不按项目切分：大屏与各列表一律展示全部项目的数据
    projectId: null as number | null,
    unread: 0,
    // 宽屏默认展开左侧圆形按钮栏，窄屏默认收起成抽屉
    menuOpen: typeof window !== 'undefined' ? window.innerWidth > 920 : true
  }),
  getters: {
    projectName(state): string {
      const p = state.projects.find((x) => x.id === state.projectId)
      return p?.name || '全部项目'
    }
  },
  actions: {
    applyTheme() {
      document.documentElement.setAttribute('data-theme', this.theme)
      localStorage.setItem('bn-theme', this.theme)
    },
    toggleTheme() {
      this.theme = this.theme === 'dark' ? 'light' : 'dark'
      this.applyTheme()
    },
    // 项目列表只给设备表单的「所属项目」下拉用，不再决定看哪一份数据
    async loadProjects() {
      try {
        const list: any = await metaApi.projects()
        this.projects = list || []
      } catch {
        this.projects = []
      }
    },
    async loadUnread() {
      try {
        this.unread = Number(await alarmApi.unread()) || 0
      } catch {
        this.unread = 0
      }
    }
  }
})
