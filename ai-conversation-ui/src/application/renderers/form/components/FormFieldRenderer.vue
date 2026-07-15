<script setup lang="ts">
import { computed } from 'vue'
import AppCheckbox from '../../../../components/input/AppCheckbox/index.vue'
import AppCodeEditor from '../../../../components/input/AppCodeEditor/index.vue'
import AppDatePicker from '../../../../components/input/AppDatePicker/index.vue'
import AppInput from '../../../../components/input/AppInput/index.vue'
import AppSelect from '../../../../components/input/AppSelect/index.vue'
import AppSelectTree from '../../../../components/input/AppSelectTree/index.vue'
import AppSwitch from '../../../../components/input/AppSwitch/index.vue'
import AppTimePicker from '../../../../components/input/AppTimePicker/index.vue'
import { formatFieldValue, getFieldValue } from '../schema'
import type { FormRendererField } from '../types'

const props = defineProps<{
  field: FormRendererField
  modelValue: Record<string, unknown>
  schemaComponent?: string
  readonly?: boolean
}>()

const emit = defineEmits<{
  change: [payload: { key: string; value: unknown }]
}>()

const componentMap = {
  'zg-input': AppInput,
  'zg-textarea': AppInput,
  'zg-selector': AppSelect,
  'zg-selector-tree': AppSelectTree,
  'zg-date-picker': AppDatePicker,
  'zg-time-picker': AppTimePicker,
  'zg-checkbox': AppCheckbox,
  'zg-switch': AppSwitch,
  'zg-code-editor': AppCodeEditor,
} as const

const fieldValue = computed(() => getFieldValue(props.modelValue, props.field))
const isReadonly = computed(() => {
  if (props.readonly) {
    return true
  }
  if (props.field.options?.readonly) {
    return true
  }
  return props.schemaComponent === 'zg-common-info' && !props.field.component
})
const currentComponent = computed(() => {
  if (isReadonly.value) {
    return null
  }
  const key = props.field.component as keyof typeof componentMap | undefined
  if (key && componentMap[key]) {
    return componentMap[key]
  }
  return AppInput
})
const componentProps = computed(() => {
  const options = props.field.options || {}
  const placeholder = options.placeholder || `请输入${props.field.label}`

  if (props.field.component === 'zg-textarea' || props.field.type === 'textarea') {
    return {
      type: 'textarea',
      rows: Number(options.rows || 4),
      placeholder,
      ...props.field.componentProps,
    }
  }

  if (props.field.component === 'zg-selector') {
    return {
      options: props.field.list || [],
      placeholder: options.placeholder || `请选择${props.field.label}`,
      clearable: options.clearable ?? true,
      filterable: options.filterable ?? false,
      multiple: options.multiple ?? false,
      ...props.field.componentProps,
    }
  }

  if (props.field.component === 'zg-checkbox') {
    return {
      options: props.field.list || [],
      checkboxLabel: props.field.label,
      ...props.field.componentProps,
    }
  }

  return {
    placeholder,
    clearable: options.clearable ?? true,
    ...props.field.componentProps,
  }
})
const displayValue = computed(() => formatFieldValue(fieldValue.value))
const wrapperStyle = computed(() => props.field.options?.styles || {})

function handleChange(value: unknown) {
  emit('change', { key: props.field.key, value })
}
</script>

<template>
  <div
    v-if="!field.options?.hidden"
    class="form-field-renderer"
    :class="field.options?.className"
    :style="wrapperStyle"
  >
    <div v-if="isReadonly" class="form-field-renderer__display">
      <div class="form-field-renderer__label">{{ field.label }}</div>
      <div class="form-field-renderer__value">{{ displayValue }}</div>
    </div>

    <component
      :is="currentComponent"
      v-else
      :model-value="fieldValue"
      :label="field.label"
      :required="field.options?.required"
      :disabled="field.options?.disabled"
      :label-position="field.options?.labelPosition || 'left'"
      block
      v-bind="componentProps"
      @update:model-value="handleChange"
      @change="handleChange"
    />
  </div>
</template>

<style scoped>
.form-field-renderer {
  min-width: 0;
}

.form-field-renderer__display {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-2);
  min-height: 100%;
  padding: var(--app-space-4) var(--app-space-roomy);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-muted);
}

.form-field-renderer__label {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  font-weight: 600;
}

.form-field-renderer__value {
  color: var(--app-title);
  font-size: var(--app-font-size-body-lg);
  line-height: 1.6;
  word-break: break-word;
}
</style>
