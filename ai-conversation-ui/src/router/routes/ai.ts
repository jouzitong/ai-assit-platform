import type { RouteRecordRaw } from 'vue-router'

export const aiRoutes: RouteRecordRaw[] = [
  {
    path: '/ai/chat',
    name: 'ai-chat',
    component: () => import('../../modules/ai-chat/views/ChatWorkspaceView.vue'),
  },
]
