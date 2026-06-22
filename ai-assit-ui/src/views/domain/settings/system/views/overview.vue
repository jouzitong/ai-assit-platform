<script setup>
import {
  ArrowRight,
  Collection,
  DataLine,
  Grid,
  Monitor,
  Setting,
  TrendCharts
} from '@element-plus/icons-vue'

const statusCards = [
  { label: '配置域', value: '6', note: '当前已接入主入口', tone: 'neutral' },
  { label: 'AI 流程', value: '12', note: '节点策略已启用', tone: 'primary' },
  { label: '待处理提醒', value: '3', note: '需要本周完成', tone: 'warning' },
  { label: '最近变更', value: '18', note: '7 天内更新', tone: 'success' }
]

const quickEntries = [
  {
    key: 'data-source',
    label: '数据源配置',
    summary: '连接、健康状态、表数据入口',
    icon: DataLine,
    meta: '12 个数据源',
    to: '/settings/system/data-source'
  },
  {
    key: 'params',
    label: '系统参数',
    summary: '全局开关、默认值、运行阈值',
    icon: Grid,
    meta: '38 项参数',
    to: '/settings/system/params'
  },
  {
    key: 'components',
    label: '常用组件',
    summary: '模板片段、复用区块、页面骨架',
    icon: Collection,
    meta: '18 个组件',
    to: '/settings/system/components'
  },
  {
    key: 'ai',
    label: 'AI 接入',
    summary: 'provider、model、credential 管理',
    icon: Monitor,
    meta: '6 个模型组',
    to: '/settings/system/ai'
  },
  {
    key: 'ai-flow',
    label: 'AI流程配置',
    summary: '工作流、节点、skill、SQL 规范',
    icon: Setting,
    meta: '4 条主流程',
    to: '/settings/system/ai-flow'
  },
  {
    key: 'overview',
    label: '系统总览',
    summary: '聚合入口、变更和治理状态',
    icon: TrendCharts,
    meta: '当前页',
    to: '/settings/system/overview'
  }
]

const changeFeed = [
  {
    date: '06-22',
    title: 'AI流程配置入口已合并到系统设置',
    desc: '统一承载节点编排、技能挂载和 SQL 生成策略，避免多个入口分散维护。'
  },
  {
    date: '06-18',
    title: '数据源管理新增表数据工作台',
    desc: '可直接在数据源配置页进入表级管理与字段维护。'
  },
  {
    date: '06-15',
    title: 'AI 接入页补齐模型与凭证台账',
    desc: 'provider、model、credential 三层结构已可独立维护。'
  },
  {
    date: '06-12',
    title: '系统参数分组视图完成第一轮收口',
    desc: '便于后续继续拆分开关、默认值和运行阈值。'
  }
]

const todos = [
  '复核 AI 流程页的节点模板字段映射',
  '整理系统参数中的默认值与灰度开关分组',
  '确认数据源连接健康状态的批量刷新策略',
  '补充常用组件页的模板分类与负责人信息'
]

const tips = [
  { label: '推荐先看', value: 'AI 接入 / AI流程配置' },
  { label: '最近最活跃', value: '数据源配置' },
  { label: '治理关注点', value: '参数分组与变更审计' }
]
</script>

<template>
  <div class="overview-page">
    <section class="overview-hero">
      <div class="hero-copy">
        <p class="eyebrow">System Overview</p>
        <h2>系统配置工作台</h2>
        <p class="hero-desc">
          这里不做门户化堆料，只保留配置台真正高频的信息：入口、状态、最近变更和待办。
        </p>
      </div>

      <div class="hero-actions">
        <RouterLink to="/settings/system/ai" class="hero-btn primary">
          进入 AI 接入
        </RouterLink>
        <RouterLink to="/settings/system/ai-flow" class="hero-btn secondary">
          打开 AI流程配置
        </RouterLink>
      </div>
    </section>

    <section class="status-strip">
      <article v-for="item in statusCards" :key="item.label" class="status-card" :data-tone="item.tone">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.note }}</p>
      </article>
    </section>

    <section class="overview-main">
      <div class="main-left">
        <section class="panel">
          <div class="section-head">
            <div>
              <p class="eyebrow">Quick Access</p>
              <h3>配置入口</h3>
            </div>
            <span class="section-meta">紧凑模式</span>
          </div>

          <div class="entry-grid">
            <RouterLink v-for="entry in quickEntries" :key="entry.key" :to="entry.to" class="entry-card">
              <span class="entry-icon">
                <component :is="entry.icon" :size="16" />
              </span>
              <div class="entry-copy">
                <strong>{{ entry.label }}</strong>
                <p>{{ entry.summary }}</p>
              </div>
              <span class="entry-meta">{{ entry.meta }}</span>
            </RouterLink>
          </div>
        </section>

        <section class="dual-grid">
          <article class="panel">
            <div class="section-head">
              <div>
                <p class="eyebrow">Change Feed</p>
                <h3>最近变更</h3>
              </div>
            </div>

            <div class="feed-list">
              <article v-for="item in changeFeed" :key="item.title" class="feed-item">
                <span class="feed-date">{{ item.date }}</span>
                <div class="feed-copy">
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.desc }}</p>
                </div>
              </article>
            </div>
          </article>

          <article class="panel">
            <div class="section-head">
              <div>
                <p class="eyebrow">Todo</p>
                <h3>待处理事项</h3>
              </div>
            </div>

            <ul class="todo-list">
              <li v-for="item in todos" :key="item">{{ item }}</li>
            </ul>
          </article>
        </section>
      </div>

      <aside class="main-right">
        <section class="panel side-panel">
          <div class="section-head">
            <div>
              <p class="eyebrow">Focus</p>
              <h3>当前提示</h3>
            </div>
          </div>

          <div class="tip-list">
            <article v-for="item in tips" :key="item.label" class="tip-card">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </article>
          </div>
        </section>

        <section class="panel side-panel">
          <div class="section-head">
            <div>
              <p class="eyebrow">Flow</p>
              <h3>推荐路径</h3>
            </div>
          </div>

          <div class="flow-list">
            <RouterLink to="/settings/system/data-source" class="flow-item">
              <strong>1. 先看数据源配置</strong>
              <span>核对连接状态与表数据入口</span>
              <ArrowRight :size="14" />
            </RouterLink>
            <RouterLink to="/settings/system/ai" class="flow-item">
              <strong>2. 再看 AI 接入</strong>
              <span>统一 provider / model / credential</span>
              <ArrowRight :size="14" />
            </RouterLink>
            <RouterLink to="/settings/system/ai-flow" class="flow-item">
              <strong>3. 最后看 AI流程配置</strong>
              <span>处理节点与工作流编排策略</span>
              <ArrowRight :size="14" />
            </RouterLink>
          </div>
        </section>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.overview-page {
  display: grid;
  gap: 14px;
  min-height: 0;
  padding: 0;
  overflow-x: hidden;
  overflow-y: visible;
}

