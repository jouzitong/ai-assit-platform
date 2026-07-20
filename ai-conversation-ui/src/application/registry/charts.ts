import { markRaw } from 'vue'
import {
  ComboChartRenderer,
  LineChartRenderer,
  RadarChartRenderer,
} from '../renderers/echarts'
import type { ApplicationRendererDefinition } from './types'

export const CHART_RENDERER_DEFINITIONS = [
  {
    key: 'line-chart-renderer',
    aliases: ['zg-line-chart-renderer'],
    name: '折线图渲染器',
    category: '数据可视化',
    version: '1.0.0',
    sourcePath: 'src/application/renderers/echarts/LineChartRenderer.vue',
    component: markRaw(LineChartRenderer),
  },
  {
    key: 'combo-chart-renderer',
    aliases: ['zg-combo-chart-renderer'],
    name: '柱线组合图渲染器',
    category: '数据可视化',
    version: '1.0.0',
    sourcePath: 'src/application/renderers/echarts/ComboChartRenderer.vue',
    component: markRaw(ComboChartRenderer),
  },
  {
    key: 'radar-chart-renderer',
    aliases: ['zg-radar-chart-renderer'],
    name: '雷达图渲染器',
    category: '数据可视化',
    version: '1.0.0',
    sourcePath: 'src/application/renderers/echarts/RadarChartRenderer.vue',
    component: markRaw(RadarChartRenderer),
  },
] as const satisfies readonly ApplicationRendererDefinition<any>[]
