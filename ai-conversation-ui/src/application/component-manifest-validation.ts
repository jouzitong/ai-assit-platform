import type {
  ApplicationComponentDefinition,
  ApplicationComponentParameter,
  ApplicationRenderDocument,
  ApplicationRenderNode,
} from './component-manifest'
import type { ApplicationRendererExposure } from './registry/catalog'

export interface ApplicationRendererAssetMetadata {
  key: string
  aliases?: readonly string[]
  name: string
  category: string
  version: string
  sourcePath: string
  exposure: ApplicationRendererExposure
}

export interface ApplicationRenderNodeMetadata {
  key: string
  aliases?: readonly string[]
  version?: string
  parameters?: readonly ApplicationComponentParameter[]
}

const SUPPORTED_PROTOCOL_VERSIONS = new Set(['1.0', '1.0.0'])
const STABLE_ID_PATTERN = /^[A-Za-z][A-Za-z0-9_.:-]{0,127}$/
const VERSION_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$/
const ALLOWED_NODE_KEYS = new Set([
  'id',
  'component',
  'componentVersion',
  'props',
  'layout',
  'datasource',
  'bindings',
  'events',
  'actions',
  'children',
])
const UNSAFE_OBJECT_KEYS = new Set(['__proto__', 'prototype', 'constructor'])
const PARAMETER_CONTROLS = new Set(['text', 'number', 'boolean', 'json'])
const PARAMETER_ITEM_TYPES = new Set(['boolean', 'number', 'object', 'string'])
type JsonValueKind = 'array' | 'boolean' | 'null' | 'number' | 'object' | 'string'

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function sameStringSet(left: readonly string[] = [], right: readonly string[] = []) {
  if (left.length !== right.length) return false
  const normalizedLeft = [...left].sort()
  const normalizedRight = [...right].sort()
  return normalizedLeft.every((value, index) => value === normalizedRight[index])
}

function validateJsonValue(
  value: unknown,
  path: string,
  errors: string[],
  ancestors = new Set<object>(),
) {
  if (value === null || typeof value === 'string' || typeof value === 'boolean') return
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) errors.push(`${path} 必须是有限数字`)
    return
  }
  if (typeof value !== 'object') {
    errors.push(`${path} 不是可序列化 JSON 值`)
    return
  }
  if (ancestors.has(value)) {
    errors.push(`${path} 存在循环引用`)
    return
  }

  const nextAncestors = new Set(ancestors).add(value)
  if (Array.isArray(value)) {
    value.forEach((item, index) => validateJsonValue(item, `${path}[${index}]`, errors, nextAncestors))
    return
  }

  for (const [key, child] of Object.entries(value)) {
    if (UNSAFE_OBJECT_KEYS.has(key)) errors.push(`${path}.${key} 是不安全字段`)
    validateJsonValue(child, `${path}.${key}`, errors, nextAncestors)
  }
}

function jsonValueKind(value: unknown): JsonValueKind | undefined {
  if (value === null) return 'null'
  if (Array.isArray(value)) return 'array'
  if (typeof value === 'boolean') return 'boolean'
  if (typeof value === 'number') return 'number'
  if (typeof value === 'string') return 'string'
  if (isRecord(value)) return 'object'
  return undefined
}

function primitiveTypeKinds(type: string) {
  const normalized = type.replace(/\s+/g, '')
  if (!normalized || normalized.includes('<') || normalized.endsWith('[]')) return []
  const parts = normalized.split('|')
  if (!parts.every(part => ['boolean', 'null', 'number', 'string'].includes(part))) return []
  return parts as JsonValueKind[]
}

