<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useResponsiveOverlayTarget } from '../../../composables/useResponsiveViewport'
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
      { label: '等于', value: 'eq' },
      { label: '模糊查询', value: 'like' },
      { label: '为空', value: 'is_null' },
      { label: '不为空', value: 'is_not_null' },
    ],
    defaultOperator: 'like',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: { op: FilterOperator; value?: unknown }]
  change: [value: { op: FilterOperator; value?: unknown }]
  blur: [event: FocusEvent]
  focus: [event: FocusEvent]
  keyup: [event: KeyboardEvent]
  confirm: []
  clear: []
}>()

const popoverVisible = ref(false)
const shellRef = ref<HTMLElement>()
const popoverRef = ref<HTMLElement>()
const responsiveOverlayTarget = useResponsiveOverlayTarget()

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

const displayValue = computed(() => {
  if (isNullOperator.value) {
    return resolvedOperatorLabel.value
  }
  return resolvedValue.value
})

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

function openPopover() {
  if (!props.disabled) {
    popoverVisible.value = true
  }
}

function closePopover() {
  popoverVisible.value = false
}

function handleReferenceFocus(event: FocusEvent) {
  openPopover()
  emit('focus', event)
}

function handleValueEnter(event: KeyboardEvent) {
  closePopover()
  emit('keyup', event)
}

function handleConfirm() {
  emitValue(resolvedOperator.value, resolvedValue.value, 'change')
  closePopover()
  emit('confirm')
}

function handleDocumentPointerDown(event: PointerEvent) {
  if (!popoverVisible.value) {
    return
  }

  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  if (shellRef.value?.contains(target) || popoverRef.value?.contains(target)) {
    return
  }

  closePopover()
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function isNullOperatorByValue(value: FilterOperator) {
  return value === 'is_null' || value === 'is_not_null'
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown, true)
})
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
      v-model:visible="popoverVisible"
      placement="bottom-start"
      trigger="manual"
      width="340"
      :disabled="disabled"
      :append-to="responsiveOverlayTarget"
      popper-class="app-filter-operator-input-popper"
      transition="app-filter-operator-input-slide"
    >
      <template #reference>
        <div
          ref="shellRef"
          class="app-filter-operator-input"
          @click="openPopover"
        >
          <el-input
            :model-value="displayValue"
            :placeholder="placeholder"
            :clearable="clearable"
            :disabled="disabled"
            readonly
            @update:model-value="handleInput"
            @change="handleChange"
            @blur="emit('blur', $event)"
            @focus="handleReferenceFocus"
            @clear="handleClear"
          />
        </div>
      </template>

      <div
        ref="popoverRef"
        class="app-filter-operator-input__popover"
      >
        <div class="app-filter-operator-input__inline">
          <el-select
            :model-value="resolvedOperator"
            class="app-filter-operator-input__op"
            size="small"
            :teleported="false"
            @change="handleOperatorChange"
          >
            <el-option
              v-for="operator in operators"
              :key="operator.value"
              :label="operator.label"
              :value="operator.value"
            />
          </el-select>
          <el-input
            :model-value="isNullOperator ? '' : resolvedValue"
            :placeholder="isNullOperator ? '当前操作不需要输入值' : placeholder"
            :disabled="isNullOperator"
            clearable
            size="small"
            class="app-filter-operator-input__control"
            @update:model-value="handleInput"
            @change="handleChange"
            @keyup.enter="handleValueEnter"
            @clear="handleClear"
          />
          <el-button
            type="primary"
            size="small"
            class="app-filter-operator-input__confirm"
            @click="handleConfirm"
          >
            确定
          </el-button>
        </div>
      </div>
    </el-popover>
  </AppFieldShell>
</template>

<style scoped>
.app-filter-operator-input {
  width: 100%;
}

.app-filter-operator-input__popover {
  padding: var(--app-space-hairline) 0;
}

.app-filter-operator-input__inline {
  display: flex;
  align-items: center;
  gap: var(--app-space-tight);
  min-width: 0;
}

.app-filter-operator-input__op {
  width: 96px;
  flex: 0 0 96px;
}

.app-filter-operator-input__op :deep(.el-select__suffix) {
  opacity: 0;
  transition: opacity 0.16s ease;
}

.app-filter-operator-input__op:hover :deep(.el-select__suffix),
.app-filter-operator-input__op:focus-within :deep(.el-select__suffix) {
  opacity: 1;
}

.app-filter-operator-input__control {
  width: 100%;
}

.app-filter-operator-input__confirm {
  flex: 0 0 auto;
}

:global(.app-filter-operator-input-popper) {
  transform-origin: top left;
}

:global(.app-filter-operator-input-slide-enter-active),
:global(.app-filter-operator-input-slide-leave-active) {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

:global(.app-filter-operator-input-slide-enter-from),
:global(.app-filter-operator-input-slide-leave-to) {
  opacity: 0;
  transform: translateY(-6px) scaleY(0.96);
}

:global(.app-filter-operator-input-slide-enter-to),
:global(.app-filter-operator-input-slide-leave-from) {
  opacity: 1;
  transform: translateY(0) scaleY(1);
}
</style>
