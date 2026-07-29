<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import FormGroupPanel from './components/FormGroupPanel.vue'
import FormHeaderBar from './components/FormHeaderBar.vue'
import {
  cloneFormValues,
  createFieldMap,
  getFieldValue,
  normalizeSchema,
  setFieldValue,
} from './schema'
import type {
  FormRendererAction,
  FormRendererMode,
  FormRendererResetPayload,
  FormRendererSchema,
  FormRendererSubmitPayload,
} from './types'

const props = withDefaults(
  defineProps<{
    schema: FormRendererSchema
    modelValue?: Record<string, unknown>
    readonly?: boolean
    formMode?: FormRendererMode
    submitting?: boolean
  }>(),
  {
    modelValue: () => ({}),
    readonly: false,
    formMode: 'edit',
    submitting: false,
  },
)

const emit = defineEmits<{
  action: [action: FormRendererAction]
  change: [payload: { key: string; value: unknown; values: Record<string, unknown> }]
  'update:modelValue': [value: Record<string, unknown>]
  submit: [payload: FormRendererSubmitPayload]
  reset: [payload: FormRendererResetPayload]
}>()

const normalizedSchema = computed(() => normalizeSchema(props.schema))
const fieldMap = computed(() => createFieldMap(normalizedSchema.value))
const formState = reactive<Record<string, unknown>>({})
const validationErrors = reactive<Record<string, string>>({})
const initialState = ref<Record<string, unknown>>({})
const effectiveReadonly = computed(() => props.readonly || props.formMode === 'view')
const mutationActionKeys = new Set(['SUBMIT', 'SAVE', 'CREATE', 'UPDATE'])
const visibleActions = computed(() => effectiveReadonly.value
  ? normalizedSchema.value.actions.filter(action => !mutationActionKeys.has(action.action))
  : normalizedSchema.value.actions)
const formLayoutStyle = computed<Record<string, string>>(() => ({
  '--form-field-label-width': normalizeLabelWidth(normalizedSchema.value.form_config.labelWidth),
}))

watch(
  () => ({
    schemaData: normalizedSchema.value.data,
    defaultValues: normalizedSchema.value.form_config.defaultValues,
    modelValue: props.modelValue,
    formMode: props.formMode,
  }),
  ({ schemaData, defaultValues, modelValue, formMode }) => {
    const baseValues = formMode === 'add'
      ? defaultValues || {}
      : schemaData || {}
    const nextValues = {
      ...cloneFormValues(baseValues),
      ...cloneFormValues(modelValue || {}),
    }
    if (JSON.stringify(nextValues) === JSON.stringify(formState)) {
      return
    }
    initialState.value = cloneFormValues(nextValues)
    Object.keys(formState).forEach((key) => {
      delete formState[key]
    })
    Object.assign(formState, nextValues)
    clearValidationErrors()
  },
  { deep: true, immediate: true },
)

const handleAction = (action: FormRendererAction) => {
  if (action.action === 'RESET') {
    replaceFormState(initialState.value)
    clearValidationErrors()
    const values = cloneFormValues(formState)
    emit('update:modelValue', values)
    emit('reset', { values })
  } else if (mutationActionKeys.has(action.action)) {
    if (!validateForm()) {
      return
    }
    emit('submit', {
      action,
      values: cloneFormValues(formState),
    })
  }
  emit('action', action)
}

function handleFieldChange(payload: { key: string; value: unknown }) {
  const field = fieldMap.value[payload.key]
  if (!field) {
    return
  }
  setFieldValue(formState, field, payload.value)
  delete validationErrors[payload.key]
  const nextState = cloneFormValues(formState)
  emit('update:modelValue', nextState)
  emit('change', { ...payload, values: nextState })
}

function replaceFormState(values: Record<string, unknown>) {
  Object.keys(formState).forEach((key) => {
    delete formState[key]
  })
  Object.assign(formState, cloneFormValues(values))
}

function validateForm() {
  clearValidationErrors()
  normalizedSchema.value.fields.forEach((field) => {
    if (!field.options?.required || field.options.hidden || field.options.readonly) {
      return
    }
    if (isEmptyValue(getFieldValue(formState, field))) {
      validationErrors[field.key] = `${field.label}不能为空`
    }
  })
  return Object.keys(validationErrors).length === 0
}

function clearValidationErrors() {
  Object.keys(validationErrors).forEach((key) => {
    delete validationErrors[key]
  })
}

function isEmptyValue(value: unknown) {
  return value == null
    || (typeof value === 'string' && value.trim() === '')
    || (Array.isArray(value) && value.length === 0)
}

function normalizeLabelWidth(value: string | number | undefined) {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    return `${value}px`
  }
  const normalized = String(value || '').trim()
  return /^\d+(?:\.\d+)?(?:px|rem|em|%)$/.test(normalized) ? normalized : '96px'
}
</script>

<template>
  <section
    class="form-main-layout"
    :class="`form-main-layout--${normalizedSchema.form_config.variant}`"
    :style="formLayoutStyle"
  >
    <FormHeaderBar
      :title="normalizedSchema.title"
      :description="normalizedSchema.form_config.description"
      :actions="visibleActions"
      :actions-align="normalizedSchema.form_config.actionsAlign"
      :submitting="submitting"
      @action="handleAction"
    />

    <div class="form-main-layout__groups">
      <FormGroupPanel
        v-for="group in normalizedSchema.groups"
        :key="group.key"
        :group="group"
        :fields="fieldMap"
        :model-value="formState"
        :errors="validationErrors"
        :schema-component="normalizedSchema.component"
        :readonly="effectiveReadonly"
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