function expectedParameterKinds(parameter: ApplicationComponentParameter): JsonValueKind[] {
  const normalized = parameter.type.replace(/\s+/g, '')
  if (/^Array<.+>$/.test(normalized) || normalized.endsWith('[]')) return ['array']

  const primitiveKinds = primitiveTypeKinds(normalized)
  if (primitiveKinds.length) return primitiveKinds

  if (normalized.startsWith('Record<') || normalized.startsWith('Partial<')) return ['object']

  const defaultKind = jsonValueKind(parameter.defaultValue)
  if (defaultKind) return [defaultKind]
  if (parameter.control === 'text') return ['string']
  if (parameter.control === 'number') return ['number']
  if (parameter.control === 'boolean') return ['boolean']
  return []
}

function expectedArrayElementKinds(
  parameter: ApplicationComponentParameter,
): JsonValueKind[] {
  const normalized = parameter.type.replace(/\s+/g, '')
  const match = normalized.match(/^Array<(.+)>$/) || normalized.match(/^(.+)\[\]$/)
  if (!match) return []

  const itemType = match[1]
  const primitiveKinds = primitiveTypeKinds(itemType)
  if (primitiveKinds.length) return primitiveKinds
  if (itemType.startsWith('Record<') || itemType.startsWith('Partial<')) return ['object']
  if (parameter.itemType) return [parameter.itemType]

  if (Array.isArray(parameter.defaultValue)) {
    return [...new Set(parameter.defaultValue
      .map(jsonValueKind)
      .filter((kind): kind is JsonValueKind => Boolean(kind)))]
  }
  return []
}

export function getApplicationComponentParameterValueError(
  value: unknown,
  parameter: ApplicationComponentParameter,
) {
  const actualKind = jsonValueKind(value)
  const expectedKinds = expectedParameterKinds(parameter)
  if (!actualKind || (expectedKinds.length && !expectedKinds.includes(actualKind))) {
    return `值与声明类型 ${parameter.type} 不匹配`
  }
  if (actualKind === 'number' && !Number.isFinite(value)) {
    return '值必须是有限数字'
  }

  const elementKinds = actualKind === 'array'
    ? expectedArrayElementKinds(parameter)
    : []
  if (elementKinds.length) {
    const items = value as unknown[]
    const invalidIndex = items.findIndex((item) => {
      const itemKind = jsonValueKind(item)
      return !itemKind || !elementKinds.includes(itemKind)
    })
    if (invalidIndex >= 0) return `第 ${invalidIndex + 1} 项与数组元素类型 ${parameter.type} 不匹配`
  }
  return ''
}

function validateParameterValue(
  value: unknown,
  parameter: ApplicationComponentParameter,
  path: string,
  errors: string[],
) {
  const error = getApplicationComponentParameterValueError(value, parameter)
  if (error) errors.push(`${path} ${error}`)
}

function validateNodeParameters(
  node: ApplicationRenderNode,
  parameters: readonly ApplicationComponentParameter[],
  path: string,
  errors: string[],
) {
  const props = isRecord(node.props) ? node.props : {}
  const parameterMap = new Map(parameters.map(parameter => [parameter.key, parameter]))
  const unknownProps = Object.keys(props).filter(key => !parameterMap.has(key))
  if (unknownProps.length) errors.push(`${path}.props 包含未声明参数 ${unknownProps.join(', ')}`)

  const missingRequiredProps = parameters
    .filter(parameter => parameter.required && !Object.prototype.hasOwnProperty.call(props, parameter.key))
    .map(parameter => parameter.key)
  if (missingRequiredProps.length) errors.push(`${path}.props 缺少必填参数 ${missingRequiredProps.join(', ')}`)

  Object.entries(props).forEach(([key, value]) => {
    const parameter = parameterMap.get(key)
    if (parameter) validateParameterValue(value, parameter, `${path}.props.${key}`, errors)
  })
}

