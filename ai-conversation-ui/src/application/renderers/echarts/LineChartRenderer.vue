<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseEchart from './BaseEchart.vue'
import type { LineChartSeries } from './types'
import { DEFAULT_ECHART_COLORS, createAxisLabelFormatter, createLegend, createTooltip, mergeOptions } from './utils'

const props = withDefaults(defineProps<{
  categories: Array<string | number>
  series: LineChartSeries[]
  option?: EChartsOption
  height?: number | string
  unit?: string
  colors?: string[]
  smooth?: boolean
  area?: boolean
  showSymbol?: boolean
  legend?: boolean
  loading?: boolean
}>(), {
  option: () => ({}),
  height: 320,
  unit: '',
  colors: () => DEFAULT_ECHART_COLORS,
  smooth: true,
  area: false,
  showSymbol: false,
  legend: true,
  loading: false,
})

const chartOption = computed<EChartsOption>(() => {
  const baseOption: EChartsOption = {
    color: props.colors,
    grid: {
      left: 18,
      right: 18,
      top: props.legend ? 52 : 24,
      bottom: 18,
      containLabel: true,
    },
    legend: createLegend(props.series.map((item) => item.name), props.legend),
    tooltip: createTooltip(props.unit),
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.categories,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'var(--app-chart-axis)' } },
      axisLabel: { color: 'var(--app-chart-label)' },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: 'var(--app-chart-label)',
        formatter: createAxisLabelFormatter(props.unit),
      },
      splitLine: {
        lineStyle: {
          color: 'var(--app-chart-grid)',
        },
      },
    },
    series: props.series.map((item) => ({
      type: 'line',
      name: item.name,
      data: item.data,
      smooth: item.smooth ?? props.smooth,
      showSymbol: item.showSymbol ?? props.showSymbol,
      symbolSize: item.symbolSize ?? 7,
      stack: item.stack,
      yAxisIndex: item.yAxisIndex ?? 0,
      lineStyle: {
        width: item.lineWidth ?? 3,
      },
      areaStyle: item.area ?? props.area ? { opacity: 0.14 } : undefined,
      emphasis: {
        focus: 'series',
      },
    })),
  }

  return mergeOptions(baseOption as Record<string, unknown>, props.option as Record<string, unknown>) as EChartsOption
})
</script>

<template>
  <BaseEchart :option="chartOption" :height="height" :loading="loading" />
</template>
