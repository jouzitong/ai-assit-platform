<script setup lang="ts">
withDefaults(defineProps<{
  align?: 'start' | 'center' | 'end' | 'between'
}>(), {
  align: 'end',
})
</script>

<template>
  <div class="layout-action-bar" :class="`layout-action-bar--${align}`" role="toolbar">
    <slot />
  </div>
</template>

<style scoped>
.layout-action-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--app-space-2);
  min-width: 0;
}

.layout-action-bar--start {
  justify-content: flex-start;
}

.layout-action-bar--center {
  justify-content: center;
}

.layout-action-bar--end {
  justify-content: flex-end;
}

.layout-action-bar--between {
  justify-content: space-between;
}

@container (max-width: 520px) {
  .layout-action-bar {
    align-items: stretch;
  }
}
</style>
