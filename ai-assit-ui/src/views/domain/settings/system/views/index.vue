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
  min-height: 0;
  height: 100%;
  width: 100%;
  display: grid;
  padding: 0;
  overflow: hidden;
  background: #fff;
}

.system-shell {
  min-height: 0;
  display: grid;
  grid-template-columns: var(--sidebar-width) 12px minmax(0, 1fr);
  gap: 0;
  width: 100%;
  height: 100%;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 0;
  background: #fff;
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
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  background: #fff;
  height: 100%;
  border-right: 1px solid rgba(226, 232, 240, 0.95);
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
  background: #fff;
  border-left: 1px solid rgba(226, 232, 240, 0.95);
  border-right: 1px solid rgba(226, 232, 240, 0.95);
  transition: background-color 0.2s ease;
}

.system-divider:hover {
  background: rgba(248, 250, 252, 0.95);
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
  width: 2px;
  height: 28px;
  transform: translate(-50%, -50%);
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.9);
}

.system-shell.is-resizing .system-sidebar,
.system-shell.is-resizing .system-content-panel {
  transition: none;
}

.system-content-panel {
  width: 100%;
  display: flex;
  overflow: hidden;
  background: #fff;
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
    border-bottom: 1px solid rgba(226, 232, 240, 0.95);
  }

  .system-divider {
    display: none;
  }
}
</style>
