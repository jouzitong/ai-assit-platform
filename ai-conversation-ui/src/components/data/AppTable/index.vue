<script setup lang="ts">
defineProps<{
  columns?: string[]
}>()
</script>

<template>
  <div class="app-table">
    <div class="app-table__head">
      <span v-for="column in columns" :key="column">{{ column }}</span>
    </div>
    <div class="app-table__body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.app-table {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  overflow: hidden;
  background: var(--app-surface-solid);
}

.app-table__head {
  display: grid;
  grid-auto-flow: column;
  gap: 16px;
  padding: 16px 20px;
  background: var(--app-surface-muted);
  color: var(--app-title);
  font-weight: 600;
}

.app-table__body {
  padding: 20px;
}
</style>
