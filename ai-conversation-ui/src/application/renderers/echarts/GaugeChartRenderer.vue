<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseEchart from './BaseEchart.vue'
import { DEFAULT_ECHART_COLORS, mergeOptions } from './utils'

const props = withDefaults(defineProps<{
  value: number
  min?: number
  max?: number
  unit?: string
  option?: EChartsOption
  height?: number | string
  colors?: string[]
  loading?: boolean
}>(), {
  min: 0,
  max: 100,
  unit: '',
  option: () => ({}),
  height: 280,
  colors: () => DEFAULT_ECHART_COLORS,
  loading: false,
})

const chartOption = computed<EChartsOption>(() => {
  const max = Math.max(props.max, props.min + Number.EPSILON)
  const baseOption: EChartsOption = {
    color: props.colors,
    series: [{
      type: 'gauge',
      min: props.min,
      max,
      startAngle: 210,
      endAngle: -30,
      center: ['50%', '56%'],
      radius: '78%',
      progress: {
        show: true,
        width: 16,
        itemStyle: { color: props.colors[0] },
      },
      axisLine: {
        lineStyle: {
          width: 16,
          color: [[1, 'var(--app-chart-grid)']],
        },
      },
      pointer: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      anchor: { show: false },
      detail: {
        valueAnimation: true,
        offsetCenter: [0, '6%'],
        color: 'var(--app-chart-label)',
        fontSize: 28,
        fontWeight: 700,
        formatter: props.unit ? `{value}${props.unit}` : '{value}',
      },
      data: [{ value: props.value }],
    }],
  }

  return mergeOptions(baseOption as Record<string, unknown>, props.option as Record<string, unknown>) as EChartsOption
})
</script>

<template>
  <BaseEchart :option="chartOption" :height="height" :loading="loading" />
</template>
