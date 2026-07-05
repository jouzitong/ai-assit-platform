<script setup lang="ts">
import type { RendererTreeNode } from '../types'

defineProps<{
  data: RendererTreeNode[]
  selectedKey: string | number | null
}>()

const emit = defineEmits<{
  select: [node: RendererTreeNode]
}>()
</script>

<template>
  <el-card shadow="never" class="list-tree-panel">
    <template #header>
      <div class="list-tree-panel__header">分类</div>
    </template>
    <el-tree
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
  border-radius: 24px;
}

.list-tree-panel__header {
  font-weight: 600;
  color: #1d2d44;
}
</style>
