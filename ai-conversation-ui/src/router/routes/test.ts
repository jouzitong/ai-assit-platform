import type { RouteRecordRaw } from 'vue-router'

export const testRoutes: RouteRecordRaw[] = [
  {
    path: '/test',
    name: 'test-index',
    meta: { title: 'Test Cases' },
    component: () => import('../../modules/test/views/TestIndexView.vue'),
  },
  {
    path: '/test/list',
    name: 'test-list',
    meta: {
      title: 'List Renderer Test',
      testCase: {
        order: 10,
        title: '列表渲染器',
        category: '渲染器',
        description: '验证列表 Schema、筛选、表格操作和分页等通用渲染能力。',
      },
    },
    component: () => import('../../modules/test/views/TestListView.vue'),
  },
  {
    path: '/test/form',
    name: 'test-form',
    meta: {
      title: 'Form Renderer Test',
      testCase: {
        order: 20,
        title: '表单渲染器',
        category: '渲染器',
        description: '验证表单 Schema、字段联动、数据变更和动作提交。',
      },
    },
    component: () => import('../../modules/test/views/TestFormView.vue'),
  },
  {
    path: '/test/editor',
    name: 'test-editor',
    meta: {
      title: 'Code Editor Interaction Test',
      testCase: {
        order: 30,
        title: '代码编辑器',
        category: '基础组件',
        description: '验证 Markdown、Python、JSON 编辑以及 Markdown 多种预览模式。',
      },
    },
    component: () => import('../../modules/test/views/TestEditorView.vue'),
  },
  {
    path: '/test/dialog',
    name: 'test-dialog',
    meta: {
      title: 'Dialog Interaction Test',
      testCase: {
        order: 40,
        title: '弹窗与抽屉',
        category: '基础组件',
        description: '验证统一弹窗、抽屉、确认操作、尺寸和底部动作区域。',
      },
    },
    component: () => import('../../modules/test/views/TestDialogView.vue'),
  },
  {
    path: '/test/echarts',
    name: 'test-echarts',
    meta: {
      title: 'ECharts Renderer Test',
      testCase: {
        order: 50,
        title: 'ECharts 图表',
        category: '可视化',
        description: '验证折线图、组合图和雷达图等 ECharts 通用渲染组件。',
      },
    },
    component: () => import('../../modules/test/views/TestEchartsView.vue'),
  },
  {
    path: '/test/chat',
    name: 'test-chat',
    meta: {
      title: 'Chat Interaction Test',
      fullscreen: true,
      testCase: {
        order: 60,
        title: '聊天交互',
        category: '综合场景',
        description: '验证聊天工作区、消息流、输入区以及相关组合交互。',
      },
    },
    component: () => import('../../modules/test/views/TestChatView.vue'),
  },
  {
    path: '/test/render-runtime',
    name: 'test-render-runtime',
    meta: {
      title: 'Render Runtime Test',
      testCase: {
        order: 70,
        title: 'Render Runtime',
        category: '运行时',
        description: '验证 Render JSON 注册、解析和运行时组件渲染链路。',
      },
    },
    component: () => import('../../modules/test/views/TestRenderRuntimeView.vue'),
  },
]
