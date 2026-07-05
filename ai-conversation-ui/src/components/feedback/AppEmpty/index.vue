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
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
  color: #64748b;
  background: #f8fafc;
}
</style>
