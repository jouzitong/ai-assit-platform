<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FormMainLayout from '../../../application/renderers/form/FormMainLayout.vue'
import type { FormRendererAction, FormRendererSchema } from '../../../application/renderers/form/types'
import infoSchemaRaw from '../fixtures/info.schema.json'

const baseSchema = {
  ...(infoSchemaRaw as FormRendererSchema),
  title: '基础信息表单',
  component: 'zg-common-info',
  form_config: {
    variant: 'workbench',
    columns: 2,
    description: 'JSON 结构参考 data/settings/info.json。',
    actionsAlign: 'right',
  },
  fields: [
    ...(infoSchemaRaw.fields || []),
    {
      key: 'description',
      name: 'description',
      label: '描述',
      field: ['description'],
      component: 'zg-textarea',
      type: 'textarea',
      options: {
        rows: 4,
        styles: {
          width: 100,
        },
      },
    },
  ],
  actions: [
    {
      key: 'save',
      name: '保存',
      action: 'SAVE',
      type: 'primary',
    },
  ],
  data: {
    name: 'META-10001',
    description: '维护统一元数据项、分类结构和可配置内容。',
  },
} satisfies FormRendererSchema

const formSchema = ref<FormRendererSchema>(JSON.parse(JSON.stringify(baseSchema)))
const formState = reactive<Record<string, unknown>>({
  ...(formSchema.value.data || {}),
})

function handleAction(action: FormRendererAction) {
  if (action.action === 'SAVE') {
    ElMessage.success(`保存成功：${JSON.stringify(formState)}`)
    return
  }
  ElMessage.info(action.action)
}

function handleChange(payload: { key: string; value: unknown; values: Record<string, unknown> }) {
  Object.assign(formState, payload.values)
}
</script>

<template>
  <section class="test-form-view">
    <FormMainLayout
      v-model="formState"
      :schema="formSchema"
      @action="handleAction"
      @change="handleChange"
    />
  </section>
</template>

<style scoped>
.test-form-view {
  min-height: 100%;
}
</style>
