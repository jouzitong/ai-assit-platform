<script setup lang="ts">
withDefaults(defineProps<{
  align?: 'start' | 'center' | 'end' | 'between'
}>(), {
  align: 'end',
})
</script>

<template>
  <footer class="layout-dialog-footer" :class="`layout-dialog-footer--${align}`">
    <slot />
  </footer>
</template>

<style scoped>
.layout-dialog-footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--app-space-2);
  min-width: 0;
}

.layout-dialog-footer--start {
  justify-content: flex-start;
}

.layout-dialog-footer--center {
  justify-content: center;
}

.layout-dialog-footer--end {
  justify-content: flex-end;
}

.layout-dialog-footer--between {
  justify-content: space-between;
}

.layout-dialog-footer :deep(.el-button) {
  min-width: var(--app-layout-action-min-width);
}
</style>
