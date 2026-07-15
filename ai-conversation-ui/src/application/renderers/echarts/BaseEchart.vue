<script setup lang="ts">
import * as echarts from 'echarts'
import type { EChartsType } from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { hasSeriesData, resolveCssVariables } from './utils'

const props = withDefaults(defineProps<{
  option: Record<string, unknown>
  height?: number | string
  width?: number | string
  loading?: boolean
  emptyText?: string
}>(), {
  height: 320,
  width: '100%',
  loading: false,
  emptyText: '暂无图表数据',
})

const chartRef = ref<HTMLDivElement | null>(null)
let chartInstance: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null
let themeObserver: MutationObserver | null = null

const shellStyle = computed(() => ({
  width: typeof props.width === 'number' ? `${props.width}px` : props.width,
  height: typeof props.height === 'number' ? `${props.height}px` : props.height,
}))

const isEmpty = computed(() => !hasSeriesData(props.option))

function renderChart() {
  if (!chartInstance) {
    return
  }

  if (props.loading) {
    chartInstance.showLoading('default', { text: '加载中...' })
  } else {
    chartInstance.hideLoading()
  }

  const resolvedOption = chartRef.value
    ? resolveCssVariables(props.option, chartRef.value)
    : props.option
  chartInstance.setOption(resolvedOption, true)
  nextTick(() => {
    chartInstance?.resize()
  })
}

function initChart() {
  if (!chartRef.value) {
    return
  }

  chartInstance = echarts.init(chartRef.value)
  renderChart()
}

function destroyChart() {
  resizeObserver?.disconnect()
  resizeObserver = null
  themeObserver?.disconnect()
  themeObserver = null
  chartInstance?.dispose()
  chartInstance = null
}

watch(() => props.option, renderChart, { deep: true })
watch(() => props.loading, renderChart)

onMounted(() => {
  initChart()

  if (chartRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      chartInstance?.resize()
    })
    resizeObserver.observe(chartRef.value)
  }

  if (typeof MutationObserver !== 'undefined') {
    themeObserver = new MutationObserver(renderChart)
    themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme', 'data-density', 'style'],
    })
  }
})

onBeforeUnmount(() => {
  destroyChart()
})

defineExpose({
  getInstance: () => chartInstance,
  resize: () => chartInstance?.resize(),
})
</script>

<template>
  <div class="base-echart" :style="shellStyle">
    <div ref="chartRef" class="base-echart__canvas" />
    <div v-if="isEmpty && !loading" class="base-echart__empty">
      {{ emptyText }}
    </div>
  </div>
</template>

<style scoped>
.base-echart {
  position: relative;
  max-width: 100%;
  min-width: 0;
  min-height: 220px;
  border-radius: var(--app-radius-panel);
  overflow: hidden;
  border: 1px solid var(--app-chart-border);
  background: var(--app-chart-background);
}

.base-echart__canvas {
  width: 100%;
  height: 100%;
}

.base-echart__empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--app-chart-label);
  font-size: var(--app-font-size-body-lg);
  background: var(--app-chart-empty-bg);
}
</style>
