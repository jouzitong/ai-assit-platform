<script setup lang="ts">
import AppFieldShell from '../shared/AppFieldShell.vue'

defineOptions({
  inheritAttrs: false,
})

withDefaults(
  defineProps<{
    modelValue?: boolean | string | number
    label?: string
    hint?: string
    error?: string
    disabled?: boolean
    activeText?: string
    inactiveText?: string
    block?: boolean
    required?: boolean
    labelPosition?: 'left' | 'inner'
  }>(),
  {
    modelValue: false,
    disabled: false,
    activeText: '',
    inactiveText: '',
    block: true,
    required: false,
    labelPosition: 'inner',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean | string | number]
  change: [value: boolean | string | number]
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
    <div class="app-switch">
      <el-switch
        :model-value="modelValue"
        :disabled="disabled"
        v-bind="$attrs"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
      />
      <span v-if="activeText || inactiveText" class="app-switch__text">
        {{ modelValue ? activeText : inactiveText }}
      </span>
    </div>
  </AppFieldShell>
</template>

<style scoped>
.app-switch {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.app-switch__text {
  font-size: 13px;
  color: var(--app-text);
}
</style>
