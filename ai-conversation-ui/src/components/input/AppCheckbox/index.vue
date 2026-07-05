<script setup lang="ts">
import AppFieldShell from '../shared/AppFieldShell.vue'

type CheckboxOption = {
  label: string
  value: string | number | boolean
  disabled?: boolean
}

defineOptions({
  inheritAttrs: false,
})

withDefaults(
  defineProps<{
    modelValue?: boolean | Array<string | number | boolean>
    options?: CheckboxOption[]
    label?: string
    hint?: string
    error?: string
    checkboxLabel?: string
    disabled?: boolean
    block?: boolean
    required?: boolean
    labelPosition?: 'left' | 'inner'
  }>(),
  {
    modelValue: false,
    options: () => [],
    checkboxLabel: '',
    disabled: false,
    block: true,
    required: false,
    labelPosition: 'inner',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean | Array<string | number | boolean>]
  change: [value: boolean | Array<string | number | boolean>]
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
    <el-checkbox-group
      v-if="options.length"
      :model-value="Array.isArray(modelValue) ? modelValue : []"
      :disabled="disabled"
      v-bind="$attrs"
      @update:model-value="emit('update:modelValue', $event)"
      @change="emit('change', $event)"
    >
      <el-checkbox
        v-for="option in options"
        :key="String(option.value)"
        :label="option.value"
        :disabled="option.disabled"
      >
        {{ option.label }}
      </el-checkbox>
    </el-checkbox-group>

    <el-checkbox
      v-else
      :model-value="Boolean(modelValue)"
      :disabled="disabled"
      v-bind="$attrs"
      @update:model-value="emit('update:modelValue', $event)"
      @change="emit('change', $event)"
    >
      <slot>{{ checkboxLabel }}</slot>
    </el-checkbox>
  </AppFieldShell>
</template>
