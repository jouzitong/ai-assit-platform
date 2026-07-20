<script setup lang="ts">
import type { RenderModeHostProps } from '../model/render-app'

defineProps<RenderModeHostProps>()
</script>

<template>
  <main class="standard-mode-host">
    <header v-if="title || description" class="standard-mode-host__header">
      <h1 v-if="title">{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </header>
    <section class="standard-mode-host__content">
      <slot />
    </section>
  </main>
</template>

<style scoped>
.standard-mode-host {
  width: 100%;
  min-height: 100dvh;
  padding: var(--app-space-6);
  background: var(--app-body-bg);
  color: var(--app-text);
}

.standard-mode-host__header,
.standard-mode-host__content {
  width: min(100%, 1440px);
  margin: 0 auto;
}

.standard-mode-host__header {
  padding: 0 var(--app-space-1) var(--app-space-5);
}

.standard-mode-host__header h1 {
  margin: 0;
  color: var(--app-title);
  font-size: var(--app-font-size-title-lg);
  line-height: var(--app-line-height-tight);
}

.standard-mode-host__header p {
  margin: var(--app-space-2) 0 0;
  color: var(--app-text-soft);
  font-size: var(--app-font-size-body-lg);
  line-height: var(--app-line-height-body);
}

.standard-mode-host__content {
  min-width: 0;
  min-height: 0;
}

@media (max-width: 768px) {
  .standard-mode-host {
    padding: var(--app-space-4);
  }
}
</style>
