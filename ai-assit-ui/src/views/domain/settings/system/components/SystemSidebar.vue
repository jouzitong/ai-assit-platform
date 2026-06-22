<script setup>
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

defineProps({
  items: {
    type: Array,
    required: true
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

defineEmits(['toggle'])

const route = useRoute()
</script>

<template>
  <aside class="system-sidebar" :class="{ collapsed }">
    <div class="sidebar-toolbar" :class="{ collapsed }">
      <div v-if="!collapsed" class="sidebar-title">
        <p>System Settings</p>
        <strong>配置导航</strong>
      </div>

      <button
        class="sidebar-toggle"
        type="button"
        :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
        :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="$emit('toggle')"
      >
        <ArrowRight v-if="collapsed" :size="16" />
        <ArrowLeft v-else :size="16" />
      </button>
    </div>

    <nav v-if="!collapsed" class="sidebar-body" aria-label="系统配置域">
      <RouterLink
        v-for="item in items"
        :key="item.key"
        :to="item.path"
        class="sidebar-item"
        :class="{ active: route.path === item.path }"
      >
        <span class="sidebar-item-icon">{{ item.icon }}</span>
        <span class="sidebar-item-copy">
          <strong>{{ item.label }}</strong>
        </span>
        <ArrowRight :size="12" class="sidebar-item-arrow" />
      </RouterLink>
    </nav>
  </aside>
</template>

<style scoped>
.system-sidebar {
  width: 100%;
  min-width: 0;
  flex: 1;
  height: 100%;
  min-height: 100%;
  padding: 10px 10px 12px 8px;
  transition: width 0.22s ease, flex-basis 0.22s ease, padding 0.22s ease;
  display: flex;
  flex-direction: column;
  gap: 8px;
  box-sizing: border-box;
  border: 0;
  border-radius: 0;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.98));
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
  backdrop-filter: none;
  overflow: hidden;
}

.system-sidebar.collapsed {
  padding: 10px 6px;
}

.sidebar-toolbar {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 34px;
  padding: 0 2px 6px;
  flex: none;
  border-bottom: 1px solid rgba(226, 232, 240, 0.82);
}

.sidebar-toolbar.collapsed {
  justify-content: center;
  padding-bottom: 0;
  border-bottom: 0;
}

.sidebar-title {
  min-width: 0;
  display: grid;
  gap: 0;
}

.sidebar-title p {
  margin: 0;
  color: #2563eb;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.sidebar-title strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.15;
  letter-spacing: -0.03em;
}

.sidebar-toggle {
  width: 30px;
  height: 30px;
  border: 1px solid rgba(219, 234, 254, 0.96);
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #2563eb;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.06);
  flex: none;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.sidebar-toggle:hover {
  border-color: rgba(96, 165, 250, 0.56);
  background: linear-gradient(180deg, #eff6ff, #dbeafe);
  box-shadow: 0 8px 16px rgba(37, 99, 235, 0.12);
}

.sidebar-body {
  min-height: 0;
  flex: 1;
  display: grid;
  gap: 5px;
  align-content: start;
  padding-top: 6px;
  overflow-y: auto;
  overflow-x: hidden;
}

.sidebar-item {
  text-decoration: none;
  color: inherit;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 12px;
  min-height: 44px;
  padding: 7px 9px;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.sidebar-item:hover {
  transform: translateY(-1px);
  border-color: rgba(147, 197, 253, 0.72);
  background: rgba(239, 246, 255, 0.92);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.06);
}

.sidebar-item.active {
  border-color: rgba(96, 165, 250, 0.54);
  background: linear-gradient(135deg, rgba(219, 234, 254, 0.98), rgba(191, 219, 254, 0.92));
  box-shadow:
    inset 0 0 0 1px rgba(96, 165, 250, 0.12),
    0 6px 14px rgba(37, 99, 235, 0.06);
}

.sidebar-item-icon {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(219, 234, 254, 0.92);
  color: #2563eb;
  font-size: 10px;
  font-weight: 700;
}

.sidebar-item-copy {
  min-width: 0;
  display: grid;
  gap: 0;
}

.sidebar-item-copy strong {
  color: #0f172a;
  font-size: 12px;
  line-height: 1.1;
}

.sidebar-item-arrow {
  color: #a0aec0;
}

.sidebar-item.active .sidebar-item-arrow {
  color: #2563eb;
}

@media (max-width: 1180px) {
  .system-sidebar {
    height: auto;
    width: 100%;
    flex-basis: auto;
    padding-bottom: 10px;
  }
}
</style>
