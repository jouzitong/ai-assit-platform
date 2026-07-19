import type { RouteRecordRaw } from 'vue-router'

export const testRoutes: RouteRecordRaw[] = [
  {
    path: '/test/list',
    name: 'test-list',
    meta: { title: 'List Renderer Test' },
    component: () => import('../../modules/test/views/TestListView.vue'),
  },
  {
    path: '/test/form',
    name: 'test-form',
    meta: { title: 'Form Renderer Test' },
    component: () => import('../../modules/test/views/TestFormView.vue'),
  },
  {
    path: '/test/editor',
    name: 'test-editor',
    meta: { title: 'Code Editor Interaction Test' },
    component: () => import('../../modules/test/views/TestEditorView.vue'),
  },
  {
    path: '/test/dialog',
    name: 'test-dialog',
    meta: { title: 'Dialog Interaction Test' },
    component: () => import('../../modules/test/views/TestDialogView.vue'),
  },
  {
    path: '/test/echarts',
    name: 'test-echarts',
    meta: { title: 'ECharts Renderer Test' },
    component: () => import('../../modules/test/views/TestEchartsView.vue'),
  },
  {
    path: '/test/chat',
    name: 'test-chat',
    meta: { title: 'Chat Interaction Test', fullscreen: true },
    component: () => import('../../modules/test/views/TestChatView.vue'),
  },
  {
    path: '/test/render-runtime',
    name: 'test-render-runtime',
    meta: { title: 'Render Runtime Test' },
    component: () => import('../../modules/test/views/TestRenderRuntimeView.vue'),
  },
]
