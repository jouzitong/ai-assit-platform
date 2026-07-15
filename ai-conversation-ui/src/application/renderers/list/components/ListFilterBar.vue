<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { AppFilterOperatorInput } from '../../../../components/basic'
import {
  AppDatePicker,
  AppInput,
  AppSelect,
  AppSelectTree,
} from '../../../../components/input'
import { getDefaultFilterValue } from '../schema'
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

const FILTER_COMPONENT_MAP = {
  'zg-input': AppFilterOperatorInput,
  'zg-selector': AppSelect,
  'zg-selector-tree': AppSelectTree,
  'zg-input-date': AppDatePicker,
  'zg-input-datetime': AppDatePicker,
} as const

const DEFAULT_COMPONENT_BY_TYPE = {
  text: AppFilterOperatorInput,
  select: AppSelect,
  date: AppDatePicker,
  daterange: AppDatePicker,
} as const

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

const handleFilterChange = (filter: RendererFilter, value: unknown) => {
  updateFilter(filter.key, value)
  if (shouldSubmitOnChange(filter)) {
    emit('submit')
  }
}

const resolveSelectOptions = (filter: RendererFilter) => {
  if (filter.list?.length) {
    return filter.list.map((item) => ({
      label: item.key,
      value: item.value,
      disabled: item.disabled,
    }))
  }

  if (filter.selector) {
    // TODO: support remote selector datasource after datasource protocol is finalized.
    return []
  }

  if (filter.enums) {
    // TODO: resolve enum options from enum store after enums contract is finalized.
    return []
  }

  return []
}

const resolveFilterComponent = (filter: RendererFilter) => {
  if (filter.component && filter.component in FILTER_COMPONENT_MAP) {
    return FILTER_COMPONENT_MAP[filter.component as keyof typeof FILTER_COMPONENT_MAP]
  }

  if (filter.type && filter.type in DEFAULT_COMPONENT_BY_TYPE) {
    return DEFAULT_COMPONENT_BY_TYPE[filter.type as keyof typeof DEFAULT_COMPONENT_BY_TYPE]
  }

  return AppInput
}

const getFilterComponentProps = (filter: RendererFilter) => {
  const componentProps = filter.componentProps || {}
  const componentOptions = filter.options || {}
  const placeholder = filter.placeholder || buildPlaceholder(filter)
  const styleConfig = componentOptions.styles || {}
  const commonProps = {
    modelValue: normalizeFilterValue(filter),
    placeholder,
    clearable: componentOptions.clearable ?? true,
    disabled: componentOptions.disabled ?? false,
    block: false,
    label: componentOptions.labelPosition === 'left' ? undefined : filter.label,
    labelPosition: componentOptions.labelPosition ?? 'inner',
    class: componentOptions.className,
    style: styleConfig,
    teleported: componentOptions.teleported,
    ...componentProps,
  }

  if (filter.component === 'zg-selector' || filter.type === 'select') {
    return {
      ...commonProps,
      options: resolveSelectOptions(filter),
      teleported: componentProps.teleported ?? componentOptions.teleported ?? true,
      filterable: componentProps.filterable ?? componentOptions.filterable ?? true,
      multiple: componentProps.multiple ?? componentOptions.multiple ?? false,
      collapseTags: componentProps.collapseTags ?? componentOptions.collapseTags ?? true,
    }
  }

  if (filter.component === 'zg-selector-tree') {
    return {
      ...commonProps,
      data: filter.data || [],
      teleported: componentProps.teleported ?? componentOptions.teleported ?? true,
      filterable: componentProps.filterable ?? componentOptions.filterable ?? true,
      multiple: componentProps.multiple ?? componentOptions.multiple ?? false,
      checkStrictly: componentProps.checkStrictly ?? componentOptions.checkStrictly ?? true,
      renderAfterExpand: componentProps.renderAfterExpand ?? componentOptions.renderAfterExpand ?? false,
    }
  }

  if (filter.component === 'zg-input-date') {
    return {
      ...commonProps,
      type: 'date',
    }
  }

  if (filter.component === 'zg-input-datetime') {
    return {
      ...commonProps,
      type: 'datetime',
    }
  }

  if (filter.type === 'date') {
    return {
      ...commonProps,
      type: 'date',
    }
  }

  if (filter.type === 'daterange') {
    return {
      ...commonProps,
      type: 'daterange',
      'range-separator': '至',
      'start-placeholder': '开始日期',
      'end-placeholder': '结束日期',
    }
  }

  if (!filter.component || filter.component === 'zg-input' || filter.type === 'text') {
    return {
      ...commonProps,
      operators: componentProps.operators ?? componentOptions.operators,
      defaultOperator: componentProps.defaultOperator ?? componentOptions.defaultOperator ?? filter.query?.op ?? 'like',
    }
  }

  return commonProps
}

const normalizeFilterValue = (filter: RendererFilter) => {
  const value = localFilters[filter.key]
  if (filter.options?.multiple) {
    return Array.isArray(value) ? value : []
  }
  return value
}

const buildPlaceholder = (filter: RendererFilter) => {
  if (
    filter.component === 'zg-selector' ||
    filter.component === 'zg-selector-tree' ||
    filter.type === 'select'
  ) {
    return `请选择${filter.label}`
  }
  return `请输入${filter.label}`
}

const isInputLikeFilter = (filter: RendererFilter) =>
  !filter.component || filter.component === 'zg-input'

const isSelectLikeFilter = (filter: RendererFilter) =>
  filter.component === 'zg-selector'
  || filter.component === 'zg-selector-tree'
  || filter.type === 'select'
  || filter.type === 'date'
  || filter.type === 'daterange'

const shouldSubmitOnChange = (filter: RendererFilter) =>
  filter.query?.submitOnChange
  ?? filter.options?.submitOnChange
  ?? isSelectLikeFilter(filter)

const shouldSubmitOnEnter = (filter: RendererFilter) =>
  filter.query?.submitOnEnter
  ?? filter.options?.submitOnEnter
  ?? isInputLikeFilter(filter)

const handleFilterEnter = (filter: RendererFilter) => {
  if (shouldSubmitOnEnter(filter)) {
    emit('submit')
  }
}

const filterEntries = computed(() =>
  props.filters.map((filter) => ({
    ...filter,
    renderComponent: resolveFilterComponent(filter),
    renderProps: getFilterComponentProps(filter),
  })).filter((filter) => !filter.options?.hidden),
)

const handleReset = () => {
  const nextState = props.filters.reduce<Record<string, unknown>>((acc, filter) => {
    acc[filter.key] = getDefaultFilterValue(filter)
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
      <el-form-item
        v-for="filter in filterEntries"
        :key="filter.key"
        :label="filter.options?.labelPosition === 'left' ? filter.label : undefined"
      >
        <component
          :is="filter.renderComponent"
          v-bind="filter.renderProps"
          class="list-filter-bar__component"
          @update:model-value="updateFilter(filter.key, $event)"
          @change="handleFilterChange(filter, $event)"
          @confirm="handleFilterEnter(filter)"
          @keyup.enter="handleFilterEnter(filter)"
        />
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
  border-radius: var(--app-radius-2xl);
}

.list-filter-bar__actions {
  margin-left: auto;
}

.list-filter-bar__component {
  min-width: min(180px, 100%);
}

@container application-list-layout (max-width: 768px) {
  :deep(.el-form) {
    display: flex;
    flex-direction: column;
  }

  .list-filter-bar__actions {
    margin-left: 0;
  }
}
</style>
