<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  columns?: 1 | 2 | 3 | 4
}>(), {
  columns: 2,
})

const gridStyle = computed(() => ({
  '--layout-form-columns': String(props.columns),
}))
</script>

<template>
  <div class="layout-form-grid-container">
    <div class="layout-form-grid" :style="gridStyle">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.layout-form-grid-container {
  width: 100%;
  min-width: 0;
  container-type: inline-size;
}

.layout-form-grid {
  display: grid;
  grid-template-columns: repeat(var(--layout-form-columns), minmax(0, 1fr));
  gap: var(--app-space-4);
  width: 100%;
}

@container (max-width: 560px) {
  .layout-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
