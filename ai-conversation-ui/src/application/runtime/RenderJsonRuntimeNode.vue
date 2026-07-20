<script setup lang="ts">
import { computed, reactive, ref, watch, type CSSProperties } from 'vue'
import { findApplicationRenderer } from '../registry'
import { createDefaultQueryState } from '../renderers/list/schema'
import type { RendererQueryState } from '../renderers/list/types'
import { resolveRendererRuntimeData } from './resolveRendererRuntimeData'

defineOptions({ name: 'RenderJsonRuntimeNode' })

interface RuntimeNode {
  id?: string
  component?: string
  props?: Record<string, unknown>
  layout?: Record<string, unknown>
  datasource?: Record<string, unknown>
  children?: RuntimeNode[]
}

const props = defineProps<{
  node: RuntimeNode
}>()

const definition = computed(() => findApplicationRenderer(props.node.component))
const componentKey = computed(() => props.node.component || '')
const normalizedComponentKey = computed(() => componentKey.value.toLowerCase())
const isText = computed(() => normalizedComponentKey.value === 'text')
const isHeading = computed(() => ['heading', 'title'].includes(normalizedComponentKey.value))
const isLayout = computed(() => ['page', 'container', 'section', 'grid', 'stack'].includes(normalizedComponentKey.value))
const rawNodeProps = computed(() => props.node.props || {})
const schema = computed<Record<string, unknown> | null>(() => {
  const value = rawNodeProps.value.schema
  if (isRecord(value)) {
    return mergeDatasource(value)
  }
  if (definition.value?.key === 'zg-list-main-layout' || definition.value?.key === 'form-main-layout') {
    return mergeDatasource(rawNodeProps.value)
  }
  return null
})
const queryState = reactive<Partial<RendererQueryState>>({})
const resolvedData = ref<Record<string, unknown>>({ records: [], total: 0, treeData: [] })
const rendererLoading = ref(false)
const rendererError = ref('')
let requestSequence = 0

const rendererState = computed(() => ({
  loading: rendererLoading.value,
  error: rendererError.value || undefined,
  empty: Array.isArray(resolvedData.value.records) && resolvedData.value.records.length === 0,
}))

const runtimeProps = computed(() => {
  const baseProps = {
    ...(definition.value?.defaultProps || {}),
    ...rawNodeProps.value,
  }
  if (!schema.value) {
    return baseProps
  }
  return {
    ...baseProps,
    schema: schema.value,
    data: resolvedData.value,
    state: rendererState.value,
    records: resolvedData.value.records || [],
    treeData: resolvedData.value.treeData || [],
    total: resolvedData.value.total || 0,
  }
})

const nodeStyle = computed<CSSProperties>(() => {
  const layout = props.node.layout || {}
  const style: CSSProperties = {}
  const allowed = [
    'display',
    'gridTemplateColumns',
    'gridTemplateRows',
    'gridColumn',
    'gridRow',
    'gap',
    'padding',
    'alignItems',
    'justifyItems',
    'justifyContent',
    'width',
    'height',
    'minHeight',
  ] as const
  allowed.forEach((key) => {
    const value = layout[key]
    if (typeof value === 'string' || typeof value === 'number') {
      Object.assign(style, { [key]: value })
    }
  })
  if (props.node.children?.length && !style.display) {
    style.display = normalizedComponentKey.value === 'grid' ? 'grid' : 'flex'
    if (style.display === 'flex') {
      style.flexDirection = 'column'
    }
  }
  return style
})

const textValue = computed(() => String(
  rawNodeProps.value.value
    ?? rawNodeProps.value.text
    ?? rawNodeProps.value.title
    ?? '',
))

watch(
  () => JSON.stringify({ component: props.node.component, schema: schema.value }),
  () => {
    Object.keys(queryState).forEach(key => delete queryState[key as keyof RendererQueryState])
    if (schema.value && definition.value?.key === 'zg-list-main-layout') {
      Object.assign(queryState, createDefaultQueryState(schema.value as never))
    }
    void loadData(queryState)
  },
  { immediate: true },
)

async function loadData(query: Partial<RendererQueryState>) {
  if (
    !definition.value?.resolveData
    || !schema.value
    || !isRecord(schema.value.datasource)
  ) {
    rendererError.value = ''
    resolvedData.value = resolveInlineData(rawNodeProps.value)
    return
  }
  const sequence = ++requestSequence
  rendererLoading.value = true
  rendererError.value = ''
  try {
    const payload = await resolveRendererRuntimeData(componentKey.value, {
      schema: schema.value,
      query,
    })
    if (sequence !== requestSequence) return
    const resolved = payload.resolved as { data?: Record<string, unknown> } | null
    resolvedData.value = resolved?.data || resolveInlineData(rawNodeProps.value)
  } catch (error) {
    if (sequence !== requestSequence) return
    rendererError.value = error instanceof Error ? error.message : '页面数据加载失败'
  } finally {
    if (sequence === requestSequence) rendererLoading.value = false
  }
}

function handleQueryChange(query: RendererQueryState) {
  Object.assign(queryState, query)
}

function handleReload(query: RendererQueryState) {
  Object.assign(queryState, query)
  void loadData(queryState)
}

function mergeDatasource(value: Record<string, unknown>) {
  if (!props.node.datasource || value.datasource) return value
  return { ...value, datasource: props.node.datasource }
}

function resolveInlineData(value: Record<string, unknown>) {
  if (isRecord(value.data)) return value.data
  return {
    records: Array.isArray(value.records) ? value.records : [],
    treeData: Array.isArray(value.treeData) ? value.treeData : [],
    total: typeof value.total === 'number'
      ? value.total
      : Array.isArray(value.records) ? value.records.length : 0,
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
</script>

<template>
  <div class="render-json-runtime-node" :style="nodeStyle" :data-component="componentKey">
    <p v-if="isText" class="render-json-runtime-node__text">{{ textValue }}</p>
    <h2 v-else-if="isHeading" class="render-json-runtime-node__heading">{{ textValue }}</h2>
    <template v-else-if="isLayout">
      <RenderJsonRuntimeNode
        v-for="child in node.children || []"
        :key="child.id || child.component"
        :node="child"
      />
    </template>
    <template v-else-if="definition">
      <div v-if="rendererError" class="render-json-runtime-node__error" role="alert">
        {{ rendererError }}
      </div>
      <component
        :is="definition.component"
        v-bind="runtimeProps"
        @query-change="handleQueryChange"
        @reload="handleReload"
      />
      <RenderJsonRuntimeNode
        v-for="child in node.children || []"
        :key="child.id || child.component"
        :node="child"
      />
    </template>
    <div v-else class="render-json-runtime-node__error" role="alert">
      暂不支持组件：{{ componentKey || 'unknown' }}
    </div>
  </div>
</template>

<style scoped>
.render-json-runtime-node {
  min-width: 0;
}

.render-json-runtime-node__text,
.render-json-runtime-node__heading {
  margin: 0;
  color: var(--el-text-color-primary);
}

.render-json-runtime-node__text {
  font-size: 1rem;
  line-height: 1.6;
}

.render-json-runtime-node__heading {
  font-size: 1.5rem;
  line-height: 1.35;
}

.render-json-runtime-node__error {
  padding: 1rem;
  border: 0.0625rem dashed var(--el-color-danger-light-5);
  border-radius: 0.75rem;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
</style>
