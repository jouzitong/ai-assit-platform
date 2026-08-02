<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseEchart from './BaseEchart.vue'
import type { PieDataItem } from './types'
import { DEFAULT_ECHART_COLORS, createLegend, mergeOptions } from './utils'

const props = withDefaults(defineProps<{
  data: PieDataItem[]
  option?: EChartsOption
  height?: number | string
  colors?: string[]
  legend?: boolean
  loading?: boolean
}>(), {
  option: () => ({}),
  height: 320,
  colors: () => DEFAULT_ECHART_COLORS,
  legend: true,
  loading: false,
})

const chartOption = computed<EChartsOption>(() => {
  const max = Math.max(...props.data.map(item => Number(item.value) || 0), 1)
  const baseOption: EChartsOption = {
    color: props.colors,
    legend: createLegend(props.data.map(item => item.name), props.legend),
    tooltip: {
      trigger: 'item',
      backgroundColor: 'var(--app-chart-tooltip-bg)',
      borderWidth: 0,
      textStyle: { color: 'var(--app-chart-tooltip-text)' },
    },
    series: [{
      type: 'funnel',
      left: '8%',
      top: props.legend ? 52 : 18,
      bottom: 18,
      width: '84%',
      min: 0,
      max,
      minSize: '0%',
      maxSize: '100%',
      sort: 'descending',
      gap: 5,
      label: { color: 'var(--app-chart-label)' },
      labelLine: { lineStyle: { color: 'var(--app-chart-axis)' } },
      data: props.data,
    }],
  }

  return mergeOptions(baseOption as Record<string, unknown>, props.option as Record<string, unknown>) as EChartsOption
})
</script>

<template>
  <BaseEchart :option="chartOption" :height="height" :loading="loading" />
</template>
