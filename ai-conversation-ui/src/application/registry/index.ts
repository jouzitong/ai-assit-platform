import { markRaw, type Component } from 'vue'
import { resolveChartRendererData, resolveListRendererData } from '../resolver'
import { APPLICATION_COMPONENT_MANIFEST } from '../component-manifest'
import { assertApplicationRendererAssetDefinitions } from '../component-manifest-validation'
import { APPLICATION_LAYOUT_CATALOG } from '../layout/catalog'
import { APPLICATION_STATIC_RENDER_NODE_CATALOG } from '../runtime/node-catalog'
import {
  APPLICATION_RENDERER_CATALOG,
  BAR_CHART_RENDERER_CATALOG_ENTRY,
  COMBO_CHART_RENDERER_CATALOG_ENTRY,
  FORM_RENDERER_CATALOG_ENTRY,
  FUNNEL_CHART_RENDERER_CATALOG_ENTRY,
  GAUGE_CHART_RENDERER_CATALOG_ENTRY,
  HEATMAP_CHART_RENDERER_CATALOG_ENTRY,
  LINE_CHART_RENDERER_CATALOG_ENTRY,
  LIST_RENDERER_CATALOG_ENTRY,
  PIE_CHART_RENDERER_CATALOG_ENTRY,
  PUBLIC_APPLICATION_RENDERER_CATALOG,
  RADAR_CHART_RENDERER_CATALOG_ENTRY,
  SCATTER_CHART_RENDERER_CATALOG_ENTRY,
} from './catalog'
import type { ApplicationRendererDefinition } from './types'
import { createChartPropsNormalizer } from '../renderers/echarts/normalize'

const RENDERER_COMPONENT_MODULES = import.meta.glob<Component>(
  ['../renderers/**/*.vue', '!../renderers/**/components/**/*.vue'],
  { eager: true, import: 'default' },
)

const RENDERER_RUNTIME_OPTIONS: Record<
  string,
  Partial<Pick<
    ApplicationRendererDefinition<any, any>,
    'defaultProps' | 'normalizeProps' | 'resolveData'
  >>
> = {
  [LIST_RENDERER_CATALOG_ENTRY.key]: {
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
  },
  [FORM_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: {
      modelValue: {},
      readonly: false,
    },
  },
  [LINE_CHART_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: { categories: [], series: [] },
    normalizeProps: createChartPropsNormalizer(LINE_CHART_RENDERER_CATALOG_ENTRY.key),
    resolveData: resolveChartRendererData,
  },
  [COMBO_CHART_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: { categories: [], barSeries: [], lineSeries: [] },
    normalizeProps: createChartPropsNormalizer(COMBO_CHART_RENDERER_CATALOG_ENTRY.key),
    resolveData: resolveChartRendererData,
  },
  [RADAR_CHART_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: { indicators: [], series: [] },
    normalizeProps: createChartPropsNormalizer(RADAR_CHART_RENDERER_CATALOG_ENTRY.key),
    resolveData: resolveChartRendererData,
  },
  [PIE_CHART_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: { data: [] },
    normalizeProps: createChartPropsNormalizer(PIE_CHART_RENDERER_CATALOG_ENTRY.key),
    resolveData: resolveChartRendererData,
  },
  [BAR_CHART_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: { categories: [], series: [] },
    normalizeProps: createChartPropsNormalizer(BAR_CHART_RENDERER_CATALOG_ENTRY.key),
    resolveData: resolveChartRendererData,
  },
  [GAUGE_CHART_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: { value: 0 },
    normalizeProps: createChartPropsNormalizer(GAUGE_CHART_RENDERER_CATALOG_ENTRY.key),
    resolveData: resolveChartRendererData,
  },
  [FUNNEL_CHART_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: { data: [] },
    normalizeProps: createChartPropsNormalizer(FUNNEL_CHART_RENDERER_CATALOG_ENTRY.key),
    resolveData: resolveChartRendererData,
  },
  [SCATTER_CHART_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: { series: [] },
    normalizeProps: createChartPropsNormalizer(SCATTER_CHART_RENDERER_CATALOG_ENTRY.key),
    resolveData: resolveChartRendererData,
  },
  [HEATMAP_CHART_RENDERER_CATALOG_ENTRY.key]: {
    defaultProps: { xCategories: [], yCategories: [], data: [] },
    normalizeProps: createChartPropsNormalizer(HEATMAP_CHART_RENDERER_CATALOG_ENTRY.key),
    resolveData: resolveChartRendererData,
  },
}

