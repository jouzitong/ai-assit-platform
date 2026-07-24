<script setup lang="ts">
import ListFilterBar from '../../../application/renderers/list/components/ListFilterBar.vue'
import type { RendererFilter } from '../../../application/schema'

defineProps<{
  filters: RendererFilter[]
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
  submit: []
  reset: []
}>()
</script>

<template>
  <ListFilterBar
    :filters="filters"
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    @submit="emit('submit')"
    @reset="emit('reset')"
  />
</template>
