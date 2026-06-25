<script setup>
import {ref, watch} from 'vue'
import {SchemaLoader} from '../loader/SchemaLoader'
import {createLocalSchemaProvider} from '../loader/providers/localSchemaProvider'
import PageRenderer from '../renderer/PageRenderer.vue'
import {resolveSchema} from '../resolver/SchemaResolver'
import {createPageRuntime} from '../runtime/createPageRuntime'

const props = defineProps({
  pageCode: {
    type: String,
    required: true
  }
})

const loading = ref(false)
const errorMessage = ref('')
const runtime = ref(null)

const loader = new SchemaLoader([
  createLocalSchemaProvider()
])

async function boot() {
  if (!props.pageCode) {
    errorMessage.value = '请通过 query 传入 pageCode，例如 /settings/system/lowcode?pageCode=your_page_code'
    runtime.value = null
    loading.value = false
    return
  }

  loading.value = true
  errorMessage.value = ''
  runtime.value = null
  try {
    const rawSchema = await loader.load(props.pageCode)
    const resolvedSchema = resolveSchema(rawSchema, props.pageCode)
    const nextRuntime = createPageRuntime(resolvedSchema)
    runtime.value = nextRuntime
    await nextRuntime.init()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '低代码页面初始化失败'
  } finally {
    loading.value = false
  }
}

watch(() => props.pageCode, () => {
  boot()
}, {immediate: true})
</script>

<template>
  <div class="lowcode-page">
    <div class="page-head">
      <div>
        <p class="eyebrow">Lowcode Runtime</p>
        <h2>页面结构渲染架构</h2>
        <p class="page-desc">当前页通过 `pageCode` 从 RenderPageContent 读取 JSON，并走通 `SchemaLoader -> SchemaResolver
          -> PageRuntime -> Renderer -> Registry -> Vue3` 全链路。</p>
      </div>
    </div>

    <div v-if="loading" class="page-placeholder">
      正在装载页面 schema...
    </div>
    <div v-else-if="errorMessage" class="page-placeholder is-error">
      {{ errorMessage }}
    </div>
    <PageRenderer v-else-if="runtime" :runtime="runtime"/>
  </div>
</template>

<style scoped>
.lowcode-page {
  display: grid;
  gap: 18px;
  min-height: 0;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-head h2,
.page-head p {
  margin: 0;
}

.page-desc {
  margin-top: 6px;
  color: #475569;
}

.page-placeholder {
  min-height: 240px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  border: 1px dashed rgba(148, 163, 184, 0.4);
  background: rgba(248, 250, 252, 0.7);
  color: #475569;
}

.page-placeholder.is-error {
  color: #b91c1c;
}
</style>
