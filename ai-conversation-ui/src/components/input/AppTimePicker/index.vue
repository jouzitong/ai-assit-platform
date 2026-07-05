<script setup lang="ts">
import AppFieldShell from '../shared/AppFieldShell.vue'

defineOptions({
  inheritAttrs: false,
})

withDefaults(
  defineProps<{
    modelValue?: string | Date | null
    label?: string
    hint?: string
    error?: string
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
    isRange?: boolean
    block?: boolean
    required?: boolean
    labelPosition?: 'left' | 'inner'
  }>(),
  {
    modelValue: null,
    placeholder: '',
    clearable: false,
    disabled: false,
    isRange: false,
    block: true,
    required: false,
    labelPosition: 'inner',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | Date | null]
  change: [value: string | Date | null]
  visibleChange: [visible: boolean]
}>()
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
    <el-time-picker
      :model-value="modelValue"
      :placeholder="placeholder"
      :clearable="clearable"
      :disabled="disabled"
      :is-range="isRange"
      style="width: 100%"
      v-bind="$attrs"
      @update:model-value="emit('update:modelValue', $event)"
      @change="emit('change', $event)"
      @visible-change="emit('visibleChange', $event)"
    />
  </AppFieldShell>
</template>
