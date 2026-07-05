<script setup lang="ts">
import AppFieldShell from '../shared/AppFieldShell.vue'

type SelectOption = {
  label: string
  value: string | number | boolean
  disabled?: boolean
}

defineOptions({
  inheritAttrs: false,
})

withDefaults(
  defineProps<{
    modelValue?: string | number | boolean | Array<string | number | boolean> | null
    options?: SelectOption[]
    label?: string
    hint?: string
    error?: string
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
    filterable?: boolean
    multiple?: boolean
    block?: boolean
    required?: boolean
    labelPosition?: 'left' | 'inner'
  }>(),
  {
    modelValue: null,
    options: () => [],
    placeholder: '',
    clearable: false,
    disabled: false,
    filterable: false,
    multiple: false,
    block: true,
    required: false,
    labelPosition: 'inner',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | number | boolean | Array<string | number | boolean> | null]
  change: [value: string | number | boolean | Array<string | number | boolean> | null]
  visibleChange: [visible: boolean]
  clear: []
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
    <el-select
      :model-value="modelValue"
      :placeholder="placeholder"
      :clearable="clearable"
      :disabled="disabled"
      :filterable="filterable"
      :multiple="multiple"
      v-bind="$attrs"
      @update:model-value="emit('update:modelValue', $event)"
      @change="emit('change', $event)"
      @visible-change="emit('visibleChange', $event)"
      @clear="emit('clear')"
    >
      <template v-if="$slots.default">
        <slot />
      </template>
      <el-option
        v-for="option in options"
        v-else
        :key="String(option.value)"
        :label="option.label"
        :value="option.value"
        :disabled="option.disabled"
      />
    </el-select>
  </AppFieldShell>
</template>
