<script setup lang="ts">
defineProps<{
  title?: string
}>()
</script>

<template>
  <div class="app-dialog">
    <header v-if="title" class="app-dialog__header">{{ title }}</header>
    <div class="app-dialog__body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.app-dialog {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-solid);
}

.app-dialog__header {
  padding: var(--app-space-4) var(--app-space-5) 0;
  font-weight: 600;
  color: var(--app-title);
}

.app-dialog__body {
  padding: var(--app-space-5);
}
</style>
