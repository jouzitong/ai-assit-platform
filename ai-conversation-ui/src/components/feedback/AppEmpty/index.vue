<script setup lang="ts">
defineProps<{
  description?: string
}>()
</script>

<template>
  <div class="app-empty">
    <slot>{{ description ?? '暂无数据' }}</slot>
  </div>
</template>

<style scoped>
.app-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  border: 1px dashed var(--app-border);
  border-radius: 16px;
  color: var(--app-text-soft);
  background: var(--app-surface-muted);
}
</style>
