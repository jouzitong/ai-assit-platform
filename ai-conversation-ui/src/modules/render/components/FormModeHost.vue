<script setup lang="ts">
import type { RenderModeHostProps } from '../model/render-app'

defineProps<RenderModeHostProps>()
</script>

<template>
  <el-container class="form-mode-host" :data-form-mode="formMode || 'view'">
    <el-main class="form-mode-host__main">
      <section class="form-mode-host__content">
        <slot />
      </section>
    </el-main>
  </el-container>
</template>

<style scoped>
.form-mode-host {
  width: 100%;
  height: 100dvh;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--app-body-bg);
  color: var(--app-text);
}

.form-mode-host__main {
  display: flex;
  min-width: 0;
  min-height: 0;
  padding: var(--app-space-6);
  overflow: auto;
}

.form-mode-host__content {
  display: flex;
  width: min(100%, var(--app-dialog-width-xl));
  min-width: 0;
  min-height: 100%;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .form-mode-host__main {
    padding: var(--app-space-3);
  }
}
</style>
