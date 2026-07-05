<script setup lang="ts">
import { computed } from 'vue'
import type { RendererTabConfig } from '../types'

const props = defineProps<{
  modelValue: string
  tab?: RendererTabConfig
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const tabs = computed(() => props.tab?.tabs || [])
</script>

<template>
  <div v-if="tabs.length" class="list-tabs-bar">
    <el-tabs :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)">
      <el-tab-pane v-for="item in tabs" :key="item.key" :label="item.label" :name="item.key" />
    </el-tabs>
  </div>
</template>

<style scoped>
.list-tabs-bar {
  margin-top: -8px;
}

:deep(.el-tabs__header) {
  margin-bottom: 0;
}
</style>
