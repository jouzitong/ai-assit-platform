<script setup>
import { ComboChart, LineChart, RadarChart } from '../../../../../components/commons/chart'

defineProps({
  pageTitle: {
    type: String,
    required: true
  },
  pageDescription: {
    type: String,
    required: true
  },
  chartExamples: {
    type: Array,
    default: () => []
  },
  usageTips: {
    type: Array,
    default: () => []
  }
})

function resolveChartComponent(type) {
  if (type === 'combo') {
    return ComboChart
  }
  if (type === 'radar') {
    return RadarChart
  }
  return LineChart
}
</script>

<template>
  <main class="page chart-showcase-page">
    <section class="chart-hero">
      <div>
        <p class="chart-eyebrow">Common Charts</p>
        <h1>{{ pageTitle }}</h1>
        <p class="chart-description">{{ pageDescription }}</p>
      </div>
      <div class="chart-tip-panel">
        <strong>使用约定</strong>
        <ul>
          <li v-for="item in usageTips" :key="item">{{ item }}</li>
        </ul>
      </div>
    </section>

    <section class="chart-grid">
      <article v-for="item in chartExamples" :key="item.key" class="chart-card">
        <div class="chart-card-head">
          <div>
            <p class="chart-card-kicker">{{ item.component }}</p>
            <h2>{{ item.title }}</h2>
          </div>
          <p>{{ item.desc }}</p>
        </div>

        <component :is="resolveChartComponent(item.component)" v-bind="item.props" />

        <pre class="chart-props">{{ JSON.stringify(item.props, null, 2) }}</pre>
      </article>
    </section>
  </main>
</template>
