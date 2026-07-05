<script setup lang="ts">
import AppFieldShell from '../shared/AppFieldShell.vue'

defineOptions({
  inheritAttrs: false,
})

withDefaults(
  defineProps<{
    modelValue?: string | number
    label?: string
    hint?: string
    error?: string
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
    type?: string
    block?: boolean
    required?: boolean
    labelPosition?: 'left' | 'inner'
  }>(),
  {
    modelValue: '',
    placeholder: '',
    clearable: false,
    disabled: false,
    type: 'text',
    block: true,
    required: false,
    labelPosition: 'inner',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | number]
  change: [value: string | number]
  blur: [event: FocusEvent]
  focus: [event: FocusEvent]
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
    <el-input
      :model-value="modelValue"
      :type="type"
      :placeholder="placeholder"
      :clearable="clearable"
      :disabled="disabled"
      v-bind="$attrs"
      @update:model-value="emit('update:modelValue', $event)"
      @change="emit('change', $event)"
      @blur="emit('blur', $event)"
      @focus="emit('focus', $event)"
      @clear="emit('clear')"
    >
      <template v-if="$slots.prefix" #prefix>
        <slot name="prefix" />
      </template>
      <template v-if="$slots.suffix" #suffix>
        <slot name="suffix" />
      </template>
      <template v-if="$slots.prepend" #prepend>
        <slot name="prepend" />
      </template>
      <template v-if="$slots.append" #append>
        <slot name="append" />
      </template>
    </el-input>
  </AppFieldShell>
</template>
