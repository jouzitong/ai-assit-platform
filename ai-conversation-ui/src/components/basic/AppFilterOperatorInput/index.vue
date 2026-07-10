<script setup lang="ts">
import { computed } from 'vue'
import AppFieldShell from '../../input/shared/AppFieldShell.vue'

type FilterOperator = 'eq' | 'like' | 'is_null' | 'is_not_null' | string

const props = withDefaults(
  defineProps<{
    modelValue?: unknown
    label?: string
    hint?: string
    error?: string
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
    block?: boolean
    required?: boolean
    labelPosition?: 'left' | 'inner'
    operators?: Array<{ label: string; value: string }>
    defaultOperator?: FilterOperator
  }>(),
  {
    modelValue: '',
    placeholder: '',
    clearable: true,
    disabled: false,
    block: true,
    required: false,
    labelPosition: 'inner',
    operators: () => [
      { label: '=', value: 'eq' },
      { label: 'like', value: 'like' },
      { label: 'is_null', value: 'is_null' },
      { label: 'is_not_null', value: 'is_not_null' },
    ],
    defaultOperator: 'like',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: { op: FilterOperator; value?: unknown }]
  change: [value: { op: FilterOperator; value?: unknown }]
  blur: [event: FocusEvent]
  focus: [event: FocusEvent]
  clear: []
}>()

const resolvedOperator = computed<FilterOperator>(() => {
  if (isRecord(props.modelValue) && typeof props.modelValue.op === 'string') {
    return props.modelValue.op
  }
  return props.defaultOperator
})

const resolvedValue = computed(() => {
  if (isRecord(props.modelValue) && Object.prototype.hasOwnProperty.call(props.modelValue, 'value')) {
    return props.modelValue.value as string | number
  }
  return props.modelValue as string | number
})

const isNullOperator = computed(() =>
  resolvedOperator.value === 'is_null' || resolvedOperator.value === 'is_not_null',
)

const resolvedOperatorLabel = computed(() =>
  props.operators.find((operator) => operator.value === resolvedOperator.value)?.label || resolvedOperator.value,
)

function emitValue(op: FilterOperator, value: unknown, eventName: 'update' | 'change' = 'update') {
  const payload = isNullOperatorByValue(op) ? { op } : { op, value }
  if (eventName === 'change') {
    emit('change', payload)
    return
  }
  emit('update:modelValue', payload)
}

function handleOperatorChange(op: string | number | boolean) {
  const nextOperator = String(op)
  emitValue(nextOperator, resolvedValue.value)
  emit('change', isNullOperatorByValue(nextOperator) ? { op: nextOperator } : { op: nextOperator, value: resolvedValue.value })
}

function handleInput(value: string | number) {
  emitValue(resolvedOperator.value, value)
}

function handleChange(value: string | number) {
  emitValue(resolvedOperator.value, value, 'change')
}

function handleClear() {
  emitValue(resolvedOperator.value, '')
  emit('clear')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function isNullOperatorByValue(value: FilterOperator) {
  return value === 'is_null' || value === 'is_not_null'
}
</script>

<template>
  <AppFieldShell
    :label="label"
    :hint="hint"
    :error="error"
    :block="block"
    :required="required"
    :label-position="labelPosition"
  >
    <el-popover
      placement="bottom-start"
      trigger="click"
      width="220"
      :disabled="disabled"
    >
      <template #reference>
        <div class="app-filter-operator-input">
          <el-input
            :model-value="isNullOperator ? '' : resolvedValue"
            :placeholder="isNullOperator ? resolvedOperator : placeholder"
            :clearable="clearable"
            :disabled="disabled || isNullOperator"
            @update:model-value="handleInput"
            @change="handleChange"
            @blur="emit('blur', $event)"
            @focus="emit('focus', $event)"
            @clear="handleClear"
          >
            <template #prefix>
              <span class="app-filter-operator-input__op">{{ resolvedOperatorLabel }}</span>
            </template>
          </el-input>
        </div>
      </template>

      <div class="app-filter-operator-input__popover">
        <div class="app-filter-operator-input__title">过滤操作</div>
        <el-radio-group
          :model-value="resolvedOperator"
          class="app-filter-operator-input__ops"
          @change="handleOperatorChange"
        >
          <el-radio-button
            v-for="operator in operators"
            :key="operator.value"
            :label="operator.value"
          >
            {{ operator.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
    </el-popover>
  </AppFieldShell>
</template>

<style scoped>
.app-filter-operator-input {
  width: 100%;
}

.app-filter-operator-input__op {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 20px;
}

.app-filter-operator-input__popover {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.app-filter-operator-input__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.app-filter-operator-input__ops {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.app-filter-operator-input__ops :deep(.el-radio-button__inner) {
  width: 100%;
  border-left: var(--el-border);
  border-radius: 4px;
}
</style>
