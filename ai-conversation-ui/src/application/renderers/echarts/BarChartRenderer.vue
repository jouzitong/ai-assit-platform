<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseEchart from './BaseEchart.vue'
import type { BarChartSeries } from './types'
import { DEFAULT_ECHART_COLORS, createAxisLabelFormatter, createLegend, createTooltip, mergeOptions } from './utils'

const props = withDefaults(defineProps<{
  categories: Array<string | number>
  series: BarChartSeries[]
  option?: EChartsOption
  height?: number | string
  unit?: string
  colors?: string[]
  legend?: boolean
  horizontal?: boolean
  stacked?: boolean
  loading?: boolean
}>(), {
  option: () => ({}),
  height: 320,
  unit: '',
  colors: () => DEFAULT_ECHART_COLORS,
  legend: true,
  horizontal: false,
  stacked: false,
  loading: false,
})

const chartOption = computed<EChartsOption>(() => {
  const categoryAxis = {
    type: 'category' as const,
    data: props.categories,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: 'var(--app-chart-axis)' } },
    axisLabel: { color: 'var(--app-chart-label)' },
  }
  const valueAxis = {
    type: 'value' as const,
    axisLabel: {
      color: 'var(--app-chart-label)',
      formatter: createAxisLabelFormatter(props.unit),
    },
    splitLine: { lineStyle: { color: 'var(--app-chart-grid)' } },
  }

  const baseOption: EChartsOption = {
    color: props.colors,
    grid: {
      left: 18,
      right: 18,
      top: props.legend ? 52 : 24,
      bottom: 18,
      containLabel: true,
    },
    legend: createLegend(props.series.map(item => item.name), props.legend),
    tooltip: createTooltip(props.unit),
    xAxis: props.horizontal ? valueAxis : categoryAxis,
    yAxis: props.horizontal ? categoryAxis : valueAxis,
    series: props.series.map(item => ({
      type: 'bar',
      name: item.name,
      data: item.data,
      stack: item.stack ?? (props.stacked ? 'total' : undefined),
      yAxisIndex: item.yAxisIndex ?? 0,
      barMaxWidth: item.barMaxWidth ?? 28,
      itemStyle: {
        ...(item.color ? { color: item.color } : {}),
        borderRadius: item.borderRadius ?? (props.horizontal ? [0, 10, 10, 0] : [10, 10, 0, 0]),
      },
      emphasis: { focus: 'series' },
    })),
  }

  return mergeOptions(baseOption as Record<string, unknown>, props.option as Record<string, unknown>) as EChartsOption
})
</script>

<template>
  <BaseEchart :option="chartOption" :height="height" :loading="loading" />
</template>
