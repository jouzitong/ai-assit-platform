<script setup>
const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  collapsible: {
    type: Boolean,
    default: false
  },
  collapsed: {
    type: Boolean,
    default: false
  },
  toggleLabel: {
    type: String,
    default: '«'
  },
  toggleCollapsedLabel: {
    type: String,
    default: '»'
  }
})

const emit = defineEmits(['toggle'])
</script>

<template>
  <div class="sidebar" :class="{ 'is-collapsed': collapsed }">
    <div v-if="title || collapsible" class="sidebar__header">
      <div v-if="title" class="sidebar__title">
        {{ collapsed ? '' : title }}
      </div>
      <div v-if="collapsible" class="sidebar__toggle">
        <button type="button" @click="emit('toggle', !props.collapsed)">
          {{ collapsed ? toggleCollapsedLabel : toggleLabel }}
        </button>
      </div>
    </div>
    <div v-show="!collapsed" class="sidebar__content">
      <slot name="prepend" />
      <slot name="main" />
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  position: relative;
  overflow: visible;
  border-right: 1px solid var(--stroke);
  padding: 16px 16px 18px;
  height: 100%;
  background: var(--panel-bg);
  transition: width 0.2s ease, padding 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 10px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--stroke);
}

.sidebar__title {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-dim);
}

.sidebar__toggle button {
  height: 28px;
  padding: 0 10px;
  border-radius: 10px;
  border: 1px solid var(--stroke);
  background: var(--control-bg);
  color: var(--text);
}

.sidebar.is-collapsed {
  width: 0;
  min-width: 0;
  padding: 0;
  border-right-color: transparent;
  background: transparent;
}

.sidebar.is-collapsed .sidebar__header {
  position: absolute;
  top: 12px;
  left: 100%;
  margin: 0 0 0 6px;
  padding: 0;
  border: 0;
}
</style>
