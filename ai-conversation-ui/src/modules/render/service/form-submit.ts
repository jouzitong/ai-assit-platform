import {
  upsertRenderMetaContent,
  type RenderRuntimeActionPayload,
} from '../../../application/runtime'

export interface ExecuteRenderFormSubmitOptions {
  code: string
  content: Record<string, unknown>
  payload: RenderRuntimeActionPayload
}

const FORM_COMPONENTS = new Set([
  'form-main-layout',
  'zg-common-form',
  'zg-common-info',
  'common-form',
  'common-info',
])

export async function executeRenderFormSubmit(options: ExecuteRenderFormSubmitOptions) {
  const executor = resolveSubmitExecutor(options.payload.schema)
  if (!executor) {
    throw new Error('当前表单未配置 form_config.submit.executor，无法保存数据')
  }
  if (options.payload.formMode === 'view') {
    throw new Error('查看模式不允许提交表单')
  }
  if (executor !== 'render-meta-data') {
    throw new Error(`暂不支持表单提交器: ${executor}`)
  }

  const patched = patchFormData(options.content, options.payload)
  if (!patched.updated) {
    throw new Error(`未找到待保存的表单节点: ${options.payload.nodeId}`)
  }
  const saved = await upsertRenderMetaContent(options.code, patched.content)
  return isRecord(saved) ? saved : patched.content
}

function resolveSubmitExecutor(schema: Record<string, unknown> | null) {
  if (!schema || !isRecord(schema.form_config)) {
    return ''
  }
  const submit = schema.form_config.submit
  return isRecord(submit) && typeof submit.executor === 'string'
    ? submit.executor.trim()
    : ''
}

function patchFormData(
  content: Record<string, unknown>,
  payload: RenderRuntimeActionPayload,
): { content: Record<string, unknown>; updated: boolean } {
  if (isTargetFormSchema(content, payload)) {
    return {
      content: { ...content, data: cloneRecord(payload.values) },
      updated: true,
    }
  }

  let updated = false
  const nextContent: Record<string, unknown> = { ...content }

  if (isRecord(content.schema) && isTargetFormSchema(content.schema, payload)) {
    nextContent.schema = {
      ...content.schema,
      data: cloneRecord(payload.values),
    }
    updated = true
  }

  if (isRecord(content.root)) {
    const patchedRoot = patchFormNode(content.root, payload)
    if (patchedRoot.updated) {
      nextContent.root = patchedRoot.node
      updated = true
    }
  }

  return { content: nextContent, updated }
}

function patchFormNode(
  node: Record<string, unknown>,
  payload: RenderRuntimeActionPayload,
): { node: Record<string, unknown>; updated: boolean } {
  const nodeId = readString(node.id) || readString(node.key)
  const props = isRecord(node.props) ? node.props : {}
  const propsSchema = isRecord(props.schema) ? props.schema : null
  const matchesNode = nodeId === payload.nodeId
  let updated = false
  let nextProps = props

  if (propsSchema && (matchesNode || isTargetFormSchema(propsSchema, payload))) {
    nextProps = {
      ...props,
      schema: {
        ...propsSchema,
        data: cloneRecord(payload.values),
      },
    }
    updated = true
  } else if (matchesNode && isFormComponent(node.component)) {
    nextProps = {
      ...props,
      data: cloneRecord(payload.values),
    }
    updated = true
  }

  const children = Array.isArray(node.children) ? node.children : []
  const nextChildren = children.map((child) => {
    if (!isRecord(child)) {
      return child
    }
    const patchedChild = patchFormNode(child, payload)
    updated = updated || patchedChild.updated
    return patchedChild.node
  })

  return {
    node: {
      ...node,
      ...(nextProps !== props ? { props: nextProps } : {}),
      ...(children.length ? { children: nextChildren } : {}),
    },
    updated,
  }
}

function isTargetFormSchema(
  schema: Record<string, unknown>,
  payload: RenderRuntimeActionPayload,
) {
  const schemaId = readString(schema.id)
  return isFormComponent(schema.component)
    && (schemaId === payload.nodeId || schema === payload.schema)
}

function isFormComponent(value: unknown) {
  return typeof value === 'string' && FORM_COMPONENTS.has(value.toLowerCase())
}

const UNSAFE_OBJECT_KEYS = new Set(['__proto__', 'prototype', 'constructor'])

function cloneRecord(value: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(value)
      .filter(([key]) => !UNSAFE_OBJECT_KEYS.has(key))
      .map(([key, child]) => [key, cloneValue(child)]),
  )
}

function cloneValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(cloneValue)
  }
  return isRecord(value) ? cloneRecord(value) : value
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}
