<script setup lang="ts">
import { computed } from 'vue'
import { useResponsiveOverlayTarget } from '../../../composables/useResponsiveViewport'
import AppFieldShell from '../shared/AppFieldShell.vue'

type TreeOption = {
  label: string
  value?: string | number
  key?: string | number
  children?: TreeOption[]
  disabled?: boolean
  [key: string]: unknown
}

defineOptions({
  inheritAttrs: false,
})

const props = withDefaults(
  defineProps<{
    modelValue?: string | number | Array<string | number> | null
    data?: TreeOption[]
    label?: string
    hint?: string
    error?: string
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
    multiple?: boolean
    checkStrictly?: boolean
    renderAfterExpand?: boolean
    block?: boolean
    required?: boolean
    labelPosition?: 'left' | 'inner'
    teleported?: boolean
    appendTo?: string | HTMLElement
  }>(),
  {
    modelValue: null,
    data: () => [],
    placeholder: '',
    clearable: false,
    disabled: false,
    multiple: false,
    checkStrictly: true,
    renderAfterExpand: false,
    block: true,
    required: false,
    labelPosition: 'inner',
  },
)

const responsiveOverlayTarget = useResponsiveOverlayTarget()
const resolvedTeleported = computed(() => props.teleported ?? true)
const resolvedAppendTo = computed(() => props.appendTo ?? responsiveOverlayTarget.value)

const emit = defineEmits<{
  'update:modelValue': [value: string | number | Array<string | number> | null]
  change: [value: string | number | Array<string | number> | null]
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
    <el-tree-select
      :model-value="modelValue"
      :data="data"
      :placeholder="placeholder"
      :clearable="clearable"
      :disabled="disabled"
      :multiple="multiple"
      :check-strictly="checkStrictly"
      :render-after-expand="renderAfterExpand"
      :teleported="resolvedTeleported"
      :append-to="resolvedAppendTo"
      style="width: 100%"
      v-bind="$attrs"
      @update:model-value="emit('update:modelValue', $event)"
      @change="emit('change', $event)"
      @visible-change="emit('visibleChange', $event)"
      @clear="emit('clear')"
    />
  </AppFieldShell>
</template>
