<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { RendererFilter } from '../types'

const props = defineProps<{
  filters: RendererFilter[]
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
  submit: []
  reset: []
}>()

const localFilters = reactive<Record<string, unknown>>({})

watch(
  () => props.modelValue,
  (value) => {
    Object.keys(localFilters).forEach((key) => {
      delete localFilters[key]
    })
    Object.assign(localFilters, value)
  },
  { immediate: true, deep: true },
)

const updateFilter = (key: string, value: unknown) => {
  localFilters[key] = value
  emit('update:modelValue', { ...localFilters })
}

const handleReset = () => {
  const nextState = props.filters.reduce<Record<string, unknown>>((acc, filter) => {
    acc[filter.key] = ''
    return acc
  }, {})

  Object.keys(localFilters).forEach((key) => {
    delete localFilters[key]
  })
  Object.assign(localFilters, nextState)
  emit('update:modelValue', nextState)
  emit('reset')
}
</script>

<template>
  <el-card v-if="filters.length" shadow="never" class="list-filter-bar">
    <el-form inline>
      <el-form-item v-for="filter in filters" :key="filter.key" :label="filter.label">
        <el-input
          v-if="!filter.type || filter.type === 'text'"
          :model-value="String(localFilters[filter.key] ?? '')"
          :placeholder="filter.placeholder || `请输入${filter.label}`"
          clearable
          @update:model-value="updateFilter(filter.key, $event)"
          @keyup.enter="emit('submit')"
        />
        <el-select
          v-else-if="filter.type === 'select'"
          :model-value="localFilters[filter.key]"
          :placeholder="filter.placeholder || `请选择${filter.label}`"
          clearable
          style="width: 180px"
          @update:model-value="updateFilter(filter.key, $event)"
        >
          <el-option
            v-for="option in filter.options || []"
            :key="String(option.value)"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item class="list-filter-bar__actions">
        <el-button type="primary" @click="emit('submit')">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.list-filter-bar {
  border-radius: 20px;
}

.list-filter-bar__actions {
  margin-left: auto;
}

@media (max-width: 768px) {
  :deep(.el-form) {
    display: flex;
    flex-direction: column;
  }

  .list-filter-bar__actions {
    margin-left: 0;
  }
}
</style>
