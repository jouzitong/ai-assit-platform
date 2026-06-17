<script setup>
defineProps({
  sidebarCollapsed: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <div class="list-common">
    <div class="container" :class="{ 'no-sidebar': !$slots.sidebar }">
      <aside v-if="$slots.sidebar" class="sidebar-area" :class="{ 'is-collapsed': sidebarCollapsed }">
        <slot name="sidebar"/>
      </aside>
      <main class="main">
        <slot name="header"/>
        <slot name="advanced-filters"/>
        <slot name="filter-summary"/>
        <div class="content">
          <slot name="stats"/>
          <slot name="table"/>
        </div>
        <slot name="footer"/>
      </main>
    </div>
  </div>
</template>

<style scoped>
.list-common {
  min-height: 100%;
  height: 100%;
  color: var(--text);
  font-family: var(--font-family-base);
  background: radial-gradient(1200px 600px at 10% -10%, var(--surface-glow-1), transparent 60%),
  radial-gradient(900px 600px at 85% 10%, var(--surface-glow-2), transparent 55%),
  var(--bg);
  overflow: hidden;
  width: 100%;
  border-radius: 24px;
}

.container {
  display: flex;
  height: 100%;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.03), transparent 40%);
}

.sidebar-area {
  width: 18%;
  min-width: 220px;
  flex: 0 0 18%;
}

.sidebar-area.is-collapsed {
  width: 0;
  min-width: 0;
  flex: 0 0 12px;
}

.main {
  flex: 1;
  width: auto;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.container.no-sidebar .main {
  width: 100%;
}

.content {
  flex: 1;
  padding: 3px 3px;
  border-bottom: 1px solid var(--stroke);
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: hidden;
}

@media (max-width: 1000px) {
  .container {
    flex-direction: column;
  }

  .sidebar-area {
    width: 100%;
    min-width: 0;
    flex: 0 0 auto;
  }
}
</style>
