import type { RouteRecordRaw } from 'vue-router'

export const aiRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'chat-home',
    meta: { title: 'AI Chat' },
    component: () => import('../../modules/ai-chat/views/ChatWorkspaceView.vue'),
  },
  {
    path: '/c/:sessionId',
    name: 'chat-session',
    meta: { title: 'AI Chat Session' },
    component: () => import('../../modules/ai-chat/views/ChatWorkspaceView.vue'),
  },
  {
    path: '/g/:groupId',
    name: 'chat-group-home',
    meta: { title: 'AI Chat Group' },
    component: () => import('../../modules/ai-chat/views/ChatWorkspaceView.vue'),
  },
  {
    path: '/g/:groupId/c/:sessionId',
    name: 'chat-group-session',
    meta: { title: 'AI Chat Group Session' },
    component: () => import('../../modules/ai-chat/views/ChatWorkspaceView.vue'),
  },
]
