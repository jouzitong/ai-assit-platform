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
      backgroundColor: 'var(--app-chart-tooltip-bg)',
      borderWidth: 0,
      textStyle: {
        color: 'var(--app-chart-tooltip-text)',
      },
    },
    radar: {
      radius: '62%',
      center: ['50%', props.legend ? '56%' : '52%'],
      indicator: props.indicators,
      splitNumber: 4,
      axisName: {
        color: 'var(--app-chart-label)',
        fontSize: 12,
      },
      splitArea: {
        areaStyle: {
          color: ['var(--app-chart-radar-area-soft)', 'var(--app-chart-radar-area)'],
        },
      },
      splitLine: {
        lineStyle: {
          color: 'var(--app-chart-grid-strong)',
        },
      },
      axisLine: {
        lineStyle: {
          color: 'var(--app-chart-grid-strong)',
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
