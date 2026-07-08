<script setup lang="ts">
import { computed } from 'vue'

defineOptions({
  inheritAttrs: false,
})

const props = withDefaults(
  defineProps<{
    currentPage?: number
    pageSize?: number
    total?: number
    pageSizes?: number[]
    layout?: string
    pagerCount?: number
    background?: boolean
    disabled?: boolean
    small?: boolean
    hideOnSinglePage?: boolean
    align?: 'left' | 'center' | 'right'
  }>(),
  {
    currentPage: 1,
    pageSize: 10,
    total: 0,
    pageSizes: () => [5, 10, 20, 50, 100, 200, 500],
    layout: 'prev, pager, next',
    pagerCount: 5,
    background: true,
    disabled: false,
    small: false,
    hideOnSinglePage: false,
    align: 'right',
  },
)

const emit = defineEmits<{
  'update:currentPage': [value: number]
  'update:pageSize': [value: number]
  currentChange: [value: number]
  sizeChange: [value: number]
  change: [currentPage: number, pageSize: number]
}>()

const normalizedLayout = computed(() => {
  const segments = props.layout
    .split(',')
    .map(item => item.trim())
    .filter(item => item && item !== 'total' && item !== 'sizes')

  return segments.length ? segments.join(', ') : 'prev, pager, next'
})

const shouldHidePagination = computed(() => props.hideOnSinglePage && props.total <= props.pageSize)

const handleCurrentPageChange = (value: number) => {
  emit('update:currentPage', value)
  emit('currentChange', value)
  emit('change', value, props.pageSize)
}

const handlePageSizeChange = (value: number) => {
  emit('update:pageSize', value)
  emit('sizeChange', value)
  emit('change', props.currentPage, value)
}
</script>

<template>
  <div v-if="!shouldHidePagination" class="app-pagination">
    <div class="app-pagination__total">
      共计 {{ props.total }}
    </div>

    <div class="app-pagination__controls" :class="`app-pagination__controls--${props.align}`">
      <el-select
        class="app-pagination__size-select"
        :model-value="props.pageSize"
        :disabled="props.disabled"
        popper-class="app-pagination__size-popper"
        @update:model-value="handlePageSizeChange"
      >
        <el-option
          v-for="size in props.pageSizes"
          :key="size"
          :label="`${size}条/页`"
          :value="size"
        />
      </el-select>

      <el-pagination
        :current-page="props.currentPage"
        :page-size="props.pageSize"
        :layout="normalizedLayout"
        :pager-count="props.pagerCount"
        :background="props.background"
        :disabled="props.disabled"
        :small="props.small"
        :total="props.total"
        v-bind="$attrs"
        @update:current-page="emit('update:currentPage', $event)"
        @current-change="handleCurrentPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.app-pagination {
  --app-pagination-surface: var(--system-surface-muted);
  --app-pagination-surface-hover: var(--system-surface-strong);
  --app-pagination-surface-active: var(--system-accent-bg-strong);
  --app-pagination-border: var(--system-border);
  --app-pagination-border-strong: var(--system-accent-border);
  --app-pagination-text: var(--system-text);
  --app-pagination-text-muted: var(--system-text-soft);
  --app-pagination-text-active: var(--system-title);
  --app-pagination-shadow: var(--app-shadow-sm);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

.app-pagination__total {
  flex: 0 0 auto;
  color: var(--app-pagination-text-muted);
  font-size: 13px;
}

.app-pagination__controls {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.app-pagination__controls--left {
  justify-content: flex-start;
}

.app-pagination__controls--center {
  justify-content: center;
}

.app-pagination__controls--right {
  justify-content: flex-end;
}

.app-pagination__size-select {
  width: 108px;
}

.app-pagination :deep(.el-select__wrapper) {
  background: var(--app-pagination-surface);
  border: 1px solid var(--app-pagination-border);
  border-radius: 12px;
  box-shadow: var(--app-pagination-shadow);
  min-height: 38px;
}

.app-pagination :deep(.el-select__wrapper:hover) {
  background: var(--app-pagination-surface-hover);
}

.app-pagination :deep(.el-select__wrapper.is-focused) {
  border-color: var(--app-pagination-border-strong);
  box-shadow: 0 0 0 1px var(--app-pagination-border-strong);
}

.app-pagination :deep(.el-select__selected-item),
.app-pagination :deep(.el-select__placeholder),
.app-pagination :deep(.el-select__caret),
.app-pagination :deep(.el-input__inner) {
  color: var(--app-pagination-text);
}

.app-pagination :deep(.btn-prev),
.app-pagination :deep(.btn-next),
.app-pagination :deep(.el-pager li) {
  background: var(--app-pagination-surface);
  border: 1px solid var(--app-pagination-border);
  border-radius: 12px;
  box-shadow: var(--app-pagination-shadow);
  color: var(--app-pagination-text);
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.app-pagination :deep(.btn-prev:hover),
.app-pagination :deep(.btn-next:hover),
.app-pagination :deep(.el-pager li:hover) {
  background: var(--app-pagination-surface-hover);
  border-color: var(--app-pagination-border-strong);
  color: var(--app-pagination-text-active);
}

.app-pagination :deep(.el-pager li.is-active) {
  background: var(--app-pagination-surface-active);
  border-color: var(--app-pagination-border-strong);
  color: var(--app-pagination-text-active);
}

.app-pagination :deep(.btn-prev:disabled),
.app-pagination :deep(.btn-next:disabled) {
  opacity: 0.48;
  cursor: not-allowed;
}

:global(.app-pagination__size-popper) {
  border-radius: 14px;
  border: 1px solid var(--system-border);
  background: var(--system-surface-solid);
  box-shadow: var(--system-shadow);
}

:global(.app-pagination__size-popper .el-select-dropdown__item) {
  color: var(--system-text);
}

:global(.app-pagination__size-popper .el-select-dropdown__item.hover),
:global(.app-pagination__size-popper .el-select-dropdown__item:hover) {
  background: var(--system-accent-bg);
}

:global(.app-pagination__size-popper .el-select-dropdown__item.is-selected) {
  color: var(--system-accent-text);
  font-weight: 600;
}
</style>
