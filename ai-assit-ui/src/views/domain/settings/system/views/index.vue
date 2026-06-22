<script setup>
import SystemSidebar from '../components/SystemSidebar.vue'
import SystemContent from '../components/SystemContent.vue'
import { useSystemPage } from '../service'

const {
  sections,
  sidebarCollapsed,
  sidebarWidth,
  isSidebarResizing,
  toggleSidebar,
  startSidebarResize
} = useSystemPage()
</script>

<template>
  <main class="system-page">
    <section
      class="system-shell"
      :class="{ 'is-resizing': isSidebarResizing }"
      :style="{ '--sidebar-width': `${sidebarWidth}px` }"
    >
      <div class="system-sidebar-panel">
        <SystemSidebar
          :items="sections"
          :collapsed="sidebarCollapsed"
          :width="sidebarWidth"
          @toggle="toggleSidebar"
        />
      </div>

      <button
        class="system-divider"
        type="button"
        aria-label="拖动调整侧边栏宽度"
        title="拖动调整侧边栏宽度"
        @pointerdown="startSidebarResize"
      >
        <span class="divider-track" aria-hidden="true">
          <span class="divider-grip" />
        </span>
      </button>

      <div class="system-content-panel">
        <SystemContent />
      </div>
    </section>
  </main>
</template>

<style scoped>
.system-page {
  height: 100%;
  width: 100%;
  display: grid;
  padding: 8px 0 0;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.05), transparent 22%),
    linear-gradient(180deg, #f6f9fd 0%, #f2f6fb 100%);
}

.system-shell {
  min-height: 0;
  display: grid;
  grid-template-columns: var(--sidebar-width) 10px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  align-items: stretch;
  gap: 0;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  border: 1px solid rgba(226, 232, 240, 0.86);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.system-sidebar-panel,
.system-divider,
.system-content-panel {
  min-height: 0;
  min-width: 0;
}

.system-sidebar-panel {
  width: 100%;
  display: flex;
  overflow: hidden;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding: 4px 0 4px 4px;
  background: linear-gradient(180deg, #f8fbff 0%, #f5f9ff 100%);
  height: 100%;
  border-right: 0;
}

.system-divider {
  position: relative;
  border: 0;
  padding: 0;
  display: block;
  align-self: stretch;
  height: 100%;
  cursor: col-resize;
  touch-action: none;
  background: rgba(203, 213, 225, 0.88);
  border-left: 0;
  border-right: 0;
  transition: background-color 0.2s ease;
}

.system-divider:hover {
  background: rgba(148, 163, 184, 0.92);
}

.divider-track {
  position: absolute;
  inset: 0;
  display: block;
}

.divider-grip {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 1px;
  height: calc(100% - 28px);
  transform: translate(-50%, -50%);
  border-radius: 999px;
  background: rgba(71, 85, 105, 0.42);
}

.system-shell.is-resizing .system-sidebar,
.system-shell.is-resizing .system-content-panel {
  transition: none;
}

.system-content-panel {
  width: 100%;
  display: flex;
  overflow: hidden;
  padding: 0;
  background: linear-gradient(180deg, #f8fbff 0%, #f4f7fb 100%);
  border-left: 0;
}

@media (max-width: 1280px) {
  .system-page {
    padding: 0;
  }

  .system-shell {
    grid-template-columns: 1fr;
    border-radius: 0;
    border-left: 0;
    border-right: 0;
  }

  .system-sidebar-panel {
    padding: 4px 4px 0;
    border-bottom: 1px solid rgba(226, 232, 240, 0.95);
  }

  .system-divider {
    display: none;
  }
}
</style>