function validateNode(
  node: ApplicationRenderNode,
  path: string,
  errors: string[],
  nodeIds: Set<string>,
  componentLookup: ReadonlyMap<string, ApplicationRenderNodeMetadata>,
) {
  if (!isRecord(node)) {
    errors.push(`${path} 必须是对象`)
    return
  }

  const unsupportedKey = Object.keys(node).find(key => !ALLOWED_NODE_KEYS.has(key))
  if (unsupportedKey) errors.push(`${path} 包含不支持字段 ${unsupportedKey}`)
  if (!STABLE_ID_PATTERN.test(node.id || '')) {
    errors.push(`${path}.id 不是合法稳定标识`)
  }
  else if (nodeIds.has(node.id)) {
    errors.push(`${path}.id 与其他节点重复`)
  }
  else {
    nodeIds.add(node.id)
  }
  if (!STABLE_ID_PATTERN.test(node.component || '')) {
    errors.push(`${path}.component 不是合法 Renderer Key`)
  }
  if (node.componentVersion !== undefined && !VERSION_PATTERN.test(node.componentVersion)) {
    errors.push(`${path}.componentVersion 格式不正确`)
  }
  const componentDefinition = componentLookup.get(String(node.component || '').toLowerCase())
  if (!componentDefinition) {
    errors.push(`${path}.component 未对应可运行节点：${node.component || '(empty)'}`)
  }
  else {
    if (
      componentDefinition.version !== undefined
      && node.componentVersion !== undefined
      && node.componentVersion !== componentDefinition.version
    ) {
      errors.push(`${path}.componentVersion 与 ${componentDefinition.key} 内部版本不一致`)
    }
    if (componentDefinition.parameters) {
      validateNodeParameters(node, componentDefinition.parameters, path, errors)
    }
  }
  if (node.props !== undefined && !isRecord(node.props)) errors.push(`${path}.props 必须是对象`)
  if (node.layout !== undefined && !isRecord(node.layout)) errors.push(`${path}.layout 必须是对象`)
  if (node.datasource !== undefined && !isRecord(node.datasource)) errors.push(`${path}.datasource 必须是对象`)
  if (node.bindings !== undefined && !isRecord(node.bindings)) errors.push(`${path}.bindings 必须是对象`)
  if (node.events !== undefined && !Array.isArray(node.events)) {
    errors.push(`${path}.events 必须是数组`)
  }
  else if (node.events?.some(item => !isRecord(item))) {
    errors.push(`${path}.events 的每一项必须是对象`)
  }
  if (node.actions !== undefined && !Array.isArray(node.actions)) {
    errors.push(`${path}.actions 必须是数组`)
  }
  else if (node.actions?.some(item => !isRecord(item))) {
    errors.push(`${path}.actions 的每一项必须是对象`)
  }
  if (node.children !== undefined && !Array.isArray(node.children)) {
    errors.push(`${path}.children 必须是数组`)
  }
  else {
    node.children?.forEach((child, index) => validateNode(
      child,
      `${path}.children[${index}]`,
      errors,
      nodeIds,
      componentLookup,
    ))
  }
  validateJsonValue(node, path, errors)
}

function validateRenderDocument(
  document: ApplicationRenderDocument,
  definition: ApplicationComponentDefinition,
  path: string,
  errors: string[],
  componentLookup: ReadonlyMap<string, ApplicationRenderNodeMetadata>,
) {
  if (!isRecord(document)) {
    errors.push(`${path} 必须是对象`)
    return
  }
  const allowedDocumentKeys = new Set(['protocol', 'protocolVersion', 'pageId', 'revision', 'root'])
  const unsupportedKey = Object.keys(document).find(key => !allowedDocumentKeys.has(key))
  if (unsupportedKey) errors.push(`${path} 包含不支持字段 ${unsupportedKey}`)
  if (document.protocol !== 'render-json') errors.push(`${path}.protocol 必须为 render-json`)
  if (!SUPPORTED_PROTOCOL_VERSIONS.has(document.protocolVersion)) {
    errors.push(`${path}.protocolVersion 暂不支持 ${document.protocolVersion || '(empty)'}`)
  }
  if (!STABLE_ID_PATTERN.test(document.pageId || '')) errors.push(`${path}.pageId 不是合法稳定标识`)
  if (document.revision !== undefined && !document.revision.trim()) errors.push(`${path}.revision 不能为空`)
  if (!isRecord(document.root)) {
    errors.push(`${path}.root 必须是对象`)
    return
  }

  validateNode(document.root, `${path}.root`, errors, new Set<string>(), componentLookup)
  if (document.root.component !== definition.key) {
    errors.push(`${path}.root.component 必须为 ${definition.key}`)
  }
  if (document.root.componentVersion !== definition.version) {
    errors.push(`${path}.root.componentVersion 必须为内部版本 ${definition.version}`)
  }
}

