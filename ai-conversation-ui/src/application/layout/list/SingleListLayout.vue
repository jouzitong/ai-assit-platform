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
    <el-container direction="vertical" class="single-list-layout__shell">
      <el-header height="auto" class="single-list-layout__header">
        <slot name="header" />
        <slot name="tabs" />
        <slot name="filters" />
      </el-header>

      <el-container class="single-list-layout__body" :class="{ 'single-list-layout__body--tree': showTree }">
        <el-aside v-if="showTree" class="single-list-layout__tree">
          <slot name="tree" />
        </el-aside>

        <el-main class="single-list-layout__main">
          <el-card
            shadow="never"
            class="single-list-layout__content-card single-list-layout__list-table"
          >
            <slot name="summary" />

            <slot />

            <footer v-if="paginationEnabled" class="single-list-layout__list-table-footer">
              <slot name="pagination" />
            </footer>
          </el-card>
        </el-main>
      </el-container>
    </el-container>
  </section>
</template>

<style scoped>
.single-list-layout {
  display: flex;
  flex: 1 1 auto;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: var(--app-space-compact);
  background: var(--workbench-shell-bg);
  border-radius: var(--app-radius-shell);
  container: application-list-layout / inline-size;
}

.single-list-layout__shell {
  gap: var(--app-space-1);
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: auto;
}

.single-list-layout__header {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--app-space-3);
  padding: 0;
  background: transparent;
}

.single-list-layout--workbench {
  min-height: 100%;
  background: var(--workbench-shell-bg);
}

.single-list-layout__body {
  display: flex;
  flex: 1 0 320px;
  min-width: 0;
  min-height: 0;
}

.single-list-layout__body--tree {
  gap: var(--app-space-5);
}

.single-list-layout__tree,
.single-list-layout__main {
  min-width: 0;
  min-height: 0;
}

.single-list-layout__tree {
  flex: 0 0 280px;
  width: 280px;
  overflow: auto;
}

.single-list-layout__main {
  display: flex;
  flex: 1 1 auto;
  padding: 0;
  overflow: hidden;
}

.single-list-layout__content-card {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: var(--app-space-3);
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  border-radius: var(--app-radius-shell);
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
  gap: var(--app-space-3);
  min-height: 0;
  padding: var(--app-space-compact);
}

.single-list-layout__list-table-footer {
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
}

@container application-list-layout (max-width: 960px) {
  .single-list-layout__body--tree {
    flex-direction: column;
  }

  .single-list-layout__tree {
    flex-basis: auto;
    width: 100%;
  }
}
</style>
