import type { EChartsOption, SeriesOption } from 'echarts'

export const DEFAULT_ECHART_COLORS = [
  '#2563eb',
  '#0f766e',
  '#f97316',
  '#7c3aed',
  '#0891b2',
  '#dc2626',
  '#65a30d',
  '#ea580c',
]

function isRecord(value: unknown): value is Record<string, unknown> {
  return Object.prototype.toString.call(value) === '[object Object]'
}

export function mergeOptions<T extends Record<string, unknown>>(base: T, extra?: Record<string, unknown>): T {
  if (!extra) {
    return { ...base }
  }

  const result: Record<string, unknown> = { ...base }
  Object.entries(extra).forEach(([key, value]) => {
    const current = result[key]

    if (Array.isArray(value)) {
      result[key] = [...value]
      return
    }

    if (isRecord(current) && isRecord(value)) {
      result[key] = mergeOptions(current, value)
      return
    }

    result[key] = value
  })

  return result as T
}

export function hasSeriesData(option?: EChartsOption): boolean {
  if (!option || !Array.isArray(option.series)) {
    return false
  }

  return option.series.some((series) => {
    const candidate = series as SeriesOption & { data?: unknown[] }
    return Array.isArray(candidate.data) && candidate.data.length > 0
  })
}

export function createLegend(names: string[], enabled = true) {
  if (!enabled || names.length === 0) {
    return { show: false }
  }

  return {
    top: 0,
    left: 0,
    icon: 'roundRect',
    itemWidth: 12,
    itemHeight: 12,
    textStyle: {
      color: '#475569',
      fontSize: 12,
    },
    data: names,
  }
}

export function createTooltip(unit = '') {
  return {
    trigger: 'axis',
    backgroundColor: 'rgba(15, 23, 42, 0.9)',
    borderWidth: 0,
    textStyle: {
      color: '#f8fafc',
    },
    axisPointer: {
      type: 'line',
      lineStyle: {
        color: 'rgba(148, 163, 184, 0.45)',
      },
    },
    valueFormatter: unit ? (value: number | string) => `${value}${unit}` : undefined,
  }
}

export function createAxisLabelFormatter(unit = ''): ((value: number | string) => string) | undefined {
  if (!unit) {
    return undefined
  }

  return (value) => `${value}${unit}`
}
