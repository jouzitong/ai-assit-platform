<script setup lang="ts">
withDefaults(defineProps<{
  scroll?: boolean
}>(), {
  scroll: true,
})
</script>

<template>
  <div class="layout-dialog-body" :class="{ 'layout-dialog-body--scroll': scroll }">
    <slot />
  </div>
</template>

<style scoped>
.layout-dialog-body {
  min-width: 0;
}

.layout-dialog-body--scroll {
  max-height: var(--app-layout-dialog-body-max-height);
  overflow: auto;
  overscroll-behavior: contain;
}
</style>