function rendererModulePath(sourcePath: string) {
  return sourcePath.replace(/^src\/application\//, '../')
}

const APPLICATION_RENDERER_DEFINITIONS: ApplicationRendererDefinition<any, any>[] =
  PUBLIC_APPLICATION_RENDERER_CATALOG.map((entry) => {
    const modulePath = rendererModulePath(entry.sourcePath)
    const component = RENDERER_COMPONENT_MODULES[modulePath]
    if (!component) {
      throw new Error(`公开 Renderer 未找到组件实现：${entry.key} -> ${entry.sourcePath}`)
    }
    return {
      ...entry,
      component: markRaw(component),
      ...RENDERER_RUNTIME_OPTIONS[entry.key],
    }
  })

function rendererDefinition(key: string) {
  const definition = APPLICATION_RENDERER_DEFINITIONS.find(item => item.key === key)
  if (!definition) throw new Error(`公开 Renderer 未注册：${key}`)
  return definition
}

export const LIST_RENDERER_KEY = LIST_RENDERER_CATALOG_ENTRY.key
export const FORM_RENDERER_KEY = FORM_RENDERER_CATALOG_ENTRY.key
export const LIST_RENDERER_DEFINITION = rendererDefinition(LIST_RENDERER_KEY)
export const FORM_RENDERER_DEFINITION = rendererDefinition(FORM_RENDERER_KEY)
export const CHART_RENDERER_DEFINITIONS = [
  rendererDefinition(LINE_CHART_RENDERER_CATALOG_ENTRY.key),
  rendererDefinition(COMBO_CHART_RENDERER_CATALOG_ENTRY.key),
  rendererDefinition(RADAR_CHART_RENDERER_CATALOG_ENTRY.key),
  rendererDefinition(PIE_CHART_RENDERER_CATALOG_ENTRY.key),
  rendererDefinition(BAR_CHART_RENDERER_CATALOG_ENTRY.key),
  rendererDefinition(GAUGE_CHART_RENDERER_CATALOG_ENTRY.key),
  rendererDefinition(FUNNEL_CHART_RENDERER_CATALOG_ENTRY.key),
  rendererDefinition(SCATTER_CHART_RENDERER_CATALOG_ENTRY.key),
  rendererDefinition(HEATMAP_CHART_RENDERER_CATALOG_ENTRY.key),
]

const APPLICATION_RENDERER_MAP = new Map<string, ApplicationRendererDefinition<any, any>>()

assertApplicationRendererAssetDefinitions(
  APPLICATION_RENDERER_DEFINITIONS,
  APPLICATION_COMPONENT_MANIFEST,
  [...APPLICATION_LAYOUT_CATALOG, ...APPLICATION_STATIC_RENDER_NODE_CATALOG],
)

for (const definition of APPLICATION_RENDERER_DEFINITIONS) {
  APPLICATION_RENDERER_MAP.set(definition.key.toLowerCase(), definition)
  for (const alias of definition.aliases || []) {
    APPLICATION_RENDERER_MAP.set(alias.toLowerCase(), definition)
  }
}

export function listApplicationRenderers() {
  return [...APPLICATION_RENDERER_DEFINITIONS]
}

export function findApplicationRenderer(rendererKey?: string) {
  if (!rendererKey) {
    return undefined
  }
  return APPLICATION_RENDERER_MAP.get(rendererKey.toLowerCase())
}

export function hasApplicationRenderer(rendererKey?: string) {
  return Boolean(findApplicationRenderer(rendererKey))
}

export function resolveApplicationRenderer(rendererKey?: string) {
  return findApplicationRenderer(rendererKey)?.component
}

export type { ApplicationRendererDefinition } from './types'
export {
  APPLICATION_RENDERER_CATALOG,
  PUBLIC_APPLICATION_RENDERER_CATALOG,
  type ApplicationRendererCatalogEntry,
  type ApplicationRendererExposure,
} from './catalog'