.overview-hero,
.panel,
.status-card {
  border: 1px solid rgba(226, 232, 240, 0.92);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.overview-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 18px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero-copy h2,
.section-head h3 {
  margin: 0;
  color: #0f172a;
  letter-spacing: -0.03em;
}

.hero-copy h2 {
  font-size: 28px;
  line-height: 1.05;
}

.hero-desc {
  margin: 8px 0 0;
  max-width: 720px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-self: center;
}

.hero-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 13px;
  border-radius: 12px;
  text-decoration: none;
  font-size: 12px;
  font-weight: 700;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.hero-btn:hover {
  transform: translateY(-1px);
}

.hero-btn.primary {
  color: #eff6ff;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 12px 20px rgba(37, 99, 235, 0.2);
}

.hero-btn.secondary {
  color: #0f172a;
  background: #f8fafc;
}

.status-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-card {
  padding: 14px;
  border-radius: 14px;
}

.status-card span {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
}

.status-card strong {
  display: block;
  margin-bottom: 6px;
  font-size: 26px;
  line-height: 1;
  letter-spacing: -0.04em;
}

.status-card p {
  margin: 0;
  color: #475569;
  font-size: 12px;
}

.status-card[data-tone='primary'] {
  border-color: rgba(59, 130, 246, 0.18);
}

.status-card[data-tone='warning'] {
  border-color: rgba(217, 119, 6, 0.16);
}

.status-card[data-tone='success'] {
  border-color: rgba(22, 163, 74, 0.16);
}

.overview-main {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(280px, 0.72fr);
  gap: 14px;
  min-height: 0;
}

.main-left,
.main-right {
  display: grid;
  gap: 14px;
  min-height: 0;
}

.panel {
  border-radius: 18px;
  padding: 15px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-meta {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.entry-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  min-height: 84px;
  padding: 11px 12px;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  text-decoration: none;
  color: inherit;
  background: #fbfdff;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.entry-card:hover {
  transform: translateY(-1px);
  border-color: rgba(59, 130, 246, 0.18);
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.08);
}

.entry-icon {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: rgba(59, 130, 246, 0.1);
  color: #2563eb;
}

.entry-copy strong,
.feed-copy strong,
.tip-card strong,
.flow-item strong {
  display: block;
  color: #0f172a;
}

.entry-copy strong {
  margin-bottom: 4px;
  font-size: 13px;
}

.entry-copy p,
.feed-copy p,
.flow-item span,
.todo-list li {
  color: #64748b;
}

.entry-copy p {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
}

.entry-meta {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.dual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.feed-list,
.tip-list,
.flow-list {
  display: grid;
  gap: 10px;
}

.feed-item {
  display: grid;
  grid-template-columns: 54px 1fr;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid rgba(226, 232, 240, 0.82);
}

.feed-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.feed-date {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 800;
}

.feed-copy strong {
  margin-bottom: 4px;
  font-size: 13px;
}

.feed-copy p {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
}

.todo-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 12px;
}

.todo-list li {
  font-size: 13px;
  line-height: 1.6;
}

.side-panel {
  padding: 14px;
}

.tip-card,
.flow-item {
  padding: 11px 12px;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: #fbfdff;
}

.tip-card span {
  display: block;
  margin-bottom: 6px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
}

.tip-card strong {
  font-size: 13px;
  line-height: 1.45;
}

.flow-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  align-items: center;
  text-decoration: none;
}

.flow-item strong {
  margin-bottom: 4px;
  font-size: 13px;
}

.flow-item span {
  font-size: 12px;
  line-height: 1.5;
}

.flow-item :deep(svg) {
  color: #94a3b8;
}

@media (max-width: 1180px) {
  .status-strip,
  .overview-main,
  .dual-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .overview-page {
    gap: 10px;
    padding-top: 0;
  }

  .overview-hero {
    flex-direction: column;
  }

  .entry-grid {
    grid-template-columns: 1fr;
  }
}
</style>
