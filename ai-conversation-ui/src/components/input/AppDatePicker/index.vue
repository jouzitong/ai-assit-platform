<script setup lang="ts">
import AppFieldShell from '../shared/AppFieldShell.vue'

defineOptions({
  inheritAttrs: false,
})

withDefaults(
  defineProps<{
    modelValue?: string | string[] | Date | Date[] | null
    label?: string
    hint?: string
    error?: string
    placeholder?: string
    type?: string
    clearable?: boolean
    disabled?: boolean
    block?: boolean
    required?: boolean
    labelPosition?: 'left' | 'inner'
  }>(),
  {
    modelValue: null,
    placeholder: '',
    type: 'date',
    clearable: false,
    disabled: false,
    block: true,
    required: false,
    labelPosition: 'inner',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | string[] | Date | Date[] | null]
  change: [value: string | string[] | Date | Date[] | null]
  calendarChange: [value: unknown]
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
    <el-date-picker
      :model-value="modelValue"
      :type="type"
      :placeholder="placeholder"
      :clearable="clearable"
      :disabled="disabled"
      style="width: 100%"
      v-bind="$attrs"
      @update:model-value="emit('update:modelValue', $event)"
      @change="emit('change', $event)"
      @calendar-change="emit('calendarChange', $event)"
      @visible-change="emit('visibleChange', $event)"
    />
  </AppFieldShell>
</template>
