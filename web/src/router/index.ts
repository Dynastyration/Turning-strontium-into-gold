import { createRouter, createWebHashHistory } from 'vue-router'
import MainLayout from '@/layout/MainLayout.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true } },
    {
      path: '/',
      component: MainLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '数据大屏', fullTitle: '结构健康监测数据大屏', brand: 'BN' } },
        { path: 'devices', name: 'devices', component: () => import('@/views/DeviceList.vue'), meta: { title: '设备管理' } },
        { path: 'sensors', name: 'sensors', component: () => import('@/views/SensorList.vue'), meta: { title: '传感器管理' } },
        { path: 'data', name: 'data', component: () => import('@/views/DataManage.vue'), meta: { title: '数据管理' } },
        { path: 'video', name: 'video', component: () => import('@/views/VideoMonitor.vue'), meta: { title: '视频监控' } },
        { path: 'alarms', name: 'alarms', component: () => import('@/views/AlarmCenter.vue'), meta: { title: '告警管理' } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem('bn_token')
  if (!token && !to.meta.public) {
    return { path: '/login' }
  }
  if (token && to.path === '/login') {
    return { path: '/dashboard' }
  }
  return true
})

export default router
