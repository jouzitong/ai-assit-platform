<script setup>
import AppControl from '../AppControl.vue'

defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  schema: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['change', 'action'])

function updateField(field, payload) {
  if (!payload) return
  field.value = payload.value
  emit('change', payload)
  emit('action', {
    action: field.action,
    key: field.key,
    value: payload.value,
    field
  })
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

function getItemStyle(field) {
  const width = field?.type_config?.width
  if (!width) return {}
  return { width: typeof width === 'number' ? `${width}px` : width }
}
</script>

<template>
  <div class="filter-panel" :class="{ 'is-collapsed': !visible }">
    <div class="filter-grid">
      <label v-for="(field, index) in schema" :key="index" :style="getItemStyle(field)">
        {{ field.label }}
        <AppControl :field="field" @update="updateField(field, $event)" @action="handleFieldAction" />
      </label>
    </div>
    <slot name="footer" />
  </div>
</template>

<style scoped>
.filter-panel {
  margin: 8px 20px 0;
  border: 1px solid var(--stroke);
  border-radius: 12px;
  background: var(--surface-bg-3);
  overflow: hidden;
  max-height: 240px;
  transition: max-height 0.25s ease, opacity 0.2s ease;
}

.filter-panel.is-collapsed {
  max-height: 0;
  opacity: 0;
  border: 0;
  margin-top: 0;
}

.filter-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
}

.filter-grid label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--text-dim);
}
</style>
