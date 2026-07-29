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
import { formatFieldValue, getFieldValue, isFormFieldHidden } from '../schema'
import type { FormRendererField, FormRendererLabelPosition } from '../types'

const props = defineProps<{
  field: FormRendererField
  modelValue: Record<string, unknown>
  schemaComponent?: string
  readonly?: boolean
  error?: string
  defaultSpan?: number
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

const typeComponentMap = {
  text: AppInput,
  textarea: AppInput,
  select: AppSelect,
  date: AppDatePicker,
  daterange: AppDatePicker,
  time: AppTimePicker,
  checkbox: AppCheckbox,
  switch: AppSwitch,
  code: AppCodeEditor,
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
  const type = props.field.type as keyof typeof typeComponentMap | undefined
  if (type && typeComponentMap[type]) {
    return typeComponentMap[type]
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
      options: (props.field.list || []).map(option => ({
        label: option.key,
        value: option.value,
        disabled: option.disabled,
      })),
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
const fieldSpan = computed(() => {
  const configuredSpan = normalizeSpan(props.field.options?.span)
  if (configuredSpan) {
    return configuredSpan
  }
  if (isLongFormField(props.field)) {
    return 12
  }
  return normalizeSpan(props.defaultSpan) || 6
})
const wrapperStyle = computed<Record<string, string | number>>(() => ({
  ...(props.field.options?.styles || {}),
  '--form-field-span': String(fieldSpan.value),
  '--form-field-mobile-span': String(Math.min(fieldSpan.value, 6)),
}))
const labelPosition = computed<FormRendererLabelPosition>(() => {
  const configuredPosition = props.field.options?.labelPosition
  if (configuredPosition === 'inner') {
    return 'inline'
  }
  if (configuredPosition === 'top' || configuredPosition === 'inline' || configuredPosition === 'right') {
    return configuredPosition
  }
  return 'left'
})
const usesInlineLabel = computed(() => labelPosition.value === 'inline')

function handleChange(value: unknown) {
  emit('change', { key: props.field.key, value })
}

function normalizeSpan(value: unknown) {
  const span = Number(value)
  if (!Number.isInteger(span) || span < 1 || span > 12) {
    return undefined
  }
  return span
}

function isLongFormField(field: FormRendererField) {
  return field.component === 'zg-textarea'
    || field.component === 'zg-code-editor'
    || field.type === 'textarea'
    || field.type === 'code'
}
</script>

<template>
  <div
    v-if="!isFormFieldHidden(field)"
    class="form-field-renderer"
    :class="field.options?.className"
    :style="wrapperStyle"
  >
    <div
      v-if="isReadonly"
      class="form-field-renderer__display"
      :class="`form-field-renderer__display--${labelPosition}`"
    >
      <div class="form-field-renderer__label">
        <span v-if="field.options?.required" class="form-field-renderer__required">*</span>
        {{ field.label }}
      </div>
      <div class="form-field-renderer__value">{{ displayValue }}</div>
    </div>

    <div
      v-else
      class="form-field-renderer__editor"
      :class="`form-field-renderer__editor--${labelPosition}`"
    >
      <div v-if="!usesInlineLabel" class="form-field-renderer__label">
        <span v-if="field.options?.required" class="form-field-renderer__required">*</span>
        {{ field.label }}
      </div>
      <div class="form-field-renderer__control">
        <component
          :is="currentComponent"
          :model-value="fieldValue"
          :label="usesInlineLabel ? field.label : undefined"
          :required="usesInlineLabel && field.options?.required"
          :error="error"
          :disabled="field.options?.disabled"
          :label-position="usesInlineLabel ? 'inner' : 'left'"
          block
          v-bind="componentProps"
          @update:model-value="handleChange"
          @change="handleChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-field-renderer {
  grid-column: span var(--form-field-span, 6);
  min-width: 0;
}

.form-field-renderer__editor,
.form-field-renderer__display {
  display: grid;
  align-items: start;
  column-gap: var(--app-space-4);
  row-gap: var(--app-space-2);
  min-height: 100%;
}

.form-field-renderer__editor--left,
.form-field-renderer__display--left {
  grid-template-columns: var(--form-field-label-width, 96px) minmax(0, 1fr);
}

.form-field-renderer__editor--left > .form-field-renderer__label,
.form-field-renderer__display--left > .form-field-renderer__label {
  text-align: right;
}

.form-field-renderer__editor--right,
.form-field-renderer__display--right {
  grid-template-columns: minmax(0, 1fr) var(--form-field-label-width, 96px);
}

.form-field-renderer__editor--right .form-field-renderer__label,
.form-field-renderer__display--right .form-field-renderer__label {
  grid-column: 2;
  grid-row: 1;
}

.form-field-renderer__editor--right .form-field-renderer__control,
.form-field-renderer__display--right .form-field-renderer__value {
  grid-column: 1;
  grid-row: 1;
}

.form-field-renderer__editor--top,
.form-field-renderer__display--top {
  grid-template-columns: minmax(0, 1fr);
}

.form-field-renderer__editor--inline,
.form-field-renderer__display--inline {
  display: block;
}

.form-field-renderer__control {
  min-width: 0;
}

.form-field-renderer__display {
  min-width: 0;
}

.form-field-renderer__display--inline {
  position: relative;
}

.form-field-renderer__display--inline .form-field-renderer__label {
  position: absolute;
  top: 0;
  left: var(--app-space-3);
  z-index: 1;
  padding: 0 var(--app-space-tight);
  transform: translateY(-50%);
  border-radius: var(--app-radius-round);
  background: var(--app-surface-solid);
}

.form-field-renderer__label {
  min-width: 0;
  padding-top: var(--app-space-2);
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.form-field-renderer__editor--top .form-field-renderer__label,
.form-field-renderer__display--top .form-field-renderer__label,
.form-field-renderer__display--inline .form-field-renderer__label {
  padding-top: 0;
}

.form-field-renderer__required {
  margin-right: 4px;
  color: var(--app-danger);
}

.form-field-renderer__value {
  display: flex;
  align-items: center;
  min-width: 0;
  min-height: var(--app-control-height-md);
  padding: var(--app-space-2) var(--app-space-3);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-control);
  background: var(--app-surface-solid);
  color: var(--app-title);
  font-size: var(--app-font-size-body);
  font-weight: 500;
  line-height: 1.5;
  word-break: break-word;
}

.form-field-renderer__display--inline .form-field-renderer__value {
  padding-top: var(--app-space-3);
}

@container application-form-layout (max-width: 680px) {
  .form-field-renderer {
    grid-column: span var(--form-field-mobile-span, 6);
  }
}

@container application-form-layout (max-width: 520px) {
  .form-field-renderer__editor--left,
  .form-field-renderer__editor--right,
  .form-field-renderer__display--left,
  .form-field-renderer__display--right {
    grid-template-columns: minmax(0, 1fr);
  }

  .form-field-renderer__editor--right .form-field-renderer__label,
  .form-field-renderer__display--right .form-field-renderer__label,
  .form-field-renderer__editor--right .form-field-renderer__control,
  .form-field-renderer__display--right .form-field-renderer__value {
    grid-column: 1;
  }

  .form-field-renderer__editor--right .form-field-renderer__label,
  .form-field-renderer__display--right .form-field-renderer__label {
    grid-row: 1;
  }

  .form-field-renderer__editor--right .form-field-renderer__control,
  .form-field-renderer__display--right .form-field-renderer__value {
    grid-row: 2;
  }

  .form-field-renderer__editor--left > .form-field-renderer__label,
  .form-field-renderer__display--left > .form-field-renderer__label {
    text-align: left;
  }
}
</style>
