<script setup>
import AppControl from '../AppControl.vue'

defineProps({
  schema: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['action'])

function handleFieldUpdate(field, payload) {
  if (!payload) return
  field.value = payload.value
}

function handleFieldAction(payload) {
  if (!payload?.field) return
  emit('action', {
    action: payload.field.action,
    key: payload.field.key,
    value: payload.value,
    field: payload.field
  })
}
</script>

<template>
  <div class="search">
    <AppControl
      v-for="(field, index) in schema"
      :key="`${field.key}-${index}`"
      :field="field"
      size="compact"
      @update="handleFieldUpdate(field, $event)"
      @action="handleFieldAction"
    />
  </div>
</template>

<style scoped>
.search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  background: var(--surface-bg-3);
  padding: 6px 8px;
  border-radius: 12px;
  border: 1px solid var(--stroke);
}
</style>
