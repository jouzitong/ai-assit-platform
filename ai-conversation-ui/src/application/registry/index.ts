import { LIST_RENDERER_DEFINITION } from './list'
import { FORM_RENDERER_DEFINITION } from './form'
import { CHART_RENDERER_DEFINITIONS } from './charts'
import { APPLICATION_COMPONENT_MANIFEST } from '../component-manifest'
import type { ApplicationRendererDefinition } from './types'

const APPLICATION_RENDERER_DEFINITIONS = [
  LIST_RENDERER_DEFINITION,
  FORM_RENDERER_DEFINITION,
  ...CHART_RENDERER_DEFINITIONS,
] as const satisfies readonly ApplicationRendererDefinition<any>[]

const APPLICATION_RENDERER_MAP = new Map<string, ApplicationRendererDefinition<any>>()

function assertComponentManifestCoverage() {
  const rendererKeys = new Set(APPLICATION_RENDERER_DEFINITIONS.map(item => item.key))
  const manifestKeys = new Set(APPLICATION_COMPONENT_MANIFEST.map(item => item.key))
  const renderersWithoutAssets = [...rendererKeys].filter(key => !manifestKeys.has(key))
  const assetsWithoutRenderers = [...manifestKeys].filter(key => !rendererKeys.has(key))
  const incompleteAssets = APPLICATION_COMPONENT_MANIFEST.flatMap((item) => {
    const example = item.examples[0]
    const exampleProps = example?.renderDocument.root.props || {}
    const missingRequiredProps = item.parameters
      .filter(parameter => parameter.required && !Object.prototype.hasOwnProperty.call(exampleProps, parameter.key))
      .map(parameter => parameter.key)
    const problems = [
      !item.documentation.summary.trim() ? '缺少能力说明' : '',
      !item.documentation.usageGuide.trim() ? '缺少使用指引' : '',
      !example ? '缺少默认案例' : '',
      example && example.renderDocument.root.component !== item.key ? '案例 Renderer Key 不一致' : '',
      missingRequiredProps.length ? `案例缺少必填参数 ${missingRequiredProps.join(', ')}` : '',
    ].filter(Boolean)
    return problems.length ? [`${item.key}（${problems.join('、')}）`] : []
  })

  if (renderersWithoutAssets.length || assetsWithoutRenderers.length || incompleteAssets.length) {
    const details = [
      renderersWithoutAssets.length
        ? `缺少知识资产定义：${renderersWithoutAssets.join(', ')}`
        : '',
      assetsWithoutRenderers.length
        ? `未注册 Renderer：${assetsWithoutRenderers.join(', ')}`
        : '',
      incompleteAssets.length
        ? `知识资产定义不完整：${incompleteAssets.join('；')}`
        : '',
    ].filter(Boolean).join('；')
    throw new Error(`Application Renderer 注册表与组件知识资产定义不一致：${details}`)
  }
}

assertComponentManifestCoverage()

for (const definition of APPLICATION_RENDERER_DEFINITIONS) {
  APPLICATION_RENDERER_MAP.set(definition.key, definition)
  for (const alias of definition.aliases || []) {
    APPLICATION_RENDERER_MAP.set(alias, definition)
  }
}

export function listApplicationRenderers() {
  return [...APPLICATION_RENDERER_DEFINITIONS]
}

export function findApplicationRenderer(rendererKey?: string) {
  if (!rendererKey) {
    return undefined
  }
  return APPLICATION_RENDERER_MAP.get(rendererKey)
}

export function hasApplicationRenderer(rendererKey?: string) {
  return Boolean(findApplicationRenderer(rendererKey))
}

export function resolveApplicationRenderer(rendererKey?: string) {
  return findApplicationRenderer(rendererKey)?.component
}

export type { ApplicationRendererDefinition } from './types'
export { LIST_RENDERER_DEFINITION, LIST_RENDERER_KEY } from './list'
export { FORM_RENDERER_DEFINITION, FORM_RENDERER_KEY } from './form'
export { CHART_RENDERER_DEFINITIONS } from './charts'
