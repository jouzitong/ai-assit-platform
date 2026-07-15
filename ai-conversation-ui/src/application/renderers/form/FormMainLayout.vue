<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import FormGroupPanel from './components/FormGroupPanel.vue'
import FormHeaderBar from './components/FormHeaderBar.vue'
import { createFieldMap, normalizeSchema } from './schema'
import type { FormRendererAction, FormRendererSchema } from './types'

const props = withDefaults(
  defineProps<{
    schema: FormRendererSchema
    modelValue?: Record<string, unknown>
    readonly?: boolean
  }>(),
  {
    modelValue: () => ({}),
    readonly: false,
  },
)

const emit = defineEmits<{
  action: [action: FormRendererAction]
  change: [payload: { key: string; value: unknown; values: Record<string, unknown> }]
  'update:modelValue': [value: Record<string, unknown>]
}>()

const normalizedSchema = computed(() => normalizeSchema(props.schema))
const fieldMap = computed(() => createFieldMap(normalizedSchema.value))
const formState = reactive<Record<string, unknown>>({})

watch(
  () => [normalizedSchema.value.data, props.modelValue],
  ([schemaData, modelValue]) => {
    Object.keys(formState).forEach((key) => {
      delete formState[key]
    })
    Object.assign(formState, schemaData || {}, modelValue || {})
  },
  { deep: true, immediate: true },
)

const handleAction = (action: FormRendererAction) => {
  emit('action', action)
}

function handleFieldChange(payload: { key: string; value: unknown }) {
  formState[payload.key] = payload.value
  const nextState = { ...formState }
  emit('update:modelValue', nextState)
  emit('change', { ...payload, values: nextState })
}
</script>

<template>
  <section class="form-main-layout" :class="`form-main-layout--${normalizedSchema.form_config.variant}`">
    <FormHeaderBar
      :title="normalizedSchema.title"
      :description="normalizedSchema.form_config.description"
      :actions="normalizedSchema.actions"
      :actions-align="normalizedSchema.form_config.actionsAlign"
      @action="handleAction"
    />

    <div class="form-main-layout__groups">
      <FormGroupPanel
        v-for="group in normalizedSchema.groups"
        :key="group.key"
        :group="group"
        :fields="fieldMap"
        :model-value="formState"
        :schema-component="normalizedSchema.component"
        :readonly="readonly"
        @change="handleFieldChange"
      />
    </div>
  </section>
</template>

<style scoped>
.form-main-layout {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-5);
  width: 100%;
  min-width: 0;
  min-height: 100%;
  container: application-form-layout / inline-size;
}

.form-main-layout--workbench {
  padding: var(--app-space-2);
}

.form-main-layout__groups {
  display: grid;
  gap: var(--app-space-roomy);
}
</style>
