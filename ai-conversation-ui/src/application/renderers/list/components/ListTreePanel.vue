<script setup lang="ts">
import { computed } from 'vue'
import type { RendererTreeConfig, RendererTreeNode } from '../types'

const props = defineProps<{
  config?: RendererTreeConfig
  data: RendererTreeNode[]
  selectedKey: string | number | null
}>()

const emit = defineEmits<{
  select: [node: RendererTreeNode]
}>()

const panelTitle = computed(() => props.config?.title || '分类')
const isGroupList = computed(() => props.config?.component === 'group-list')

const groupSections = computed(() =>
  props.data.map((item) => ({
    ...item,
    children: item.children || [],
  })),
)
</script>

<template>
  <el-card shadow="never" class="list-tree-panel">
    <template #header>
      <div class="list-tree-panel__header">{{ panelTitle }}</div>
    </template>

    <div v-if="isGroupList" class="list-tree-panel__groups">
      <section
        v-for="section in groupSections"
        :key="String(section.key)"
        class="list-tree-panel__group"
      >
        <h4 class="list-tree-panel__group-title">{{ section.label }}</h4>
        <button
          v-for="item in section.children"
          :key="String(item.key)"
          type="button"
          class="list-tree-panel__group-item"
          :class="{ 'list-tree-panel__group-item--active': selectedKey === item.key }"
          @click="emit('select', item)"
        >
          <span>{{ item.label }}</span>
          <span v-if="item.count != null" class="list-tree-panel__count">{{ item.count }}</span>
        </button>
      </section>
    </div>

    <el-tree
      v-else
      :data="data"
      node-key="key"
      :current-node-key="selectedKey ?? undefined"
      highlight-current
      default-expand-all
      :expand-on-click-node="false"
      @node-click="emit('select', $event)"
    />
  </el-card>
</template>

<style scoped>
.list-tree-panel {
  height: 100%;
  border-radius: var(--app-radius-panel);
  border: 1px solid var(--workbench-side-border);
  background: var(--workbench-side-bg);
}

.list-tree-panel__header {
  font-weight: 600;
  color: var(--workbench-title);
}

.list-tree-panel__groups {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-6);
}

.list-tree-panel__group {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-2);
}

.list-tree-panel__group-title {
  margin: 0 0 6px;
  font-size: var(--app-font-size-title-lg);
  font-weight: 700;
  color: var(--workbench-title);
}

.list-tree-panel__group-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-3);
  width: 100%;
  padding: var(--app-space-compact) var(--app-space-3);
  border: 0;
  border-radius: var(--app-radius-comfortable);
  background: transparent;
  color: var(--workbench-text-soft);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.list-tree-panel__group-item:hover,
.list-tree-panel__group-item--active {
  background: var(--workbench-side-item-active);
  color: var(--workbench-title);
  transform: translateX(2px);
}

.list-tree-panel__count {
  color: var(--workbench-text-muted);
}

:deep(.el-card__header) {
  border-bottom-color: var(--workbench-side-border);
}

:deep(.el-card__body) {
  padding-top: 10px;
}

:deep(.el-tree) {
  background: transparent;
  color: var(--workbench-text);
}

:deep(.el-tree-node__content:hover) {
  background-color: var(--workbench-side-item-hover);
}
</style>
