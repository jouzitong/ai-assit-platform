import { createRouter, createWebHistory } from 'vue-router'
import { clearSession, getToken } from './utils/session'
import { getCurrentUser } from './api/auth'
import IndexPage from './views/index.vue'
import QueryPage from './views/query.vue'
import AttendancePage from './views/attendance.vue'
import PerformancePage from './views/performance.vue'
import CostPage from './views/cost.vue'
import ProfilePage from './views/profile.vue'
import SystemPage from './views/system.vue'
import LoginPage from './views/login.vue'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', component: LoginPage, meta: { title: '登录', public: true, plainLayout: true } },
  { path: '/home', component: IndexPage, meta: { title: '首页' } },
  { path: '/query', component: QueryPage, meta: { title: '智能问数' } },
  { path: '/emp/attendance', component: AttendancePage, meta: { title: '考勤看板' } },
  { path: '/emp/performance', component: PerformancePage, meta: { title: '绩效洞察' } },
  { path: '/emp/cost', component: CostPage, meta: { title: '人力成本分析' } },
  { path: '/settings/profile', component: ProfilePage, meta: { title: '个人管理' } },
  { path: '/settings/system', component: SystemPage, meta: { title: '系统管理' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} | EMP Console` : 'EMP Console'
})

router.beforeEach(async (to) => {
  const token = getToken()

  if (to.meta?.public) {
    if (token && to.path === '/login') {
      try {
        await getCurrentUser()
        return { path: '/home' }
      } catch {
        clearSession()
      }
    }
    return true
  }

  if (!token) {
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }

  try {
    await getCurrentUser()
    return true
  } catch {
    clearSession()
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }
})

export default router
