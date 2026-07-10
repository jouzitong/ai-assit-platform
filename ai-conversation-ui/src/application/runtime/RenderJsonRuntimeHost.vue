<script setup lang="ts">
import { computed } from 'vue'

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
  }>(),
  {
    document: null,
    loading: false,
    error: null,
  },
)

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
        已接收 `{{ protocolLabel }}` 文档，等待节点解析和渲染接入。
      </div>

      <div v-else class="render-json-runtime-host__state">
        当前没有可渲染的 Render JSON 文档。
      </div>
    </template>
  </section>
</template>

<style scoped>
.render-json-runtime-host {
  width: 100%;
  max-width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  container: render-json-runtime / inline-size;
}

.render-json-runtime-host__state {
  padding: 20px 24px;
  border: 1px dashed var(--el-border-color);
  border-radius: 16px;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-regular);
}

.render-json-runtime-host__state--error {
  border-color: var(--el-color-danger-light-5);
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
</style>
