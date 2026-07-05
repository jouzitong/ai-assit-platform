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
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: #fff;
}

.app-dialog__header {
  padding: 16px 20px 0;
  font-weight: 600;
}

.app-dialog__body {
  padding: 20px;
}
</style>
