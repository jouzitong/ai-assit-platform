import { createRouter, createWebHistory } from 'vue-router'
import { clearSession, getToken, isTokenPastHalfLife, setSession } from './utils/session'
import { getCurrentUser, refreshAuth } from './api/auth'
import HomePage from './views/domain/home/overview/index.vue'
import QueryPage from './views/domain/query/assistant/index.vue'
import AttendancePage from './views/domain/emp/attendance/index.vue'
import PerformancePage from './views/domain/emp/performance/index.vue'
import CostPage from './views/domain/emp/cost/index.vue'
import ProfilePage from './views/domain/settings/profile/index.vue'
import SystemPage from './views/domain/settings/system/index.vue'
import LoginPage from './views/domain/auth/login/index.vue'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', component: LoginPage, meta: { title: '登录', public: true, plainLayout: true } },
  { path: '/home', component: HomePage, meta: { title: '首页' } },
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

async function refreshTokenIfNeeded(token) {
  if (!token || !isTokenPastHalfLife(token)) {
    return null
  }

  const response = await refreshAuth()
  const nextToken = response?.token ?? response?.data?.token
  const nextUser = response?.user ?? response?.data?.user

  if (!nextToken) {
    throw new Error('刷新接口未返回 token')
  }

  setSession({
    token: nextToken,
    user: nextUser
  })

  return nextToken
}

router.beforeEach(async (to) => {
  const token = getToken()

  if (to.meta?.public) {
    if (token && to.path === '/login') {
      try {
        await refreshTokenIfNeeded(token)
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
    await refreshTokenIfNeeded(token)
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
