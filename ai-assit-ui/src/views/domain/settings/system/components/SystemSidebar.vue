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
      <div class="sidebar-shell">
        <nav class="sidebar-body" aria-label="系统配置域">
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
            <ArrowRight :size="14" class="sidebar-item-arrow" />
          </RouterLink>
        </nav>
      </div>
    </template>

    <template v-else>
      <div class="sidebar-collapsed-rail" />
    </template>
  </aside>
</template>

<style scoped>
.system-sidebar {
  width: 100%;
  min-width: 0;
  flex: 1;
  height: 100%;
  min-height: 100%;
  padding: 16px 14px;
  transition: width 0.22s ease, flex-basis 0.22s ease, padding 0.22s ease;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-sizing: border-box;
  border: 0;
  border-radius: 0;
  background: #fff;
  box-shadow: none;
  backdrop-filter: none;
  overflow: visible;
}

.system-sidebar.collapsed {
  padding: 12px 10px;
}

.sidebar-toggle {
  width: 34px;
  height: 34px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #0f172a;
  background: #fff;
  box-shadow: none;
  flex: none;
  align-self: flex-end;
}

.system-sidebar.collapsed .sidebar-toggle {
  margin: 0 auto;
}

.sidebar-shell {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.sidebar-body {
  min-height: 0;
  display: grid;
  gap: 8px;
}

.sidebar-item {
  text-decoration: none;
  color: inherit;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 18px;
  padding: 10px 12px;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.sidebar-item:hover,
.sidebar-item.active {
  transform: translateY(-1px);
  border-color: rgba(37, 99, 235, 0.25);
  background: #fff;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.05);
}

.sidebar-item-icon {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.95);
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.sidebar-item-copy,
.sidebar-account-copy {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.sidebar-item-copy strong,
.sidebar-account-copy strong {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.2;
}

.sidebar-item-copy small,
.sidebar-account-copy small {
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.sidebar-item-arrow {
  color: #94a3b8;
}

.sidebar-collapsed-rail {
  flex: 1;
  min-height: 120px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.95);
}

.system-sidebar.collapsed .sidebar-shell {
  display: none;
}

@media (max-width: 1180px) {
  .system-sidebar {
    height: auto;
    width: 100%;
    flex-basis: auto;
  }
}
</style>
