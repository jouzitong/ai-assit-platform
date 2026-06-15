<script setup>
import { workflowTypes } from '../data/ai-flow'
</script>

<template>
  <div class="ai-flow-page">
    <header class="content-head">
      <div>
        <p class="crumb">系统设置 / AI流程配置</p>
        <h1>AI 流程配置</h1>
        <p class="section-desc">
          这里用于统一管理 AI 工作流的节点编排、技能挂载、SQL 生成规范和偏好策略。当前先按流程类型做列表入口。
        </p>
      </div>
    </header>

    <section class="flow-list-panel">
      <header class="flow-list-head">
        <div>
          <p class="eyebrow">流程类型列表</p>
          <h2>先按流程分类管理，再进入具体定义</h2>
        </div>
        <span class="flow-count">{{ workflowTypes.length }} 个流程类型</span>
      </header>

      <div class="flow-list">
        <article v-for="item in workflowTypes" :key="item.key" class="flow-row">
          <div class="flow-main">
            <div class="flow-title-row">
              <h3>{{ item.name }}</h3>
              <span class="flow-status" :class="item.status === '已接入' ? 'is-live' : 'is-draft'">
                {{ item.status }}
              </span>
            </div>
            <p class="flow-code">{{ item.code }}</p>
            <p class="flow-scene">{{ item.scene }}</p>
            <p class="flow-nodes">
              <span>默认节点：</span>
              {{ item.nodes }}
            </p>
          </div>

          <div class="flow-side">
            <div class="flow-tags">
              <span v-for="tag in item.tags" :key="tag" class="flow-tag">{{ tag }}</span>
            </div>
            <RouterLink :to="`/settings/system/ai-flow/${item.key}`" class="flow-action">
              进入配置
            </RouterLink>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ai-flow-page {
  display: grid;
  gap: 12px;
}

.content-head h1,
.content-head p,
.flow-list-head h2,
.flow-list-head p,
.flow-row h3,
.flow-row p {
  margin: 0;
}

.content-head h1 {
  font-size: 30px;
  line-height: 1.05;
}

.section-desc {
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.flow-list-panel {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 18px;
  background: #fff;
  padding: 14px;
  display: grid;
  gap: 12px;
}

.flow-list-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
}

.flow-list-head h2 {
  color: #0f172a;
  font-size: 18px;
  line-height: 1.2;
}

.flow-count {
  flex: none;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  font-size: 11px;
  font-weight: 600;
}

.flow-list {
  display: grid;
  gap: 10px;
}

.flow-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background:
    linear-gradient(135deg, rgba(248, 250, 252, 0.9), rgba(255, 255, 255, 1)),
    #fff;
}

.flow-main {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.flow-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.flow-title-row h3 {
  color: #0f172a;
  font-size: 16px;
  line-height: 1.2;
}

.flow-status {
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}

.flow-status.is-live {
  color: #166534;
  background: rgba(34, 197, 94, 0.12);
}

.flow-status.is-draft {
  color: #92400e;
  background: rgba(245, 158, 11, 0.14);
}

.flow-code {
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
}

.flow-scene,
.flow-nodes {
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.flow-nodes span {
  color: #334155;
  font-weight: 600;
}

.flow-side {
  display: grid;
  gap: 10px;
  justify-items: end;
}

.flow-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: end;
}

.flow-tag {
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid rgba(191, 219, 254, 0.95);
  background: rgba(239, 246, 255, 0.9);
  color: #1d4ed8;
  font-size: 10px;
  font-weight: 600;
}

.flow-action {
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 12px;
  background: #fff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  padding: 8px 12px;
  cursor: pointer;
}

.flow-action:hover {
  background: rgba(239, 246, 255, 0.9);
}

@media (max-width: 1100px) {
  .flow-list-head,
  .flow-row {
    grid-template-columns: 1fr;
  }

  .flow-list-head {
    align-items: start;
  }

  .flow-row {
    gap: 10px;
  }

  .flow-side,
  .flow-tags {
    justify-items: start;
    justify-content: start;
  }
}
</style>
