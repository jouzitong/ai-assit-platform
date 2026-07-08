<script setup>
import { computed } from 'vue'
import BaseChart from './BaseChart.vue'
import {
  DEFAULT_CHART_COLORS,
  createAxisLabelFormatter,
  createLegend,
  createTooltip,
  mergeChartOption,
  normalizeSeries
} from './utils'

const props = defineProps({
  categories: {
    type: Array,
    default: () => []
  },
  series: {
    type: Array,
    default: () => []
  },
  option: {
    type: Object,
    default: () => ({})
  },
  height: {
    type: [Number, String],
    default: 320
  },
  colors: {
    type: Array,
    default: () => DEFAULT_CHART_COLORS
  },
  unit: {
    type: String,
    default: ''
  },
  smooth: {
    type: Boolean,
    default: true
  },
  area: {
    type: Boolean,
    default: false
  },
  showSymbol: {
    type: Boolean,
    default: false
  },
  legend: {
    type: Boolean,
    default: true
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const chartOption = computed(() => {
  const normalizedSeries = normalizeSeries(props.series).map((item) => ({
    type: 'line',
    name: item.name,
    data: item.data,
    smooth: item.smooth ?? props.smooth,
    showSymbol: item.showSymbol ?? props.showSymbol,
    symbolSize: item.symbolSize ?? 7,
    lineStyle: {
      width: item.lineWidth ?? 3
    },
    areaStyle: item.area || props.area ? { opacity: 0.14 } : undefined,
    yAxisIndex: item.yAxisIndex ?? 0,
    stack: item.stack,
    emphasis: {
      focus: 'series'
    }
  }))

  const baseOption = {
    color: props.colors,
    grid: {
      left: 18,
      right: 18,
      bottom: 18,
      top: props.legend ? 52 : 24,
      containLabel: true
    },
    legend: createLegend(normalizedSeries.map((item) => item.name), props.legend),
    tooltip: createTooltip(props.unit),
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.categories,
      axisTick: {
        show: false
      },
      axisLine: {
        lineStyle: {
          color: '#cbd5e1'
        }
      },
      axisLabel: {
        color: '#64748b'
      }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#64748b',
        formatter: createAxisLabelFormatter(props.unit)
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.18)'
        }
      }
    },
    series: normalizedSeries
  }

  return mergeChartOption(baseOption, props.option)
})
</script>

<template>
  <BaseChart :option="chartOption" :height="height" :loading="loading" />
</template>
