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
  startSidebarResize,
  bindSidebarResizeFromEdge
} = useSystemPage()
</script>

<template>
  <main class="system-page">
    <section
      class="system-shell"
      :class="{ 'is-resizing': isSidebarResizing }"
      :style="{ '--sidebar-width': `${sidebarWidth}px` }"
    >
      <div
        class="system-sidebar-panel"
        @pointerdown="bindSidebarResizeFromEdge"
      >
        <SystemSidebar
          :items="sections"
          :collapsed="sidebarCollapsed"
          :width="sidebarWidth"
          @toggle="toggleSidebar"
        />
      </div>

      <button
        class="system-resize-handle"
        type="button"
        aria-label="拖动调整侧边栏宽度"
        title="拖动调整侧边栏宽度"
        @pointerdown="startSidebarResize"
      >
        <span class="handle-grip" aria-hidden="true">⋮⋮</span>
      </button>

      <div class="system-content-panel">
        <SystemContent />
      </div>
    </section>
  </main>
</template>

<style scoped>
.system-page {
  min-height: 0;
  height: 100%;
  display: grid;
  padding: 0;
  overflow: hidden;
  background:
    radial-gradient(circle at 8% 8%, rgba(37, 99, 235, 0.08), transparent 28%),
    radial-gradient(circle at 92% 0%, rgba(14, 165, 233, 0.06), transparent 24%),
    linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
}

.system-shell {
  min-height: 0;
  display: grid;
  grid-template-columns: var(--sidebar-width) 18px minmax(0, 1fr);
  gap: 0;
  width: 100%;
  height: 100%;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.05);
  overflow: hidden;
}

.system-sidebar-panel,
.system-resize-handle,
.system-content-panel {
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.system-sidebar-panel {
  width: 100%;
  border-right: 1px solid rgba(203, 213, 225, 1);
  background: linear-gradient(180deg, rgba(243, 244, 246, 0.98), rgba(229, 231, 235, 0.94));
}

.system-resize-handle {
  border: 0;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: col-resize;
  touch-action: none;
  background:
    linear-gradient(180deg, rgba(209, 213, 219, 0.92), rgba(226, 232, 240, 0.92));
  border-left: 1px solid rgba(203, 213, 225, 1);
  border-right: 1px solid rgba(203, 213, 225, 1);
  color: #64748b;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.system-resize-handle:hover {
  background:
    linear-gradient(180deg, rgba(203, 213, 225, 0.95), rgba(226, 232, 240, 0.96));
  color: #475569;
}

.handle-grip {
  display: inline-block;
  font-size: 14px;
  line-height: 1;
  letter-spacing: -0.18em;
  transform: rotate(90deg);
  user-select: none;
  pointer-events: none;
}

.system-shell.is-resizing .system-sidebar {
  transition: none;
}

.system-content-panel {
  width: 100%;
  background: rgba(255, 255, 255, 0.92);
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
    border-right: 0;
    border-bottom: 1px solid rgba(226, 232, 240, 0.95);
  }

  .system-resize-handle {
    display: none;
  }
}
</style>
