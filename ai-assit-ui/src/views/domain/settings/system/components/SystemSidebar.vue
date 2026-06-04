<script setup>
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'

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
    <button
      class="sidebar-toggle"
      type="button"
      :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
      :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
      @click="$emit('toggle')"
    >
      <ArrowRight v-if="collapsed" :size="18" />
      <ArrowLeft v-else :size="18" />
    </button>

    <template v-if="!collapsed">
      <div class="sidebar-head">
        <p class="sidebar-kicker">System Center</p>
        <h2>系统配置中心</h2>
        <p class="sidebar-desc">按业务域组织配置，不再按单张表作为主导航。</p>
      </div>

      <nav class="sidebar-nav">
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
            <small>{{ item.summary }}</small>
          </span>
        </RouterLink>
      </nav>
    </template>

    <template v-else>
      <div class="sidebar-collapsed-rail" />
    </template>
  </aside>
</template>

<style scoped>
.system-sidebar {
  width: 296px;
  flex: 0 0 296px;
  padding: 18px 16px;
  height: 100%;
  transition: width 0.22s ease, flex-basis 0.22s ease, padding 0.22s ease;
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.05);
  backdrop-filter: blur(12px);
}

.system-sidebar.collapsed {
  width: 70px;
  flex-basis: 70px;
  padding: 12px 10px;
}

.sidebar-toggle {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #0f172a;
  background: rgba(241, 245, 249, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.95);
}

.system-sidebar.collapsed .sidebar-toggle {
  margin: 0 auto;
}

.sidebar-head {
  margin-top: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.92);
}

.sidebar-kicker {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  font-weight: 700;
}

.sidebar-head h2 {
  margin: 0;
  font-size: 20px;
  letter-spacing: -0.01em;
}

.sidebar-desc {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.55;
}

.sidebar-nav {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.sidebar-item {
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 20px;
  padding: 14px 13px;
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 12px;
  align-items: center;
  background: rgba(248, 250, 252, 0.92);
  cursor: pointer;
  text-align: left;
  text-decoration: none;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.sidebar-item.active {
  border-color: rgba(37, 99, 235, 0.22);
  background: rgba(239, 246, 255, 0.96);
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.08);
}

.sidebar-item-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.14), rgba(59, 130, 246, 0.08));
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.sidebar-item-copy {
  display: grid;
  gap: 4px;
}

.sidebar-item-copy strong {
  font-size: 14px;
  color: #0f172a;
}

.sidebar-item-copy small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.sidebar-collapsed-rail {
  flex: 1;
  margin-top: 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(241, 245, 249, 0.9), rgba(226, 232, 240, 0.55));
}

.system-sidebar.collapsed .sidebar-head,
.system-sidebar.collapsed .sidebar-nav {
  display: none;
}

@media (max-width: 1180px) {
  .system-sidebar {
    height: auto;
    overflow: visible;
  }
}
</style>