function validateManifestDefinition(
  definition: ApplicationComponentDefinition,
  errors: string[],
  componentLookup: ReadonlyMap<string, ApplicationRenderNodeMetadata>,
) {
  const path = `manifest[${definition.key || '?'}]`
  if (definition.exposure !== 'public') errors.push(`${path}.exposure 必须显式为 public`)
  if (!STABLE_ID_PATTERN.test(definition.key)) errors.push(`${path}.key 不是合法稳定标识`)
  if (!definition.name.trim()) errors.push(`${path}.name 不能为空`)
  if (!definition.category.trim()) errors.push(`${path}.category 不能为空`)
  if (!VERSION_PATTERN.test(definition.version)) errors.push(`${path}.version 格式不正确`)
  if (!definition.sourcePath.startsWith('src/application/renderers/') || !definition.sourcePath.endsWith('.vue')) {
    errors.push(`${path}.sourcePath 必须指向 renderers 下的 Vue 入口`)
  }
  if (!definition.description.trim()) errors.push(`${path}.description 不能为空`)
  if (!definition.useCases.length || definition.useCases.some(item => !item.trim())) errors.push(`${path}.useCases 必须完整`)
  if (!definition.tags.length || definition.tags.some(item => !item.trim())) errors.push(`${path}.tags 必须完整`)
  if (!definition.documentation.summary.trim()) errors.push(`${path}.documentation.summary 不能为空`)
  if (!definition.documentation.usageGuide.trim()) errors.push(`${path}.documentation.usageGuide 不能为空`)
  if (!definition.documentation.limitations.trim()) errors.push(`${path}.documentation.limitations 不能为空`)
  if (!definition.documentation.notes.trim()) errors.push(`${path}.documentation.notes 不能为空`)

  if (!definition.parameters.length) errors.push(`${path}.parameters 至少声明一个可配置参数`)
  const parameterKeys = new Set<string>()
  definition.parameters.forEach((parameter, index) => {
    const parameterPath = `${path}.parameters[${index}]`
    if (!STABLE_ID_PATTERN.test(parameter.key)) errors.push(`${parameterPath}.key 不是合法参数标识`)
    if (parameterKeys.has(parameter.key)) errors.push(`${parameterPath}.key 重复`)
    parameterKeys.add(parameter.key)
    if (!parameter.label.trim()) errors.push(`${parameterPath}.label 不能为空`)
    if (!parameter.type.trim()) errors.push(`${parameterPath}.type 不能为空`)
    if (!PARAMETER_CONTROLS.has(parameter.control)) errors.push(`${parameterPath}.control 不受支持`)
    if (parameter.itemType !== undefined && !PARAMETER_ITEM_TYPES.has(parameter.itemType)) {
      errors.push(`${parameterPath}.itemType 不受支持`)
    }
    if (parameter.required !== undefined && typeof parameter.required !== 'boolean') {
      errors.push(`${parameterPath}.required 必须是布尔值`)
    }
    if (!parameter.description.trim()) errors.push(`${parameterPath}.description 不能为空`)
    if (!Object.prototype.hasOwnProperty.call(parameter, 'defaultValue')) {
      errors.push(`${parameterPath}.defaultValue 必须显式声明`)
    }
    validateJsonValue(parameter.defaultValue, `${parameterPath}.defaultValue`, errors)
    validateParameterValue(parameter.defaultValue, parameter, `${parameterPath}.defaultValue`, errors)
    const normalizedType = parameter.type.replace(/\s+/g, '')
    const isArrayType = /^Array<.+>$/.test(normalizedType) || normalizedType.endsWith('[]')
    if (
      isArrayType
      && expectedArrayElementKinds(parameter).length === 0
    ) {
      errors.push(`${parameterPath}.itemType 必须声明，或提供可推断元素类型的非空默认数组`)
    }
  })

  const eventNames = new Set<string>()
  definition.events.forEach((event, index) => {
    const eventPath = `${path}.events[${index}]`
    if (!event.name.trim()) errors.push(`${eventPath}.name 不能为空`)
    if (eventNames.has(event.name)) errors.push(`${eventPath}.name 重复`)
    eventNames.add(event.name)
    if (!event.description.trim()) errors.push(`${eventPath}.description 不能为空`)
  })

  if (!definition.examples.length) errors.push(`${path}.examples 至少提供一个可运行案例`)
  const exampleKeys = new Set<string>()
  definition.examples.forEach((example, index) => {
    const examplePath = `${path}.examples[${index}]`
    if (!STABLE_ID_PATTERN.test(example.key)) errors.push(`${examplePath}.key 不是合法案例标识`)
    if (exampleKeys.has(example.key)) errors.push(`${examplePath}.key 重复`)
    exampleKeys.add(example.key)
    if (!example.name.trim()) errors.push(`${examplePath}.name 不能为空`)
    if (!example.description.trim()) errors.push(`${examplePath}.description 不能为空`)
    validateRenderDocument(
      example.renderDocument,
      definition,
      `${examplePath}.renderDocument`,
      errors,
      componentLookup,
    )
  })
}

