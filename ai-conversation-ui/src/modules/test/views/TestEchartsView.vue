<script setup lang="ts">
import { ComboChartRenderer, LineChartRenderer, RadarChartRenderer } from '../../../application/renderers/echarts'

const lineProps = {
  categories: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
  unit: 'h',
  series: [
    { name: '研发人均投入', data: [6.2, 6.8, 7.1, 6.9, 7.4, 5.8, 4.2], area: true },
    { name: '客服人均投入', data: [5.5, 5.9, 6.2, 6.1, 6.5, 6.3, 5.7] },
  ],
} as const

const comboProps = {
  categories: ['1月', '2月', '3月', '4月', '5月', '6月'],
  leftUnit: '人',
  rightUnit: '%',
  barSeries: [
    { name: '招聘入职', data: [32, 28, 35, 31, 42, 38] },
    { name: '离职人数', data: [11, 8, 9, 12, 10, 13] },
  ],
  lineSeries: [
    { name: '留存率', data: [92, 94, 93, 91, 95, 94], yAxisIndex: 1 },
  ],
} as const

const radarProps = {
  indicators: [
    { name: '数据接入', max: 100 },
    { name: '模型治理', max: 100 },
    { name: '流程编排', max: 100 },
    { name: '权限审计', max: 100 },
    { name: '效果评估', max: 100 },
    { name: '知识沉淀', max: 100 },
  ],
  series: [
    { name: '当前版本', data: [82, 76, 88, 72, 80, 74], opacity: 0.22 },
    { name: '目标版本', data: [95, 90, 94, 90, 92, 88], opacity: 0.1 },
  ],
} as const

const usageTips = [
  '业务页只传 categories / series / indicators 等纯数据结构，组件内部不耦合接口请求。',
  '需要定制 tooltip、grid、markLine 或 visualMap 时，直接通过 option 覆盖。',
  '三类封装统一复用 BaseEchart，后续补饼图或漏斗图可以沿用同一套模式。',
]
</script>

<template>
  <section class="test-echarts-view">
    <header class="test-echarts-view__hero">
      <div>
        <p class="test-echarts-view__eyebrow">ECharts Renderer Test</p>
        <h1>ECharts 通用组件</h1>
        <p class="test-echarts-view__description">
          当前先提供折线图、组合图、雷达图三个通用封装，结构参考 list / form 的 renderer 测试页，便于直接在业务模块复制使用。
        </p>
      </div>
      <aside class="test-echarts-view__tips">
        <strong>使用约定</strong>
        <ul>
          <li v-for="item in usageTips" :key="item">{{ item }}</li>
        </ul>
      </aside>
    </header>

    <div class="test-echarts-view__grid">
      <article class="test-echarts-view__card">
        <div class="test-echarts-view__card-head">
          <div>
            <p class="test-echarts-view__card-kicker">line</p>
            <h2>折线图</h2>
          </div>
          <p>适合趋势追踪、环比变化和多序列对比。</p>
        </div>
        <LineChartRenderer v-bind="lineProps" />
        <pre class="test-echarts-view__code">{{ JSON.stringify(lineProps, null, 2) }}</pre>
      </article>

      <article class="test-echarts-view__card">
        <div class="test-echarts-view__card-head">
          <div>
            <p class="test-echarts-view__card-kicker">combo</p>
            <h2>组合图</h2>
          </div>
          <p>柱状 + 折线，适合规模和效率指标放在同一视图里。</p>
        </div>
        <ComboChartRenderer v-bind="comboProps" />
        <pre class="test-echarts-view__code">{{ JSON.stringify(comboProps, null, 2) }}</pre>
      </article>

      <article class="test-echarts-view__card">
        <div class="test-echarts-view__card-head">
          <div>
            <p class="test-echarts-view__card-kicker">radar</p>
            <h2>雷达图</h2>
          </div>
          <p>适合能力评估、组织成熟度和多维诊断。</p>
        </div>
        <RadarChartRenderer v-bind="radarProps" />
        <pre class="test-echarts-view__code">{{ JSON.stringify(radarProps, null, 2) }}</pre>
      </article>
    </div>
  </section>
</template>

<style scoped>
.test-echarts-view {
  display: grid;
  gap: 24px;
}

.test-echarts-view__hero {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.9fr);
  gap: 20px;
}

.test-echarts-view__eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: #2563eb;
}

.test-echarts-view__description {
  margin: 10px 0 0;
  max-width: 820px;
  line-height: 1.7;
  color: #475569;
}

.test-echarts-view__tips,
.test-echarts-view__card {
  border-radius: 28px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background:
    radial-gradient(560px 220px at 100% 0%, rgba(14, 165, 233, 0.12), transparent 60%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
}

.test-echarts-view__tips {
  padding: 20px 22px;
}

.test-echarts-view__tips strong {
  display: block;
  margin-bottom: 10px;
  color: #0f172a;
}

.test-echarts-view__tips ul {
  margin: 0;
  padding-left: 18px;
  line-height: 1.7;
  color: #475569;
}

.test-echarts-view__grid {
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
}

.test-echarts-view__card {
  padding: 20px;
}

.test-echarts-view__card-head {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
}

.test-echarts-view__card-head h2 {
  margin: 2px 0 0;
  font-size: 24px;
  color: #0f172a;
}

.test-echarts-view__card-head p {
  margin: 0;
  line-height: 1.6;
  color: #64748b;
}

.test-echarts-view__card-kicker {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #0f766e;
}

.test-echarts-view__code {
  margin: 14px 0 0;
  padding: 14px;
  overflow: auto;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.94);
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 960px) {
  .test-echarts-view__hero {
    grid-template-columns: 1fr;
  }
}
</style>
