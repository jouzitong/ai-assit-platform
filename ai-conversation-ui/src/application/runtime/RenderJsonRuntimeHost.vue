<script setup lang="ts">
import { computed } from 'vue'
import type { RendererAction } from '../renderers/list/types'
import RenderJsonRuntimeNode from './RenderJsonRuntimeNode.vue'
import type { RenderRuntimeNodeScope } from './observability'

interface RenderJsonRuntimeDocument {
  protocol?: string
  protocolVersion?: string
  pageId?: string
  revision?: string
  root?: Record<string, unknown> | null
}

const props = withDefaults(
  defineProps<{
    document?: RenderJsonRuntimeDocument | null
    loading?: boolean
    error?: string | null
    observe?: boolean
    developerActions?: RendererAction[]
  }>(),
  {
    document: null,
    loading: false,
    error: null,
    observe: false,
    developerActions: () => [],
  },
)

const emit = defineEmits<{
  'scope-change': [scope: RenderRuntimeNodeScope]
  'developer-action': [action: RendererAction]
}>()

const hasRootNode = computed(() => Boolean(props.document?.root))
const protocolLabel = computed(() => props.document?.protocol || 'render-json')
</script>

<template>
  <section class="render-json-runtime-host">
    <slot
      v-if="$slots.default"
      :document="document"
      :has-root-node="hasRootNode"
      :protocol-label="protocolLabel"
    />

    <template v-else>
      <div v-if="loading" class="render-json-runtime-host__state">
        正在准备 Render JSON 运行时入口...
      </div>

      <div v-else-if="error" class="render-json-runtime-host__state render-json-runtime-host__state--error">
        {{ error }}
      </div>

      <div v-else-if="hasRootNode" class="render-json-runtime-host__state">
        <RenderJsonRuntimeNode
          :node="document!.root!"
          :observe="observe"
          :developer-actions="developerActions"
          @scope-change="emit('scope-change', $event)"
          @developer-action="emit('developer-action', $event)"
        />
      </div>

      <div v-else class="render-json-runtime-host__state">
        当前没有可渲染的 Render JSON 文档。
      </div>
    </template>
  </section>
</template>

<style scoped>
.render-json-runtime-host {
  display: flex;
  flex: 1 1 auto;
  width: 100%;
  max-width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  container: render-json-runtime / inline-size;
}

.render-json-runtime-host__state {
  width: 100%;
  min-height: 100%;
  color: var(--el-text-color-regular);
}

.render-json-runtime-host__state--error {
  padding: var(--app-space-5) var(--app-space-6);
  border: 0.0625rem dashed var(--el-color-danger-light-5);
  border-radius: var(--app-radius-xl);
  border-color: var(--el-color-danger-light-5);
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
</style>
