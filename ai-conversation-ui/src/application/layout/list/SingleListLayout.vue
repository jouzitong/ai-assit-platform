<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: string
    showTree?: boolean
    paginationEnabled?: boolean
  }>(),
  {
    variant: 'default',
    showTree: false,
    paginationEnabled: false,
  },
)
</script>

<template>
  <section class="single-list-layout" :class="`single-list-layout--${variant}`">
    <slot name="header" />

    <slot name="tabs" />

    <div class="single-list-layout__body" :class="{ 'single-list-layout__body--tree': showTree }">
      <aside v-if="showTree" class="single-list-layout__tree">
        <slot name="tree" />
      </aside>

      <div class="single-list-layout__content">
        <el-card shadow="never" class="single-list-layout__content-card">
          <slot name="filters" />

          <slot name="summary" />

          <slot />

          <div v-if="paginationEnabled" class="single-list-layout__pagination">
            <slot name="pagination" />
          </div>
        </el-card>
      </div>
    </div>
  </section>
</template>

<style scoped>
.single-list-layout {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-5);
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: var(--app-space-compact);
  background: var(--workbench-shell-bg);
  border-radius: var(--app-radius-shell);
  container: application-list-layout / inline-size;
}

.single-list-layout--workbench {
  min-height: 100%;
  background: var(--workbench-shell-bg);
}

.single-list-layout__body {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.single-list-layout__body--tree {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: var(--app-space-5);
}

.single-list-layout__tree,
.single-list-layout__content {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.single-list-layout__content-card {
  width: 100%;
  height: 100%;
  border-radius: var(--app-radius-shell);
  display: flex;
  flex-direction: column;
  gap: var(--app-space-5);
}

.single-list-layout--workbench .single-list-layout__content-card {
  border: 1px solid var(--workbench-panel-border);
  background: var(--workbench-shell-bg);
  box-shadow: var(--workbench-panel-shadow);
}

.single-list-layout--workbench :deep(.list-header-bar__title) {
  color: var(--workbench-title);
}

.single-list-layout--workbench :deep(.list-tabs-bar .el-tabs__item) {
  color: var(--workbench-tab-text);
}

.single-list-layout--workbench :deep(.list-tabs-bar .el-tabs__item.is-active) {
  color: var(--workbench-tab-active);
}

.single-list-layout--workbench :deep(.list-filter-bar) {
  border: 0;
  background: var(--workbench-filter-bg);
}

.single-list-layout--workbench :deep(.list-data-view__table) {
  --el-table-bg-color: var(--workbench-table-bg);
  --el-table-tr-bg-color: var(--workbench-table-row-bg);
  --el-table-header-bg-color: var(--workbench-table-header-bg);
  --el-table-border-color: var(--workbench-table-border);
  --el-table-text-color: var(--workbench-table-text);
  --el-table-header-text-color: var(--workbench-table-header-text);
}

.single-list-layout--workbench :deep(.el-pagination) {
  --el-text-color-regular: var(--workbench-pagination-text);
}

.single-list-layout :deep(.el-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--app-space-5);
  min-height: 0;
  padding: var(--app-space-compact);
}

.single-list-layout__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@container application-list-layout (max-width: 960px) {
  .single-list-layout__body--tree {
    grid-template-columns: 1fr;
  }
}
</style>
