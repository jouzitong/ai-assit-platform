<script setup lang="ts">
import { VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { AppCodeEditor, LayoutFormGrid, LayoutFormGridItem } from '../../../../components'

const props = withDefaults(defineProps<{
  execute: (payload: Record<string, unknown>) => Promise<Record<string, unknown>>
  disabled?: boolean
  disabledHint?: string
  inputHint?: string
}>(), {
  disabled: false,
  disabledHint: '请先保存草稿，再执行测试。',
  inputHint: '输入测试运行所需的 JSON Object。',
})

const inputText = ref('{}')
const resultText = ref('')
const loading = ref(false)

async function run() {
  if (props.disabled) {
    ElMessage.warning(props.disabledHint)
    return
  }
  let payload: Record<string, unknown>
  try {
    const parsed = JSON.parse(inputText.value || '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error()
    payload = parsed as Record<string, unknown>
  }
  catch {
    ElMessage.error('测试输入必须是合法 JSON Object')
    return
  }

  loading.value = true
  resultText.value = ''
  try {
    const result = await props.execute(payload)
    resultText.value = JSON.stringify(result ?? {}, null, 2)
    ElMessage.success('测试运行完成')
  }
  catch (error) {
    const message = error instanceof Error ? error.message : '测试运行失败'
    resultText.value = JSON.stringify({ success: false, message }, null, 2)
    ElMessage.error(message)
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="definition-test-run">
    <el-alert type="info" :closable="false" :title="inputHint" />
    <LayoutFormGrid :columns="2">
      <LayoutFormGridItem>
        <el-form-item label="测试输入 JSON">
          <AppCodeEditor v-model="inputText" format="json" min-height="300px" :max-rows="18" />
        </el-form-item>
      </LayoutFormGridItem>
      <LayoutFormGridItem>
        <el-form-item label="测试结果">
          <AppCodeEditor
            v-model="resultText"
            format="json"
            readonly
            min-height="300px"
            :max-rows="18"
            placeholder="运行后展示服务端返回结果"
          />
        </el-form-item>
      </LayoutFormGridItem>
    </LayoutFormGrid>
    <div class="definition-test-run__actions">
      <span v-if="disabled" class="definition-test-run__hint">{{ disabledHint }}</span>
      <el-button type="primary" :icon="VideoPlay" :loading="loading" :disabled="disabled" @click="run">执行测试</el-button>
    </div>
  </div>
</template>

<style scoped>
.definition-test-run {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-4);
}

.definition-test-run__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--app-space-3);
}

.definition-test-run__hint {
  color: var(--system-text-muted);
  font-size: var(--app-font-size-sm);
}
</style>
