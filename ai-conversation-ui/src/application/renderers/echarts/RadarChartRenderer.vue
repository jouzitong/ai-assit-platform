<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseEchart from './BaseEchart.vue'
import type { RadarIndicator, RadarSeries } from './types'
import { DEFAULT_ECHART_COLORS, createLegend, mergeOptions } from './utils'

const props = withDefaults(defineProps<{
  indicators: RadarIndicator[]
  series: RadarSeries[]
  option?: EChartsOption
  height?: number | string
  colors?: string[]
  legend?: boolean
  loading?: boolean
}>(), {
  option: () => ({}),
  height: 360,
  colors: () => DEFAULT_ECHART_COLORS,
  legend: true,
  loading: false,
})

const chartOption = computed<EChartsOption>(() => {
  const baseOption: EChartsOption = {
    color: props.colors,
    legend: createLegend(props.series.map((item) => item.name), props.legend),
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15, 23, 42, 0.9)',
      borderWidth: 0,
      textStyle: {
        color: '#f8fafc',
      },
    },
    radar: {
      radius: '62%',
      center: ['50%', props.legend ? '56%' : '52%'],
      indicator: props.indicators,
      splitNumber: 4,
      axisName: {
        color: '#334155',
        fontSize: 12,
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(15, 23, 42, 0.015)', 'rgba(15, 23, 42, 0.04)'],
        },
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.24)',
        },
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.24)',
        },
      },
    },
    series: props.series.map((item) => ({
      type: 'radar',
      name: item.name,
      data: [
        {
          value: item.data,
          name: item.name,
        },
      ],
      symbol: item.symbol ?? 'circle',
      symbolSize: item.symbolSize ?? 7,
      areaStyle: {
        opacity: item.opacity ?? 0.18,
      },
      lineStyle: {
        width: item.lineWidth ?? 2,
      },
    })),
  }

  return mergeOptions(baseOption as Record<string, unknown>, props.option as Record<string, unknown>) as EChartsOption
})
</script>

<template>
  <BaseEchart :option="chartOption" :height="height" :loading="loading" />
</template>
