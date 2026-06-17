<script setup>
const props = defineProps({
  title: {
    type: String,
    default: '任务中心'
  },
  meta: {
    type: String,
    default: ''
  },
  showTitle: {
    type: Boolean,
    default: false
  },
  tab: {
    type: Object,
    default: () => ({
      activeTab: '',
      list: []
    })
  }
})

const emit = defineEmits(['tab-change', 'update:tab'])

function hasTabs() {
  return Array.isArray(props.tab?.list) && props.tab.list.length > 0
}

function isActiveTab(tab) {
  return String(tab?.key ?? '') === String(props.tab?.activeTab ?? '')
}

function handleTabClick(tab) {
  if (!tab?.key || isActiveTab(tab)) {
    return
  }
  emit('update:tab', {
    ...props.tab,
    activeTab: tab.key
  })
  emit('tab-change', tab)
}
</script>

<template>
  <div class="header">
    <div class="header-top">
      <div class="header-main">
        <div v-if="showTitle" class="header-title">
          <div class="title">
            {{ title }}
          </div>
          <div v-if="meta" class="meta">
            {{ meta }}
          </div>
        </div>
        <div v-if="hasTabs()" class="header-tabs" role="tablist" aria-label="Page tabs">
          <button
            v-for="tab in props.tab.list"
            :key="tab.key"
            type="button"
            class="header-tab"
            :class="{ active: isActiveTab(tab) }"
            :disabled="tab.disabled === true"
            role="tab"
            :aria-selected="isActiveTab(tab)"
            @click="handleTabClick(tab)"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>
      <div class="header-right">
        <slot name="right" />
      </div>
    </div>
    <div v-if="$slots.left" class="header-bottom">
      <div class="header-left">
        <slot name="left" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.header {
  border-bottom: 1px solid var(--stroke);
  padding: 10px 20px 12px;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-main {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.header-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.header-tabs {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.header-tab {
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--stroke);
  border-radius: 999px;
  background: var(--control-bg);
  color: var(--text-dim);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  transition: border-color 0.18s ease, background-color 0.18s ease, color 0.18s ease;
}

.header-tab.active {
  border-color: rgba(37, 99, 235, 0.3);
  background: rgba(37, 99, 235, 0.08);
  color: var(--text);
}

.header-bottom {
  display: flex;
  min-width: 0;
}

.header-left {
  display: flex;
  flex: 1;
  min-width: 0;
}

.header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}
</style>
