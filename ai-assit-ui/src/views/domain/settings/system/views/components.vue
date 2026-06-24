<script setup>
import ActionBar from '../../../../../components/commons/list/ActionBar.vue'
import DataListFooter from '../../../../../components/commons/list/DataListFooter.vue'
import DataTable from '../../../../../components/commons/list/DataTable.vue'
import FilterBar from '../../../../../components/commons/list/FilterBar.vue'
import FilterSummary from '../../../../../components/commons/list/FilterSummary.vue'
import HeaderBar from '../../../../../components/commons/list/HeaderBar.vue'
import ListCommonLayout from '../../../../../components/commons/list/ListCommonLayout.vue'
import Sidebar from '../../../../../components/commons/list/Sidebar.vue'
import StatsBar from '../../../../../components/commons/list/StatsBar.vue'
import { useRenderComponentsPage } from '../service/components'

const {
  loading,
  errorMessage,
  sidebarCollapsed,
  page,
  pageSize,
  total,
  selectedRowId,
  selectedCategory,
  rows,
  sorts,
  filterSchema,
  actionItems,
  sidebarItems,
  statsItems,
  tableColumns,
  listConfig,
  summaryItems,
  handleFilterAction,
  handleSummaryAction,
  handleSidebarSelect,
  handleRowClick,
  handleActionBar,
  handleTableAction,
  handlePageChange,
  handlePageSizeChange
} = useRenderComponentsPage()
</script>

<template>
  <div class="component-showcase">
    <div class="content-head">
      <div>
        <p class="eyebrow">Render 组件</p>
        <h2>组件资产管理</h2>
        <p class="section-desc">页面已对接 `RenderComponentManageController`，支持真实分页、分类筛选、发布、停用与删除。</p>
      </div>
    </div>

    <div class="preview-shell">
      <ListCommonLayout :sidebar-collapsed="sidebarCollapsed">
        <template #sidebar>
          <Sidebar title="组件分组" collapsible :collapsed="sidebarCollapsed" @toggle="sidebarCollapsed = $event">
            <template #main>
              <button
                v-for="item in sidebarItems"
                :key="item.key"
                type="button"
                class="sidebar-entry"
                :class="{ active: selectedCategory === item.key }"
                @click="handleSidebarSelect(item)"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.count }}</strong>
              </button>
            </template>
          </Sidebar>
        </template>

        <template #header>
          <HeaderBar title="组件资产" meta="render/api/v1/render/components" :show-title="true">
            <template #left>
              <FilterBar :schema="filterSchema" @action="handleFilterAction" />
            </template>
            <template #right>
              <ActionBar :actions="actionItems" @action="handleActionBar" />
            </template>
          </HeaderBar>
        </template>

        <template #filter-summary>
          <FilterSummary :items="summaryItems" @action="handleSummaryAction" />
        </template>

        <template #stats>
          <StatsBar :items="statsItems" />
        </template>

        <template #table>
          <div v-if="loading" class="table-placeholder">
            正在加载组件列表...
          </div>
          <div v-else-if="errorMessage" class="table-placeholder is-error">
            {{ errorMessage }}
          </div>
          <DataTable
            v-else
            :rows="rows"
            :columns="tableColumns"
            :list-config="listConfig"
            :sorts="sorts"
            row-key="id"
            :selected-row-value="selectedRowId"
            pagination
            :total-items="total"
            :page="page"
            :page-size="pageSize"
            @update:sorts="sorts = $event"
            @update:page="handlePageChange"
            @update:pageSize="handlePageSizeChange"
            @row-click="handleRowClick"
            @action-click="handleTableAction"
          />
        </template>

        <template #footer>
          <DataListFooter
            :total-items="total"
            :page="page"
            :page-size="pageSize"
            show-page-size
            @update:page="handlePageChange"
            @update:pageSize="handlePageSizeChange"
          />
        </template>
      </ListCommonLayout>
    </div>
  </div>
</template>

<style scoped>
.component-showcase {
  display: grid;
  gap: 18px;
  min-height: 0;
}

.preview-shell {
  height: min(760px, calc(100vh - 260px));
  min-height: 620px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.sidebar-entry {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--stroke);
  background: var(--control-bg);
  color: var(--text);
}

.sidebar-entry.active {
  border-color: rgba(14, 116, 144, 0.35);
  box-shadow: 0 10px 18px rgba(14, 116, 144, 0.12);
  background: linear-gradient(135deg, rgba(224, 242, 254, 0.95), rgba(236, 253, 245, 0.92));
}

.table-placeholder {
  display: grid;
  place-items: center;
  min-height: 320px;
  border: 1px dashed var(--stroke);
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.72);
  color: var(--text-dim);
}

.table-placeholder.is-error {
  color: #b91c1c;
}
</style>
