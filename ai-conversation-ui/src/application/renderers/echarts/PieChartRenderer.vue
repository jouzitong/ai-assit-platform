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
  donut?: boolean
  legend?: boolean
  loading?: boolean
}>(), {
  option: () => ({}),
  height: 320,
  colors: () => DEFAULT_ECHART_COLORS,
  donut: false,
  legend: true,
  loading: false,
})

const chartOption = computed<EChartsOption>(() => {
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
      type: 'pie',
      radius: props.donut ? ['42%', '68%'] : ['0%', '68%'],
      center: ['50%', props.legend ? '56%' : '50%'],
      data: props.data,
      label: {
        color: 'var(--app-chart-label)',
        formatter: '{b}: {d}%',
      },
      labelLine: { lineStyle: { color: 'var(--app-chart-axis)' } },
      itemStyle: { borderColor: 'var(--app-chart-background)', borderWidth: 2 },
      emphasis: { scale: true, scaleSize: 6 },
    }],
  }

  return mergeOptions(baseOption as Record<string, unknown>, props.option as Record<string, unknown>) as EChartsOption
})
</script>

<template>
  <BaseEchart :option="chartOption" :height="height" :loading="loading" />
</template>
