import type { RouteRecordRaw } from 'vue-router'

export const dataRoutes: RouteRecordRaw[] = [
  {
    path: '/data/query',
    name: 'data-query',
    component: () => import('../../modules/data-query/views/DataQueryView.vue'),
  },
]
