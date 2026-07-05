<script setup lang="ts">
import { Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'

const componentKeyword = ref('')
const activeCategory = ref('all')
const pageSize = ref(20)
const currentPage = ref(1)

const componentCategories = [
  { key: 'all', label: '全部组件', count: 24 },
  { key: 'basic', label: '基础组件', count: 8 },
  { key: 'business', label: '业务组件', count: 9 },
  { key: 'render', label: '渲染组件', count: 5 },
  { key: 'deprecated', label: '待下线', count: 2 },
]

const componentRecords = [
  { id: 'cmp-001', name: 'FilterPanel', type: '基础组件', owner: 'Render Team', status: '已发布', updatedAt: '10 分钟前' },
  { id: 'cmp-002', name: 'MetricCard', type: '业务组件', owner: 'BI Team', status: '测试中', updatedAt: '35 分钟前' },
  { id: 'cmp-003', name: 'InsightTable', type: '渲染组件', owner: 'AI Team', status: '已发布', updatedAt: '1 小时前' },
  { id: 'cmp-004', name: 'ActionToolbar', type: '基础组件', owner: 'Frontend', status: '草稿', updatedAt: '2 小时前' },
]

const filteredComponentRecords = computed(() => {
  const keyword = componentKeyword.value.trim().toLowerCase()
  return componentRecords.filter((record) => {
    const matchCategory = activeCategory.value === 'all'
      || (activeCategory.value === 'basic' && record.type === '基础组件')
      || (activeCategory.value === 'business' && record.type === '业务组件')
      || (activeCategory.value === 'render' && record.type === '渲染组件')
      || (activeCategory.value === 'deprecated' && record.status === '草稿')

    const matchKeyword = !keyword
      || record.name.toLowerCase().includes(keyword)
      || record.owner.toLowerCase().includes(keyword)

    return matchCategory && matchKeyword
  })
})

const pageSizeOptions = [10, 20, 50, 100, 200, 500]
</script>

<template>
  <section class="system-settings-component-page">
    <el-container class="component-manage-layout">
      <el-aside width="220px" class="component-manage-layout__aside">
        <div class="component-manage-layout__aside-title">组件分类</div>
        <button
          v-for="category in componentCategories"
          :key="category.key"
          :class="['component-manage-category', { 'is-active': activeCategory === category.key }]"
          type="button"
          @click="activeCategory = category.key"
        >
          <span>{{ category.label }}</span>
          <strong>{{ category.count }}</strong>
        </button>
      </el-aside>

      <el-container class="component-manage-layout__body">
        <el-header class="component-manage-layout__header">
          <div class="component-manage-layout__title">
            <h3>组件管理</h3>
            <p>集中维护组件资产、状态和归属。</p>
          </div>
          <div class="component-manage-layout__tools">
            <el-input v-model="componentKeyword" placeholder="搜索组件名称 / 负责人" clearable>
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button plain>
              <el-icon><RefreshRight /></el-icon>
              刷新
            </el-button>
            <el-button type="primary">
              <el-icon><Plus /></el-icon>
              新建组件
            </el-button>
          </div>
        </el-header>

        <el-main class="component-manage-layout__main">
          <div
            v-for="record in filteredComponentRecords"
            :key="record.id"
            class="component-manage-card"
          >
            <div class="component-manage-card__row">
              <div>
                <div class="component-manage-card__name">{{ record.name }}</div>
                <div class="component-manage-card__meta">{{ record.id }}</div>
              </div>
              <el-tag size="small" effect="plain">{{ record.status }}</el-tag>
            </div>
            <div class="component-manage-card__info">
              <span>{{ record.type }}</span>
              <span>{{ record.owner }}</span>
              <span>{{ record.updatedAt }}</span>
            </div>
          </div>
        </el-main>

        <el-footer class="component-manage-layout__footer">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="pageSizeOptions"
            :pager-count="5"
            layout="total, sizes, prev, pager, next"
            :total="filteredComponentRecords.length"
          />
        </el-footer>
      </el-container>
    </el-container>
  </section>
</template>

<style scoped>
.system-settings-component-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.component-manage-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.component-manage-layout__aside {
  min-height: 0;
  padding: 14px 12px;
  border-right: 1px solid #eef2f7;
  background: #fbfcfd;
  overflow-y: auto;
}

.component-manage-layout__aside-title {
  margin-bottom: 10px;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
}

.component-manage-category {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #374151;
  font-size: 13px;
  cursor: pointer;
}

.component-manage-category.is-active {
  background: #eef4ff;
  color: #1d4ed8;
}

.component-manage-category strong {
  font-size: 12px;
  font-weight: 600;
}

.component-manage-layout__body {
  min-width: 0;
  min-height: 0;
}

.component-manage-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 66px;
  padding: 0 16px;
  border-bottom: 1px solid #eef2f7;
}

.component-manage-layout__title h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
}

.component-manage-layout__title p {
  margin: 3px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.component-manage-layout__tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.component-manage-layout__tools :deep(.el-input) {
  width: 240px;
}

.component-manage-layout__main {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 0;
  padding: 14px 16px;
  background: #f8fafc;
  overflow-y: auto;
}

.component-manage-card {
  padding: 14px;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  background: #fff;
}

.component-manage-card__row,
.component-manage-card__info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.component-manage-card__name {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.component-manage-card__meta,
.component-manage-card__info {
  color: #6b7280;
  font-size: 12px;
}

.component-manage-card__meta {
  margin-top: 2px;
}

.component-manage-layout__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid #eef2f7;
  background: #fff;
}

@media (max-width: 960px) {
  .component-manage-layout__header {
    flex-direction: column;
    align-items: flex-start;
    height: auto;
    padding: 12px;
  }

  .component-manage-layout__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .component-manage-layout__tools :deep(.el-input) {
    width: 100%;
  }
}
</style>
