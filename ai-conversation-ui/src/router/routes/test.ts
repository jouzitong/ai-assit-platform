import type { RouteRecordRaw } from 'vue-router'

export const testRoutes: RouteRecordRaw[] = [
  {
    path: '/test/list',
    name: 'test-list',
    meta: { title: 'List Renderer Test' },
    component: () => import('../../modules/test/views/TestListView.vue'),
  },
]
