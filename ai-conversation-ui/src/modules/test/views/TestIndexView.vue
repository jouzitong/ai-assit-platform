<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChatDotRound,
  DataAnalysis,
  EditPen,
  Files,
  List,
  Monitor,
  Operation,
} from '@element-plus/icons-vue'

interface TestCaseMeta {
  order?: number
  title?: string
  category?: string
  description?: string
}

const router = useRouter()

const iconByRouteName: Record<string, Component> = {
  'test-list': List,
  'test-form': Files,
  'test-editor': EditPen,
  'test-dialog': Operation,
  'test-echarts': DataAnalysis,
  'test-chat': ChatDotRound,
  'test-render-runtime': Monitor,
}

const testCases = computed(() => router
  .getRoutes()
  .filter(route => route.path.startsWith('/test/') && route.path !== '/test')
  .map((route) => {
    const name = String(route.name || route.path)
    const metadata = route.meta.testCase as TestCaseMeta | undefined

    return {
      name,
      path: route.path,
      order: metadata?.order ?? Number.MAX_SAFE_INTEGER,
      title: metadata?.title || String(route.meta.title || route.name || route.path),
      category: metadata?.category || '其他',
      description: metadata?.description || `访问 ${route.path} 查看测试案例。`,
      icon: iconByRouteName[name] || Monitor,
    }
  })
  .sort((left, right) => left.order - right.order || left.path.localeCompare(right.path)))
</script>

<template>
  <section class="test-index-view">
    <header class="test-index-view__hero">
      <div class="test-index-view__heading">
        <p class="test-index-view__eyebrow">COMPONENT PLAYGROUND</p>
        <h1>测试案例中心</h1>
        <p>集中访问前端组件、渲染器和综合交互测试页面。</p>
      </div>

      <div class="test-index-view__summary" aria-label="测试案例统计">
        <strong>{{ testCases.length }}</strong>
        <span>个测试案例</span>
      </div>
    </header>

    <div v-if="testCases.length" class="test-index-view__grid">
      <RouterLink
        v-for="item in testCases"
        :key="item.path"
        :to="item.path"
        class="test-index-view__card"
      >
        <div class="test-index-view__card-head">
          <span class="test-index-view__icon" aria-hidden="true">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <el-tag effect="plain" size="small">{{ item.category }}</el-tag>
        </div>

        <div class="test-index-view__card-content">
          <h2>{{ item.title }}</h2>
          <p>{{ item.description }}</p>
        </div>

        <div class="test-index-view__card-footer">
          <code>{{ item.path }}</code>
          <span>
            进入测试
            <el-icon><ArrowRight /></el-icon>
          </span>
        </div>
      </RouterLink>
    </div>

    <el-empty v-else description="暂无测试案例" />
  </section>
</template>

<style scoped>
.test-index-view {
  box-sizing: border-box;
  min-height: 100%;
  padding: var(--app-space-8);
  container-type: inline-size;
  color: var(--app-text);
}

.test-index-view__hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--app-space-6);
  margin-bottom: var(--app-space-8);
}

.test-index-view__heading {
  min-width: 0;
}

.test-index-view__eyebrow {
  margin: 0 0 var(--app-space-2);
  color: var(--app-accent);
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  letter-spacing: 0.16em;
}

.test-index-view__heading h1 {
  margin: 0;
  color: var(--app-title);
  font-size: var(--app-font-size-display);
  line-height: var(--app-line-height-tight);
}

.test-index-view__heading > p:last-child {
  margin: var(--app-space-3) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-body-lg);
  line-height: var(--app-line-height-body);
}

.test-index-view__summary {
  display: flex;
  align-items: baseline;
  gap: var(--app-space-2);
  flex: 0 0 auto;
  padding: var(--app-space-3) var(--app-space-5);
  border: 1px solid var(--app-accent-border);
  border-radius: var(--app-radius-round);
  background: var(--app-accent-bg);
  color: var(--app-accent);
}

.test-index-view__summary strong {
  font-size: var(--app-font-size-title-lg);
  line-height: var(--app-line-height-tight);
}

.test-index-view__summary span {
  font-size: var(--app-font-size-body);
}

.test-index-view__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  gap: var(--app-space-5);
}

.test-index-view__card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 220px;
  padding: var(--app-space-5);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-gradient);
  box-shadow: var(--app-shadow-md);
  color: inherit;
  text-decoration: none;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.test-index-view__card:hover {
  border-color: var(--app-accent-border);
  box-shadow: var(--app-accent-shadow);
  transform: translateY(calc(var(--app-space-hairline) * -1));
}

.test-index-view__card:focus-visible {
  outline: var(--app-space-hairline) solid var(--app-accent);
  outline-offset: var(--app-space-hairline);
}

.test-index-view__card-head,
.test-index-view__card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-3);
}

.test-index-view__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--app-control-height-lg);
  height: var(--app-control-height-lg);
  border-radius: var(--app-radius-control);
  background: var(--app-accent-bg);
  color: var(--app-accent);
  font-size: var(--app-font-size-title-md);
}

.test-index-view__card-content {
  flex: 1 1 auto;
  padding: var(--app-space-5) 0;
}

.test-index-view__card-content h2 {
  margin: 0;
  color: var(--app-title);
  font-size: var(--app-font-size-title-md);
  line-height: var(--app-line-height-tight);
}

.test-index-view__card-content p {
  margin: var(--app-space-3) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-loose);
}

.test-index-view__card-footer {
  padding-top: var(--app-space-4);
  border-top: 1px solid var(--app-border-subtle);
}

.test-index-view__card-footer code {
  min-width: 0;
  overflow: hidden;
  color: var(--app-text-soft);
  font-size: var(--app-font-size-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.test-index-view__card-footer span {
  display: inline-flex;
  align-items: center;
  gap: var(--app-space-1);
  flex: 0 0 auto;
  color: var(--app-accent);
  font-size: var(--app-font-size-body);
  font-weight: 600;
}

@container (max-width: 640px) {
  .test-index-view__hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .test-index-view__summary {
    padding: var(--app-space-2) var(--app-space-4);
  }
}
</style>
