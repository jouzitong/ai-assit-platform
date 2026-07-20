<script setup lang="ts">
import { Printer, RefreshRight } from '@element-plus/icons-vue'
import type { RenderModeHostProps } from '../model/render-app'

defineProps<RenderModeHostProps>()

const emit = defineEmits<{
  refresh: []
}>()

function printReport() {
  window.print()
}
</script>

<template>
  <main class="report-mode-host">
    <header class="report-mode-host__header">
      <div>
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
      <div class="report-mode-host__actions">
        <el-button
          :icon="RefreshRight"
          :loading="loading"
          :disabled="!refreshable"
          @click="emit('refresh')"
        >
          刷新
        </el-button>
        <el-button type="primary" :icon="Printer" @click="printReport">
          打印
        </el-button>
      </div>
    </header>
    <section class="report-mode-host__content">
      <slot />
    </section>
  </main>
</template>

<style scoped>
.report-mode-host {
  width: 100%;
  min-height: 100dvh;
  padding: var(--app-space-5);
  background: var(--app-body-bg);
  color: var(--app-text);
}

.report-mode-host__header,
.report-mode-host__content {
  width: min(100%, 1600px);
  margin: 0 auto;
}

.report-mode-host__header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
  margin-bottom: var(--app-space-5);
  padding: var(--app-space-4);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-raised);
  box-shadow: var(--app-shadow-md);
}

.report-mode-host__header h1 {
  margin: 0;
  color: var(--app-title);
  font-size: var(--app-font-size-title-lg);
}

.report-mode-host__header p {
  margin: var(--app-space-2) 0 0;
  color: var(--app-text-soft);
  font-size: var(--app-font-size-body-lg);
}

.report-mode-host__actions {
  display: flex;
  flex: none;
  gap: var(--app-space-2);
}

.report-mode-host__content {
  min-width: 0;
  min-height: 0;
  padding: var(--app-space-5);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
}

@media (max-width: 768px) {
  .report-mode-host {
    padding: var(--app-space-3);
  }

  .report-mode-host__header {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media print {
  .report-mode-host {
    min-height: auto;
    padding: 0;
    background: var(--app-surface-solid);
  }

  .report-mode-host__header {
    position: static;
    margin-bottom: 12mm;
    padding: 0;
    border: 0;
    box-shadow: none;
  }

  .report-mode-host__actions {
    display: none;
  }

  .report-mode-host__content {
    width: 100%;
    padding: 0;
    border: 0;
    box-shadow: none;
  }
}
</style>
