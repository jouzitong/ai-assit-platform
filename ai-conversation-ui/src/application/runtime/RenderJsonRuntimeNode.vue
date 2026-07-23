<script setup lang="ts">
import { computed, reactive, ref, watch, type CSSProperties } from 'vue'
import { findApplicationLayout } from '../layout'
import { findApplicationRenderer } from '../registry'
import { createDefaultQueryState } from '../renderers/list/schema'
import type { RendererAction, RendererQueryState } from '../renderers/list/types'
import type {
  RenderRuntimeNodeEvent,
  RenderRuntimeNodeKind,
  RenderRuntimeNodeScope,
} from './observability'
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

const props = withDefaults(defineProps<{
  node: RuntimeNode
  observe?: boolean
  path?: string
  developerActions?: RendererAction[]
}>(), {
  observe: false,
  path: 'root',
  developerActions: () => [],
})

const emit = defineEmits<{
  'scope-change': [scope: RenderRuntimeNodeScope]
  'developer-action': [action: RendererAction]
}>()

const definition = computed(() => findApplicationRenderer(props.node.component))
const componentKey = computed(() => props.node.component || '')
const normalizedComponentKey = computed(() => componentKey.value.toLowerCase())
const layoutDefinition = computed(() => findApplicationLayout(normalizedComponentKey.value))
const isText = computed(() => normalizedComponentKey.value === 'text')
const isHeading = computed(() => ['heading', 'title'].includes(normalizedComponentKey.value))
const isLayout = computed(() => Boolean(layoutDefinition.value))
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
const requestPlans = ref<unknown[]>([])
const nodeEvents = ref<RenderRuntimeNodeEvent[]>([])
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
    ...(definition.value?.key === 'zg-list-main-layout' && props.observe
      ? {
          developerMode: true,
          developerActions: props.developerActions,
        }
      : {}),
  }
})

const nodeStyle = computed<CSSProperties>(() => {
  const layout = props.node.layout || {}
  const style: CSSProperties = {}
  const allowed = [
    'gridColumn',
    'gridRow',
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
  return style
})

const isLayoutBounded = computed(() => {
  const layout = props.node.layout || {}
  return ['gridColumn', 'gridRow', 'width', 'height', 'minHeight']
    .some((key) => layout[key] !== undefined)
})

const textValue = computed(() => String(
  rawNodeProps.value.value
    ?? rawNodeProps.value.text
    ?? rawNodeProps.value.title
    ?? '',
))
const nodeScopeId = computed(() => props.node.id?.trim() || props.path)
const nodeKind = computed<RenderRuntimeNodeKind>(() => {
  if (isLayout.value) return 'layout'
  if (definition.value) return 'renderer'
  if (isText.value || isHeading.value) return 'static'
  return 'unknown'
})
const scopeSnapshot = computed<RenderRuntimeNodeScope>(() => ({
  id: nodeScopeId.value,
  component: componentKey.value,
  kind: nodeKind.value,
  path: props.path,
  schema: schema.value,
  query: { ...queryState },
  requestPlans: [...requestPlans.value],
  data: resolvedData.value,
  state: rendererState.value,
  events: [...nodeEvents.value],
}))

watch(
  [() => props.observe, scopeSnapshot],
  ([observe, snapshot]) => {
    if (observe) emit('scope-change', snapshot)
  },
  { deep: true, immediate: true },
)

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
    requestPlans.value = []
    resolvedData.value = resolveInlineData(rawNodeProps.value)
    return
  }
  const sequence = ++requestSequence
  requestPlans.value = []
  rendererLoading.value = true
  rendererError.value = ''
  try {
    const payload = await resolveRendererRuntimeData(componentKey.value, {
      schema: schema.value,
      query,
    })
    if (sequence !== requestSequence) return
    const resolved = payload.resolved as {
      data?: Record<string, unknown>
      requestPlans?: unknown[]
    } | null
    requestPlans.value = resolved?.requestPlans || []
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
  recordNodeEvent('queryChange', query)
}

function handleReload(query: RendererQueryState) {
  Object.assign(queryState, query)
  recordNodeEvent('reload', query)
  void loadData(queryState)
}

function handleAction(action: RendererAction) {
  recordNodeEvent('action', action)
  if (props.developerActions.some(candidate => candidate.key === action.key)) {
    emit('developer-action', action)
  }
}

function recordNodeEvent(type: string, payload?: unknown) {
  if (!props.observe) {
    return
  }
  nodeEvents.value = [
    ...nodeEvents.value,
    {
      type,
      timestamp: new Date().toISOString(),
      ...(payload === undefined ? {} : { payload }),
    },
  ].slice(-20)
}

function forwardScope(scope: RenderRuntimeNodeScope) {
  emit('scope-change', scope)
}

function forwardDeveloperAction(action: RendererAction) {
  emit('developer-action', action)
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
  <div
    class="render-json-runtime-node"
    :class="{ 'render-json-runtime-node--bounded': isLayoutBounded }"
    :style="nodeStyle"
    :data-component="componentKey"
  >
    <p v-if="isText" class="render-json-runtime-node__text">{{ textValue }}</p>
    <h2 v-else-if="isHeading" class="render-json-runtime-node__heading">{{ textValue }}</h2>
    <component
      :is="layoutDefinition.component"
      v-else-if="isLayout && layoutDefinition"
      :kind="layoutDefinition.kind"
      :layout="node.layout"
      :developer-mode="observe"
    >
      <RenderJsonRuntimeNode
        v-for="(child, index) in node.children || []"
        :key="child.id || `${child.component}-${index}`"
        :node="child"
        :observe="observe"
        :path="`${path}.${index}`"
        :developer-actions="developerActions"
        @scope-change="forwardScope"
        @developer-action="forwardDeveloperAction"
      />
    </component>
    <template v-else-if="definition">
      <div v-if="rendererError" class="render-json-runtime-node__error" role="alert">
        {{ rendererError }}
      </div>
      <component
        :is="definition.component"
        v-bind="runtimeProps"
        @query-change="handleQueryChange"
        @reload="handleReload"
        @action="handleAction"
      />
      <RenderJsonRuntimeNode
        v-for="(child, index) in node.children || []"
        :key="child.id || `${child.component}-${index}`"
        :node="child"
        :observe="observe"
        :path="`${path}.${index}`"
        :developer-actions="developerActions"
        @scope-change="forwardScope"
        @developer-action="forwardDeveloperAction"
      />
    </template>
    <div v-else class="render-json-runtime-node__error" role="alert">
      暂不支持组件：{{ componentKey || 'unknown' }}
    </div>
  </div>
</template>

<style scoped>
.render-json-runtime-node {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
}

.render-json-runtime-node--bounded {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.render-json-runtime-node--bounded > :deep(*) {
  max-width: 100%;
  max-height: 100%;
  min-width: 0;
  min-height: 0;
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
