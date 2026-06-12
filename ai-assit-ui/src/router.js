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
import SystemOverviewPage from './views/domain/settings/system/views/overview.vue'
import SystemParamsPage from './views/domain/settings/system/views/params.vue'
import SystemComponentsPage from './views/domain/settings/system/views/components.vue'
import SystemPermissionsPage from './views/domain/settings/system/views/permissions.vue'
import SystemAiPage from './views/domain/settings/system/views/ai.vue'
import SystemDataSourcePage from './views/domain/settings/system/views/data-source.vue'
import SystemDataSourceManagePage from './views/domain/settings/system/views/data-source/manage.vue'
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
  {
    path: '/settings/system',
    component: SystemPage,
    meta: { title: '系统管理' },
    redirect: '/settings/system/overview',
    children: [
      { path: 'overview', component: SystemOverviewPage, meta: { title: '系统总览' } },
      { path: 'data-source', component: SystemDataSourcePage, meta: { title: '数据源配置' } },
      { path: 'data-source/:sourceKey', component: SystemDataSourceManagePage, meta: { title: '数据表管理' } },
      { path: 'params', component: SystemParamsPage, meta: { title: '系统参数' } },
      { path: 'components', component: SystemComponentsPage, meta: { title: '常用组件' } },
      { path: 'permissions', component: SystemPermissionsPage, meta: { title: '权限配置' } },
      { path: 'ai', component: SystemAiPage, meta: { title: 'AI 接入' } }
    ]
  }
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
