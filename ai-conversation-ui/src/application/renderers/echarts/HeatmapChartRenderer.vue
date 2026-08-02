<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseEchart from './BaseEchart.vue'
import type { HeatmapPoint } from './types'
import { DEFAULT_ECHART_COLORS, mergeOptions } from './utils'

const props = withDefaults(defineProps<{
  xCategories: Array<string | number>
  yCategories: Array<string | number>
  data: HeatmapPoint[]
  option?: EChartsOption
  height?: number | string
  colors?: string[]
  loading?: boolean
}>(), {
  option: () => ({}),
  height: 320,
  colors: () => DEFAULT_ECHART_COLORS,
  loading: false,
})

const chartOption = computed<EChartsOption>(() => {
  const values = props.data
    .map(item => Number(Array.isArray(item) ? item[2] : item.value[2]))
    .filter(Number.isFinite)
  const min = values.length ? Math.min(...values) : 0
  const max = values.length ? Math.max(...values) : 1
  const palette = props.colors.length >= 3
    ? [props.colors[0], props.colors[2], props.colors[4] || props.colors[props.colors.length - 1]]
    : props.colors

  const baseOption: EChartsOption = {
    grid: { left: 18, right: 18, top: 18, bottom: 54, containLabel: true },
    tooltip: {
      position: 'top',
      backgroundColor: 'var(--app-chart-tooltip-bg)',
      borderWidth: 0,
      textStyle: { color: 'var(--app-chart-tooltip-text)' },
    },
    visualMap: {
      min,
      max: max === min ? min + 1 : max,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 4,
      textStyle: { color: 'var(--app-chart-label)' },
      inRange: { color: palette },
    },
    xAxis: {
      type: 'category',
      data: props.xCategories,
      splitArea: { show: true },
      axisLabel: { color: 'var(--app-chart-label)' },
      axisLine: { lineStyle: { color: 'var(--app-chart-axis)' } },
    },
    yAxis: {
      type: 'category',
      data: props.yCategories,
      splitArea: { show: true },
      axisLabel: { color: 'var(--app-chart-label)' },
      axisLine: { lineStyle: { color: 'var(--app-chart-axis)' } },
    },
    series: [{
      type: 'heatmap',
      data: props.data,
      label: { show: true, color: 'var(--app-chart-tooltip-text)' },
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowColor: 'rgba(15, 23, 42, 0.32)' },
      },
    }],
  }

  return mergeOptions(baseOption as Record<string, unknown>, props.option as Record<string, unknown>) as EChartsOption
})
</script>

<template>
  <BaseEchart :option="chartOption" :height="height" :loading="loading" />
</template>
