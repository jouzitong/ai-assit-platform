<script setup lang="ts">
import type { RenderModeHostProps } from '../model/render-app'

defineProps<RenderModeHostProps>()
</script>

<template>
  <main class="embedded-mode-host" :aria-label="title">
    <slot />
  </main>
</template>

<style scoped>
.embedded-mode-host {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 100dvh;
  overflow: auto;
  background: var(--app-surface-solid);
  color: var(--app-text);
  container: embedded-render-app / size;
}
</style>
