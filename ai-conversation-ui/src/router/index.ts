import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BlankLayout from '../layouts/BlankLayout/index.vue'
import ChatLayout from '../layouts/ChatLayout/index.vue'
import DefaultLayout from '../layouts/DefaultLayout/index.vue'
import { setupRouterGuards } from './guards'
import { aiRoutes } from './routes/ai'
import { dataRoutes } from './routes/data'
import { systemRoutes } from './routes/system'

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
    path: '/',
    component: DefaultLayout,
    children: [...systemRoutes, ...dataRoutes],
  },
  {
    path: '/',
    component: ChatLayout,
    children: [...aiRoutes],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/auth/login',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

setupRouterGuards(router)

export default router
