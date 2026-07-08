<script setup>
import { computed } from 'vue'
import BaseChart from './BaseChart.vue'
import { DEFAULT_CHART_COLORS, createLegend, mergeChartOption, normalizeSeries } from './utils'

const props = defineProps({
  indicators: {
    type: Array,
    default: () => []
  },
  series: {
    type: Array,
    default: () => []
  },
  option: {
    type: Object,
    default: () => ({})
  },
  height: {
    type: [Number, String],
    default: 360
  },
  colors: {
    type: Array,
    default: () => DEFAULT_CHART_COLORS
  },
  legend: {
    type: Boolean,
    default: true
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const chartOption = computed(() => {
  const normalizedSeries = normalizeSeries(props.series).map((item) => ({
    type: 'radar',
    name: item.name,
    data: [
      {
        value: item.data,
        name: item.name
      }
    ],
    symbol: item.symbol ?? 'circle',
    symbolSize: item.symbolSize ?? 7,
    areaStyle: {
      opacity: item.opacity ?? 0.18
    },
    lineStyle: {
      width: item.lineWidth ?? 2
    }
  }))

  const baseOption = {
    color: props.colors,
    legend: createLegend(normalizedSeries.map((item) => item.name), props.legend),
    radar: {
      radius: '62%',
      center: ['50%', props.legend ? '56%' : '52%'],
      indicator: props.indicators,
      splitNumber: 4,
      axisName: {
        color: '#334155',
        fontSize: 12
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(15, 23, 42, 0.015)', 'rgba(15, 23, 42, 0.04)']
        }
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.24)'
        }
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.24)'
        }
      }
    },
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15, 23, 42, 0.88)',
      borderWidth: 0,
      textStyle: {
        color: '#f8fafc'
      }
    },
    series: normalizedSeries
  }

  return mergeChartOption(baseOption, props.option)
})
</script>

<template>
  <BaseChart :option="chartOption" :height="height" :loading="loading" />
</template>
