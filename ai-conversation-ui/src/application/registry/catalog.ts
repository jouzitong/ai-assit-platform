export type ApplicationRendererExposure = 'public' | 'internal'

export interface ApplicationRendererCatalogEntry {
  key: string
  aliases: readonly string[]
  name: string
  category: string
  version: string
  sourcePath: string
  exposure: ApplicationRendererExposure
}

const COMPONENT_VERSION = '1.0.0'

export const LIST_RENDERER_CATALOG_ENTRY = {
  key: 'zg-list-main-layout',
  aliases: ['list-main-layout', 'zg-common-list', 'zg-common-tree-list', 'common-list', 'common-tree-list'],
  name: '通用列表渲染器',
  category: '数据展示',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/list/ListMainLayout.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const FORM_RENDERER_CATALOG_ENTRY = {
  key: 'form-main-layout',
  aliases: ['zg-common-form', 'zg-common-info', 'common-form', 'common-info'],
  name: '通用表单渲染器',
  category: '表单交互',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/form/FormMainLayout.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const LINE_CHART_RENDERER_CATALOG_ENTRY = {
  key: 'line-chart-renderer',
  aliases: ['zg-line-chart-renderer'],
  name: '折线图渲染器',
  category: '数据可视化',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/LineChartRenderer.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const COMBO_CHART_RENDERER_CATALOG_ENTRY = {
  key: 'combo-chart-renderer',
  aliases: ['zg-combo-chart-renderer'],
  name: '柱线组合图渲染器',
  category: '数据可视化',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/ComboChartRenderer.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const RADAR_CHART_RENDERER_CATALOG_ENTRY = {
  key: 'radar-chart-renderer',
  aliases: ['zg-radar-chart-renderer'],
  name: '雷达图渲染器',
  category: '数据可视化',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/RadarChartRenderer.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const PIE_CHART_RENDERER_CATALOG_ENTRY = {
  key: 'pie-chart-renderer',
  aliases: ['zg-pie-chart-renderer'],
  name: '饼图渲染器',
  category: '数据可视化',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/PieChartRenderer.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const BAR_CHART_RENDERER_CATALOG_ENTRY = {
  key: 'bar-chart-renderer',
  aliases: ['zg-bar-chart-renderer'],
  name: '柱状图渲染器',
  category: '数据可视化',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/BarChartRenderer.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const GAUGE_CHART_RENDERER_CATALOG_ENTRY = {
  key: 'gauge-chart-renderer',
  aliases: ['zg-gauge-chart-renderer'],
  name: '仪表盘渲染器',
  category: '数据可视化',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/GaugeChartRenderer.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const FUNNEL_CHART_RENDERER_CATALOG_ENTRY = {
  key: 'funnel-chart-renderer',
  aliases: ['zg-funnel-chart-renderer'],
  name: '漏斗图渲染器',
  category: '数据可视化',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/FunnelChartRenderer.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const SCATTER_CHART_RENDERER_CATALOG_ENTRY = {
  key: 'scatter-chart-renderer',
  aliases: ['zg-scatter-chart-renderer'],
  name: '散点图渲染器',
  category: '数据可视化',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/ScatterChartRenderer.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const HEATMAP_CHART_RENDERER_CATALOG_ENTRY = {
  key: 'heatmap-chart-renderer',
  aliases: ['zg-heatmap-chart-renderer'],
  name: '热力图渲染器',
  category: '数据可视化',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/HeatmapChartRenderer.vue',
  exposure: 'public',
} as const satisfies ApplicationRendererCatalogEntry

export const BASE_ECHART_CATALOG_ENTRY = {
  key: 'base-echart',
  aliases: [],
  name: 'ECharts 基础宿主',
  category: '内部基础组件',
  version: COMPONENT_VERSION,
  sourcePath: 'src/application/renderers/echarts/BaseEchart.vue',
  exposure: 'internal',
} as const satisfies ApplicationRendererCatalogEntry

export const APPLICATION_RENDERER_CATALOG = [
  LIST_RENDERER_CATALOG_ENTRY,
  FORM_RENDERER_CATALOG_ENTRY,
  LINE_CHART_RENDERER_CATALOG_ENTRY,
  COMBO_CHART_RENDERER_CATALOG_ENTRY,
  RADAR_CHART_RENDERER_CATALOG_ENTRY,
  PIE_CHART_RENDERER_CATALOG_ENTRY,
  BAR_CHART_RENDERER_CATALOG_ENTRY,
  GAUGE_CHART_RENDERER_CATALOG_ENTRY,
  FUNNEL_CHART_RENDERER_CATALOG_ENTRY,
  SCATTER_CHART_RENDERER_CATALOG_ENTRY,
  HEATMAP_CHART_RENDERER_CATALOG_ENTRY,
  BASE_ECHART_CATALOG_ENTRY,
] as const satisfies readonly ApplicationRendererCatalogEntry[]

export const PUBLIC_APPLICATION_RENDERER_CATALOG = APPLICATION_RENDERER_CATALOG
  .filter(item => item.exposure === 'public')
