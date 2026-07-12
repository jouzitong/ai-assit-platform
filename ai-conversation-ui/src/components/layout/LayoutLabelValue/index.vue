<script setup lang="ts">
defineProps<{
  label: string
  value?: string | number | null
  description?: string
  title?: string
}>()
</script>

<template>
  <div class="layout-label-value">
    <div class="layout-label-value__label">{{ label }}</div>
    <div class="layout-label-value__content">
      <div class="layout-label-value__value" :title="title || (value == null ? '' : String(value))">
        <slot>{{ value == null || value === '' ? '—' : value }}</slot>
      </div>
      <p v-if="description" class="layout-label-value__description">{{ description }}</p>
    </div>
  </div>
</template>

<style scoped>
.layout-label-value {
  display: grid;
  gap: var(--app-space-1);
  min-width: 0;
}

.layout-label-value__label {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  line-height: 1.5;
}

.layout-label-value__content {
  min-width: 0;
}

.layout-label-value__value {
  overflow: hidden;
  color: var(--app-title);
  font-size: var(--app-font-size-body);
  font-weight: 500;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout-label-value__description {
  margin: var(--app-space-1) 0 0;
  color: var(--app-text-faint);
  font-size: var(--app-font-size-caption);
  line-height: 1.5;
}
</style>
