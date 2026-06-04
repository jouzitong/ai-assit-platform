<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SystemSidebar from '../components/SystemSidebar.vue'
import SystemContent from '../components/SystemContent.vue'
import { useSystemPage } from '../service'

const {
  sections,
  sidebarCollapsed,
  toggleSidebar
} = useSystemPage()

const route = useRoute()

const currentTitle = computed(() => route.matched.at(-1)?.meta?.title ?? '系统配置中心')
</script>

<template>
  <main class="system-page">
    <section class="system-shell">
      <SystemSidebar
        :items="sections"
        :collapsed="sidebarCollapsed"
        @toggle="toggleSidebar"
      />

      <SystemContent />
    </section>
  </main>
</template>

<style scoped>
.system-page {
  min-height: 0;
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
  padding: 16px 18px 20px;
  background:
    radial-gradient(circle at top left, rgba(37, 99, 235, 0.08), transparent 32%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(241, 245, 249, 0.94));
}

.system-page-header {
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.04);
  padding: 18px 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.system-page-copy {
  min-width: 0;
}

.system-kicker {
  margin: 0 0 10px;
  color: var(--app-accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.22em;
}

.system-page-header h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.1;
}

.system-page-desc {
  margin: 10px 0 0;
  color: var(--app-text-muted);
  max-width: 680px;
}

.system-page-tools {
  display: grid;
  gap: 12px;
  justify-items: end;
}

.system-page-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.system-page-tab {
  text-decoration: none;
  color: var(--app-text-soft);
  background: rgba(148, 163, 184, 0.12);
  border: 1px solid transparent;
  border-radius: 14px;
  padding: 10px 14px;
  font-weight: 600;
}

.system-page-tab.active {
  color: #fff;
  background: linear-gradient(135deg, #2563eb, #6d5efc);
  border-color: transparent;
}

.system-page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.system-current {
  color: var(--app-text-muted);
  font-size: 13px;
  padding-right: 4px;
}

.system-action {
  border: 0;
  border-radius: 14px;
  padding: 11px 16px;
  font: inherit;
  cursor: pointer;
}

.system-action.secondary {
  color: var(--app-text);
  background: rgba(148, 163, 184, 0.12);
}

.system-action.primary {
  color: #eff6ff;
  background: linear-gradient(135deg, #2563eb, #6d5efc);
}

.system-shell {
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}

@media (max-width: 1280px) {
  .system-page {
    padding: 12px;
  }

  .system-page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .system-page-tools,
  .system-page-actions,
  .system-page-tabs {
    justify-content: flex-start;
  }

  .system-shell {
    grid-template-columns: 1fr;
  }
}
</style>
