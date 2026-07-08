<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseEchart from './BaseEchart.vue'
import type { ComboBarSeries, ComboLineSeries } from './types'
import { DEFAULT_ECHART_COLORS, createAxisLabelFormatter, createLegend, createTooltip, mergeOptions } from './utils'

const props = withDefaults(defineProps<{
  categories: Array<string | number>
  barSeries: ComboBarSeries[]
  lineSeries?: ComboLineSeries[]
  option?: EChartsOption
  height?: number | string
  colors?: string[]
  legend?: boolean
  loading?: boolean
  leftUnit?: string
  rightUnit?: string
}>(), {
  lineSeries: () => [],
  option: () => ({}),
  height: 340,
  colors: () => DEFAULT_ECHART_COLORS,
  legend: true,
  loading: false,
  leftUnit: '',
  rightUnit: '%',
})

const chartOption = computed<EChartsOption>(() => {
  const seriesNames = [...props.barSeries, ...props.lineSeries].map((item) => item.name)

  const baseOption: EChartsOption = {
    color: props.colors,
    grid: {
      left: 18,
      right: 18,
      top: props.legend ? 52 : 24,
      bottom: 18,
      containLabel: true,
    },
    legend: createLegend(seriesNames, props.legend),
    tooltip: createTooltip(''),
    xAxis: {
      type: 'category',
      data: props.categories,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b' },
    },
    yAxis: [
      {
        type: 'value',
        axisLabel: {
          color: '#64748b',
          formatter: createAxisLabelFormatter(props.leftUnit),
        },
        splitLine: {
          lineStyle: {
            color: 'rgba(148, 163, 184, 0.18)',
          },
        },
      },
      {
        type: 'value',
        axisLabel: {
          color: '#64748b',
          formatter: createAxisLabelFormatter(props.rightUnit),
        },
        splitLine: {
          show: false,
        },
      },
    ],
    series: [
      ...props.barSeries.map((item) => ({
        type: 'bar',
        name: item.name,
        data: item.data,
        stack: item.stack,
        yAxisIndex: item.yAxisIndex ?? 0,
        barMaxWidth: item.barMaxWidth ?? 28,
        itemStyle: {
          borderRadius: item.borderRadius ?? [10, 10, 0, 0],
        },
        emphasis: {
          focus: 'series',
        },
      })),
      ...props.lineSeries.map((item) => ({
        type: 'line',
        name: item.name,
        data: item.data,
        smooth: item.smooth ?? true,
        showSymbol: item.showSymbol ?? true,
        symbolSize: item.symbolSize ?? 8,
        yAxisIndex: item.yAxisIndex ?? 1,
        lineStyle: {
          width: item.lineWidth ?? 3,
        },
        emphasis: {
          focus: 'series',
        },
      })),
    ],
  }

  return mergeOptions(baseOption as Record<string, unknown>, props.option as Record<string, unknown>) as EChartsOption
})
</script>

<template>
  <BaseEchart :option="chartOption" :height="height" :loading="loading" />
</template>
