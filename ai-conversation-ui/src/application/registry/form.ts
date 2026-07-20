import { markRaw } from 'vue'
import FormMainLayout from '../renderers/form/FormMainLayout.vue'
import type { ApplicationRendererDefinition } from './types'

export const FORM_RENDERER_KEY = 'form-main-layout'

export const FORM_RENDERER_DEFINITION: ApplicationRendererDefinition = {
  key: FORM_RENDERER_KEY,
  aliases: ['zg-common-form', 'zg-common-info', 'common-form', 'common-info'],
  name: '通用表单渲染器',
  category: '表单交互',
  version: '1.0.0',
  sourcePath: 'src/application/renderers/form/FormMainLayout.vue',
  component: markRaw(FormMainLayout),
  defaultProps: {
    modelValue: {},
    readonly: false,
  },
}
