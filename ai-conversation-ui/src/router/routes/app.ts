import type { RouteRecordRaw } from 'vue-router'

export const appRoutes: RouteRecordRaw[] = [
  {
    path: ':mode/:code',
    name: 'render-app',
    meta: {
      title: '动态应用',
      fullscreen: true,
    },
    component: () => import('../../modules/render/views/RenderRuntimeView.vue'),
  },
]
