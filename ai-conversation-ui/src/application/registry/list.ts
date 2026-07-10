import { markRaw } from 'vue'
import ListMainLayout from '../renderers/list/ListMainLayout.vue'
import { resolveListRendererData } from '../resolver'
import type { ListRendererRuntimeProps } from '../schema/list'
import type { ApplicationRendererDefinition } from './types'

export const LIST_RENDERER_KEY = 'zg-list-main-layout'

export const LIST_RENDERER_DEFINITION: ApplicationRendererDefinition<ListRendererRuntimeProps> = {
  key: LIST_RENDERER_KEY,
  aliases: ['list-main-layout', 'zg-common-list', 'zg-common-tree-list', 'common-list', 'common-tree-list'],
  name: '通用列表渲染器',
  category: '数据展示',
  version: '1.0.0',
  sourcePath: 'src/application/renderers/list/ListMainLayout.vue',
  component: markRaw(ListMainLayout),
  defaultProps: {
    data: {
      records: [],
      total: 0,
      treeData: [],
    },
    state: {
      loading: false,
    },
  },
  resolveData: resolveListRendererData,
}
