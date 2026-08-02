<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseEchart from './BaseEchart.vue'
import type { ScatterSeries } from './types'
import { DEFAULT_ECHART_COLORS, createLegend, mergeOptions } from './utils'

const props = withDefaults(defineProps<{
  series: ScatterSeries[]
  option?: EChartsOption
  height?: number | string
  colors?: string[]
  xName?: string
  yName?: string
  legend?: boolean
  loading?: boolean
}>(), {
  option: () => ({}),
  height: 320,
  colors: () => DEFAULT_ECHART_COLORS,
  xName: '',
  yName: '',
  legend: true,
  loading: false,
})

const chartOption = computed<EChartsOption>(() => {
  const baseOption: EChartsOption = {
    color: props.colors,
    grid: { left: 18, right: 18, top: props.legend ? 52 : 24, bottom: 24, containLabel: true },
    legend: createLegend(props.series.map(item => item.name), props.legend),
    tooltip: {
      trigger: 'item',
      backgroundColor: 'var(--app-chart-tooltip-bg)',
      borderWidth: 0,
      textStyle: { color: 'var(--app-chart-tooltip-text)' },
    },
    xAxis: {
      type: 'value',
      name: props.xName,
      nameTextStyle: { color: 'var(--app-chart-label)' },
      axisLabel: { color: 'var(--app-chart-label)' },
      axisLine: { lineStyle: { color: 'var(--app-chart-axis)' } },
      splitLine: { lineStyle: { color: 'var(--app-chart-grid)' } },
    },
    yAxis: {
      type: 'value',
      name: props.yName,
      nameTextStyle: { color: 'var(--app-chart-label)' },
      axisLabel: { color: 'var(--app-chart-label)' },
      axisLine: { lineStyle: { color: 'var(--app-chart-axis)' } },
      splitLine: { lineStyle: { color: 'var(--app-chart-grid)' } },
    },
    series: props.series.map(item => ({
      type: 'scatter',
      name: item.name,
      data: item.data,
      symbolSize: item.symbolSize ?? 10,
      itemStyle: item.color ? { color: item.color } : undefined,
      emphasis: { focus: 'series' },
    })),
  }

  return mergeOptions(baseOption as Record<string, unknown>, props.option as Record<string, unknown>) as EChartsOption
})
</script>

<template>
  <BaseEchart :option="chartOption" :height="height" :loading="loading" />
</template>
