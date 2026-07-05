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
        meta: { title: 'Login' },
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
