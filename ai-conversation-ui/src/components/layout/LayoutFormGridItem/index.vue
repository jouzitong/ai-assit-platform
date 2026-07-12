<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  span?: 1 | 2 | 3 | 4 | 'full'
}>(), {
  span: 1,
})

const itemStyle = computed(() => ({
  '--layout-form-item-span': props.span === 'full' ? '-1' : String(props.span),
}))
</script>

<template>
  <div class="layout-form-grid-item" :class="{ 'layout-form-grid-item--full': span === 'full' }" :style="itemStyle">
    <slot />
  </div>
</template>

<style scoped>
.layout-form-grid-item {
  grid-column: span var(--layout-form-item-span);
  min-width: 0;
}

.layout-form-grid-item--full {
  grid-column: 1 / -1;
}

@container (max-width: 560px) {
  .layout-form-grid-item {
    grid-column: 1 / -1;
  }
}
</style>
