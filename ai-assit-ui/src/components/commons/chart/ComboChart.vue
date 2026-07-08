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
  barSeries: {
    type: Array,
    default: () => []
  },
  lineSeries: {
    type: Array,
    default: () => []
  },
  option: {
    type: Object,
    default: () => ({})
  },
  height: {
    type: [Number, String],
    default: 340
  },
  colors: {
    type: Array,
    default: () => DEFAULT_CHART_COLORS
  },
  legend: {
    type: Boolean,
    default: true
  },
  leftUnit: {
    type: String,
    default: ''
  },
  rightUnit: {
    type: String,
    default: '%'
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const chartOption = computed(() => {
  const bars = normalizeSeries(props.barSeries).map((item) => ({
    type: 'bar',
    name: item.name,
    data: item.data,
    stack: item.stack,
    barMaxWidth: item.barMaxWidth ?? 28,
    yAxisIndex: item.yAxisIndex ?? 0,
    itemStyle: {
      borderRadius: item.borderRadius ?? [10, 10, 0, 0]
    },
    emphasis: {
      focus: 'series'
    }
  }))

  const lines = normalizeSeries(props.lineSeries).map((item) => ({
    type: 'line',
    name: item.name,
    data: item.data,
    smooth: item.smooth ?? true,
    yAxisIndex: item.yAxisIndex ?? 1,
    symbolSize: item.symbolSize ?? 8,
    lineStyle: {
      width: item.lineWidth ?? 3
    },
    emphasis: {
      focus: 'series'
    }
  }))

  const series = [...bars, ...lines]
  const baseOption = {
    color: props.colors,
    grid: {
      left: 18,
      right: 18,
      bottom: 18,
      top: props.legend ? 52 : 24,
      containLabel: true
    },
    legend: createLegend(series.map((item) => item.name), props.legend),
    tooltip: createTooltip(''),
    xAxis: {
      type: 'category',
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
    yAxis: [
      {
        type: 'value',
        axisLabel: {
          color: '#64748b',
          formatter: createAxisLabelFormatter(props.leftUnit)
        },
        splitLine: {
          lineStyle: {
            color: 'rgba(148, 163, 184, 0.18)'
          }
        }
      },
      {
        type: 'value',
        axisLabel: {
          color: '#64748b',
          formatter: createAxisLabelFormatter(props.rightUnit)
        },
        splitLine: {
          show: false
        }
      }
    ],
    series
  }

  return mergeChartOption(baseOption, props.option)
})
</script>

<template>
  <BaseChart :option="chartOption" :height="height" :loading="loading" />
</template>
