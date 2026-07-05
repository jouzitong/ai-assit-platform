import type { RouteRecordRaw } from 'vue-router'

export const systemRoutes: RouteRecordRaw[] = [
  {
    path: '/system/users',
    name: 'system-users',
    component: () => import('../../modules/user/views/UserListView.vue'),
  },
]
