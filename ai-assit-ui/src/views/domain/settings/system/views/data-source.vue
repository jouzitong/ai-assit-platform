<script setup>
import { Connection, DataBoard, Plus, Search } from '@element-plus/icons-vue'
import '../styles/data-source.css'
import { useDataSourcePage } from '../service/data-source'

const {
  keyword,
  selectedSourceKey,
  filteredSources,
  openSource,
  statusClass
} = useDataSourcePage()
</script>

<template>
  <div class="data-source-page">
    <section class="content-head compact">
      <div>
        <p class="eyebrow">数据源配置</p>
        <h2>把连接管理和表数据运维收在一个工作台里</h2>
        <p class="section-desc">
          第一屏先看数据源清单，确认名称、连接信息、类型、状态和归属。
        </p>
      </div>
    </section>

    <section class="source-list-card">
      <div class="toolbar">
        <label class="search-box">
          <Search :size="16" />
          <input v-model="keyword" type="text" placeholder="搜索数据源名称、类型、负责人或库名" />
        </label>

        <div class="toolbar-actions">
          <button type="button" class="toolbar-add-btn">
            <Plus :size="16" />
            新增
          </button>
        </div>
      </div>

      <div class="source-stack">
        <div
          v-for="item in filteredSources"
          :key="item.key"
          class="source-row"
          :class="{ active: selectedSourceKey === item.key }"
          @click="openSource(item.key)"
          role="button"
          tabindex="0"
          @keydown.enter.prevent="openSource(item.key)"
          @keydown.space.prevent="openSource(item.key)"
        >
          <div class="source-row-main">
            <div class="source-title">
              <strong>{{ item.name }}</strong>
              <span class="status-chip" :class="statusClass(item.status)">{{ item.statusLabel }}</span>
            </div>

            <p>{{ item.summary }}</p>

            <div class="source-meta">
              <span><Connection :size="14" /> {{ item.host }}</span>
              <span><DataBoard :size="14" /> {{ item.database }}</span>
              <span>{{ item.type }}</span>
              <span>{{ item.owner }}</span>
            </div>
          </div>

          <div class="source-row-side">
            <div class="source-metric">
              <strong>{{ item.tables }}</strong>
              <span>表数量</span>
            </div>
            <div class="source-metric">
              <strong>{{ item.syncMode }}</strong>
              <span>同步频率</span>
            </div>
            <button type="button" class="row-action-btn" @click.stop>
              操作
            </button>
          </div>
        </div>

        <div v-if="!filteredSources.length" class="placeholder-panel">
          <p>没有匹配到数据源。这里后续可以接真实接口，支持按状态、类型和负责人筛选。</p>
        </div>
      </div>
    </section>
  </div>
</template>
