<script setup>
import { computed } from 'vue'
import { resolveLowcodeComponent } from '../../registry/componentRegistry'

const props = defineProps({
  runtime: {
    type: Object,
    required: true
  }
})

const ListCommonLayout = resolveLowcodeComponent('ListCommonLayout')
const HeaderBar = resolveLowcodeComponent('HeaderBar')
const FilterBar = resolveLowcodeComponent('FilterBar')
const ActionBar = resolveLowcodeComponent('ActionBar')
const FilterSummary = resolveLowcodeComponent('FilterSummary')
const StatsBar = resolveLowcodeComponent('StatsBar')
const DataTable = resolveLowcodeComponent('DataTable')
const DataListFooter = resolveLowcodeComponent('DataListFooter')

const feedbackClass = computed(() => `feedback-${props.runtime.feedbackType || 'info'}`)
</script>

<template>
  <div class="lowcode-list-shell">
    <component :is="ListCommonLayout">
      <template #header>
        <component :is="HeaderBar" :title="runtime.title" :meta="runtime.metaText" :show-title="true">
          <template #left>
            <component :is="FilterBar" :schema="runtime.filterSchema" @action="runtime.dispatchAction" />
          </template>
          <template #right>
            <component :is="ActionBar" :actions="runtime.actionItems" @action="runtime.dispatchAction" />
          </template>
        </component>
      </template>

      <template #filter-summary>
        <component :is="FilterSummary" :items="runtime.summaryItems" @action="runtime.dispatchAction({ action: $event })" />
      </template>

      <template #stats>
        <component v-if="runtime.statsItems.length" :is="StatsBar" :items="runtime.statsItems" />
      </template>

      <template #table>
        <div v-if="runtime.feedbackMessage" class="runtime-feedback" :class="feedbackClass">
          {{ runtime.feedbackMessage }}
        </div>
        <div v-if="runtime.loading" class="runtime-placeholder">
          正在加载页面数据...
        </div>
        <div v-else-if="runtime.errorMessage" class="runtime-placeholder is-error">
          {{ runtime.errorMessage }}
        </div>
        <component
          :is="DataTable"
          v-else
          :rows="runtime.rows"
          :columns="runtime.tableColumns"
          :list-config="runtime.listConfig"
          :row-key="runtime.rowKey"
          :selected-row-value="runtime.state.selectedRowId"
          :layout-type="runtime.layoutType"
          pagination
          :total-items="runtime.total"
          :page="runtime.page"
          :page-size="runtime.pageSize"
          @row-click="runtime.handleRowClick"
          @action-click="runtime.handleTableAction"
          @update:page="runtime.handlePageChange"
          @update:pageSize="runtime.handlePageSizeChange"
        />
      </template>

      <template #footer>
        <component
          :is="DataListFooter"
          :total-items="runtime.total"
          :page="runtime.page"
          :page-size="runtime.pageSize"
          show-page-size
          @update:page="runtime.handlePageChange"
          @update:pageSize="runtime.handlePageSizeChange"
        />
      </template>
    </component>
  </div>
</template>

<style scoped>
.lowcode-list-shell {
  height: min(760px, calc(100vh - 280px));
  min-height: 620px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.runtime-feedback {
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid transparent;
  font-size: 13px;
}

.feedback-info {
  background: rgba(226, 232, 240, 0.56);
  border-color: rgba(148, 163, 184, 0.35);
  color: #334155;
}

.feedback-success {
  background: rgba(220, 252, 231, 0.72);
  border-color: rgba(34, 197, 94, 0.22);
  color: #166534;
}

.feedback-error {
  background: rgba(254, 226, 226, 0.72);
  border-color: rgba(239, 68, 68, 0.22);
  color: #991b1b;
}

.runtime-placeholder {
  display: grid;
  place-items: center;
  min-height: 320px;
  border: 1px dashed var(--stroke);
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.72);
  color: var(--text-dim);
}

.runtime-placeholder.is-error {
  color: #b91c1c;
}
</style>
