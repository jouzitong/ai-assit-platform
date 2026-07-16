import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BlankLayout from '../layouts/BlankLayout/index.vue'
import ChatLayout from '../layouts/ChatLayout/index.vue'
import DefaultLayout from '../layouts/DefaultLayout/index.vue'
import { setupRouterGuards } from './guards'
import { aiRoutes } from './routes/ai'
import { dataRoutes } from './routes/data'
import { systemRoutes } from './routes/system'
import { testRoutes } from './routes/test'

const routes: RouteRecordRaw[] = [
  {
    path: '/auth',
    component: BlankLayout,
    children: [
      {
        path: 'login',
        name: 'auth-login',
        meta: { title: 'Login', public: true },
        component: () => import('../modules/auth/views/LoginView.vue'),
      },
    ],
  },
  {
    path: '/settings',
    component: BlankLayout,
    children: [
      {
        path: 'system/system-params/add',
        name: 'system-params-add',
        meta: { title: '新增系统参数' },
        component: () => import('../modules/system/views/SystemParamAddView.vue'),
      },
      {
        path: 'system/workflow',
        name: 'legacy-workflow-settings',
        redirect: (to) => {
          const legacyTab = Array.isArray(to.query.tab) ? to.query.tab[0] : to.query.tab
          const sectionByTab: Record<string, string> = {
            workflow: 'workflows',
            node: 'agents',
            skill: 'skills',
            tool: 'tools',
          }
          const { tab: _tab, ...query } = to.query
          return {
            path: `/settings/system/${sectionByTab[legacyTab || 'workflow'] || 'workflows'}`,
            query: legacyTab === 'node' ? { ...query, source: 'legacy-node' } : query,
          }
        },
      },
      {
        path: 'system/:section(agents|workflows|skills|tools)/:sourceKey?',
        name: 'agent-management-settings',
        meta: { title: 'Agent 与能力管理' },
        component: () => import('../modules/system/views/SystemSettingsView.vue'),
      },
      {
        path: 'system/:section?/:sourceKey?',
        name: 'system-settings',
        meta: { title: '系统设置' },
        component: () => import('../modules/system/views/SystemSettingsView.vue'),
      },
    ],
  },
  {
    path: '/',
    component: ChatLayout,
    children: [...aiRoutes],
  },
  {
    path: '/',
    component: DefaultLayout,
    children: [...systemRoutes, ...dataRoutes, ...testRoutes],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

setupRouterGuards(router)

export default router
