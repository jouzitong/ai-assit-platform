<script setup>
import SystemSidebar from '../components/SystemSidebar.vue'
import SystemContent from '../components/SystemContent.vue'
import { useSystemPage } from '../service'

const {
  sections,
  sidebarCollapsed,
  toggleSidebar
} = useSystemPage()
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
  padding: 18px 20px 22px;
  background:
    radial-gradient(circle at 8% 8%, rgba(37, 99, 235, 0.08), transparent 28%),
    radial-gradient(circle at 92% 0%, rgba(14, 165, 233, 0.06), transparent 24%),
    linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
}

.system-shell {
  min-height: 0;
  display: grid;
  grid-template-columns: 296px minmax(0, 1fr);
  gap: 18px;
}

@media (max-width: 1280px) {
  .system-page {
    padding: 12px;
  }

  .system-shell {
    grid-template-columns: 1fr;
  }
}
</style>