export function assertApplicationComponentRenderDocument(
  document: ApplicationRenderDocument,
  definition: ApplicationComponentDefinition,
  manifest: readonly ApplicationComponentDefinition[],
  additionalRenderNodes: readonly ApplicationRenderNodeMetadata[] = [],
) {
  const componentLookup = new Map<string, ApplicationRenderNodeMetadata>()
  ;[...manifest, ...additionalRenderNodes].forEach((item) => {
    for (const identifier of [item.key, ...(item.aliases || [])]) {
      componentLookup.set(identifier.toLowerCase(), item)
    }
  })
  const errors: string[] = []
  validateRenderDocument(document, definition, 'Render JSON', errors, componentLookup)
  if (errors.length) {
    throw new Error(errors[0])
  }
}

export function assertApplicationRendererAssetDefinitions(
  rendererMetadata: readonly ApplicationRendererAssetMetadata[],
  manifest: readonly ApplicationComponentDefinition[],
  additionalRenderNodes: readonly ApplicationRenderNodeMetadata[] = [],
) {
  const errors: string[] = []
  const publicRenderers = rendererMetadata.filter(item => item.exposure === 'public')
  const rendererKeys = new Set<string>()
  const rendererAliases = new Set<string>()
  const rendererSources = new Set<string>()

  rendererMetadata.forEach((renderer) => {
    const normalizedRendererKey = renderer.key.toLowerCase()
    if (renderer.exposure !== 'public' && renderer.exposure !== 'internal') {
      errors.push(`Renderer exposure 不受支持：${renderer.key}`)
    }
    if (!STABLE_ID_PATTERN.test(renderer.key)) errors.push(`Renderer Key 不合法：${renderer.key}`)
    if (!renderer.name.trim()) errors.push(`Renderer name 为空：${renderer.key}`)
    if (!renderer.category.trim()) errors.push(`Renderer category 为空：${renderer.key}`)
    if (!VERSION_PATTERN.test(renderer.version)) errors.push(`Renderer version 不合法：${renderer.key}`)
    if (!renderer.sourcePath.startsWith('src/application/renderers/') || !renderer.sourcePath.endsWith('.vue')) {
      errors.push(`Renderer sourcePath 不合法：${renderer.key}`)
    }
    if (rendererKeys.has(normalizedRendererKey) || rendererAliases.has(normalizedRendererKey)) {
      errors.push(`Renderer Key 冲突：${renderer.key}`)
    }
    rendererKeys.add(normalizedRendererKey)
    if (rendererSources.has(renderer.sourcePath)) errors.push(`Renderer sourcePath 重复：${renderer.sourcePath}`)
    rendererSources.add(renderer.sourcePath)
    renderer.aliases?.forEach((alias) => {
      const normalizedAlias = alias.toLowerCase()
      if (!STABLE_ID_PATTERN.test(alias)) errors.push(`Renderer alias 不合法：${alias}`)
      if (rendererKeys.has(normalizedAlias) || rendererAliases.has(normalizedAlias)) {
        errors.push(`Renderer alias 冲突：${alias}`)
      }
      rendererAliases.add(normalizedAlias)
    })
  })

  const manifestKeys = new Set<string>()
  const componentLookup = new Map<string, ApplicationRenderNodeMetadata>()
  manifest.forEach((definition) => {
    if (manifestKeys.has(definition.key)) errors.push(`Manifest Key 重复：${definition.key}`)
    manifestKeys.add(definition.key)
    for (const identifier of [definition.key, ...definition.aliases]) {
      const normalizedIdentifier = identifier.toLowerCase()
      const existing = componentLookup.get(normalizedIdentifier)
      if (existing && existing.key !== definition.key) {
        errors.push(`Manifest Renderer 标识冲突：${identifier}`)
      }
      componentLookup.set(normalizedIdentifier, definition)
    }
  })
  additionalRenderNodes.forEach((definition) => {
    for (const identifier of [definition.key, ...(definition.aliases || [])]) {
      const normalizedIdentifier = identifier.toLowerCase()
      const existing = componentLookup.get(normalizedIdentifier)
      if (existing && existing.key !== definition.key) {
        errors.push(`Render 节点标识冲突：${identifier}`)
      }
      componentLookup.set(normalizedIdentifier, definition)
    }
  })
  manifest.forEach((definition) => {
    validateManifestDefinition(definition, errors, componentLookup)
  })

  const publicKeys = new Set(publicRenderers.map(item => item.key))
  const missingAssets = [...publicKeys].filter(key => !manifestKeys.has(key))
  const unregisteredAssets = [...manifestKeys].filter(key => !publicKeys.has(key))
  if (missingAssets.length) errors.push(`公开 Renderer 缺少知识资产定义：${missingAssets.join(', ')}`)
  if (unregisteredAssets.length) errors.push(`知识资产未对应公开 Renderer：${unregisteredAssets.join(', ')}`)

  publicRenderers.forEach((renderer) => {
    const definition = manifest.find(item => item.key === renderer.key)
    if (!definition) return
    if (definition.name !== renderer.name) errors.push(`${renderer.key} 的 name 与 Registry 不一致`)
    if (definition.category !== renderer.category) errors.push(`${renderer.key} 的 category 与 Registry 不一致`)
    if (definition.version !== renderer.version) errors.push(`${renderer.key} 的内部 version 与 Registry 不一致`)
    if (definition.sourcePath !== renderer.sourcePath) errors.push(`${renderer.key} 的 sourcePath 与 Registry 不一致`)
    if (definition.exposure !== renderer.exposure) errors.push(`${renderer.key} 的 exposure 与 Registry 不一致`)
    if (!sameStringSet(definition.aliases, renderer.aliases)) errors.push(`${renderer.key} 的 aliases 与 Registry 不一致`)
  })

  if (errors.length) {
    throw new Error(`Application Renderer 知识资产定义校验失败：\n- ${errors.join('\n- ')}`)
  }
}
