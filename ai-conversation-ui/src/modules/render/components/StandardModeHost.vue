<script setup lang="ts">
import type { RenderModeHostProps } from '../model/render-app'

defineProps<RenderModeHostProps>()
</script>

<template>
  <el-container class="standard-mode-host">
    <el-aside v-if="$slots.aside" class="standard-mode-host__aside">
      <slot name="aside" />
    </el-aside>

    <el-container direction="vertical" class="standard-mode-host__frame">
      <el-header
        v-if="title || description || $slots.header"
        height="auto"
        class="standard-mode-host__header"
      >
        <slot name="header">
          <h1 v-if="title">{{ title }}</h1>
          <p v-if="description">{{ description }}</p>
        </slot>
      </el-header>

      <el-main class="standard-mode-host__main">
        <section class="standard-mode-host__content">
          <slot />
        </section>
      </el-main>

      <el-footer v-if="$slots.footer" height="auto" class="standard-mode-host__footer">
        <slot name="footer" />
      </el-footer>
    </el-container>
  </el-container>
</template>

<style scoped>
.standard-mode-host {
  width: 100%;
  height: 100dvh;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--app-body-bg);
  color: var(--app-text);
}

.standard-mode-host__aside,
.standard-mode-host__frame,
.standard-mode-host__main,
.standard-mode-host__content {
  min-width: 0;
  min-height: 0;
}

.standard-mode-host__header {
  flex: 0 0 auto;
  padding: var(--app-space-4) var(--app-space-4) 0;
  background: transparent;
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

.standard-mode-host__main {
  display: flex;
  flex: 1 1 auto;
  padding: var(--app-space-4);
  overflow: auto;
}

.standard-mode-host__content {
  display: flex;
  flex: 1 1 auto;
  width: 100%;
}

.standard-mode-host__footer {
  flex: 0 0 auto;
  padding: 0 var(--app-space-4) var(--app-space-4);
  background: transparent;
}

@media (max-width: 768px) {
  .standard-mode-host__header {
    padding: var(--app-space-3) var(--app-space-3) 0;
  }

  .standard-mode-host__main {
    padding: var(--app-space-3);
  }

  .standard-mode-host__footer {
    padding: 0 var(--app-space-3) var(--app-space-3);
  }
}
</style>
