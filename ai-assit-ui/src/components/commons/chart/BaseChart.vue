<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { hasChartData } from './utils'

const props = defineProps({
  option: {
    type: Object,
    default: () => ({})
  },
  height: {
    type: [Number, String],
    default: 320
  },
  width: {
    type: [Number, String],
    default: '100%'
  },
  theme: {
    type: [String, Object],
    default: undefined
  },
  renderer: {
    type: String,
    default: 'canvas'
  },
  loading: {
    type: Boolean,
    default: false
  },
  autoresize: {
    type: Boolean,
    default: true
  },
  emptyText: {
    type: String,
    default: '暂无图表数据'
  }
})

const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null

const rootStyle = computed(() => ({
  width: typeof props.width === 'number' ? `${props.width}px` : props.width,
  height: typeof props.height === 'number' ? `${props.height}px` : props.height
}))

const isEmpty = computed(() => !hasChartData(props.option))

function initChart() {
  if (!chartRef.value) {
    return
  }

  chartInstance = echarts.init(chartRef.value, props.theme, {
    renderer: props.renderer
  })
  renderChart()
}

function renderChart() {
  if (!chartInstance) {
    return
  }

  if (props.loading) {
    chartInstance.showLoading('default', {
      text: '加载中...'
    })
  } else {
    chartInstance.hideLoading()
  }

  chartInstance.setOption(props.option || {}, true)
  nextTick(() => {
    chartInstance?.resize()
  })
}

function resizeChart() {
  chartInstance?.resize()
}

function destroyChart() {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
}

watch(
  () => props.option,
  () => {
    renderChart()
  },
  { deep: true }
)

watch(
  () => props.loading,
  () => {
    renderChart()
  }
)

onMounted(() => {
  initChart()

  if (props.autoresize && typeof ResizeObserver !== 'undefined' && chartRef.value) {
    resizeObserver = new ResizeObserver(() => {
      resizeChart()
    })
    resizeObserver.observe(chartRef.value)
  }
})

onBeforeUnmount(() => {
  destroyChart()
})

defineExpose({
  getInstance: () => chartInstance,
  resize: resizeChart
})
</script>

<template>
  <div class="chart-shell" :style="rootStyle">
    <div ref="chartRef" class="chart-canvas" />
    <div v-if="isEmpty && !loading" class="chart-empty">
      {{ emptyText }}
    </div>
  </div>
</template>

<style scoped>
.chart-shell {
  position: relative;
  min-height: 220px;
  border-radius: 22px;
  background:
    radial-gradient(500px 220px at 0% 0%, rgba(14, 165, 233, 0.12), transparent 65%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.98));
  border: 1px solid rgba(148, 163, 184, 0.2);
  overflow: hidden;
}

.chart-canvas {
  width: 100%;
  height: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #64748b;
  font-size: 14px;
  background: rgba(248, 250, 252, 0.72);
}
</style>
