<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isFullscreen = computed(() => route.meta.fullscreen === true)
</script>

<template>
  <div :class="['default-layout', { 'default-layout--fullscreen': isFullscreen }]">
    <main class="default-layout__content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.default-layout {
  min-height: 100vh;
}

.default-layout__content {
  padding: 32px;
}

.default-layout--fullscreen .default-layout__content {
  padding: 0;
}
</style>
