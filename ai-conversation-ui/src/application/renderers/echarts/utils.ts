import type { EChartsOption, SeriesOption } from 'echarts'

export const DEFAULT_ECHART_COLORS = [
  'var(--app-chart-color-1)',
  'var(--app-chart-color-2)',
  'var(--app-chart-color-3)',
  'var(--app-chart-color-4)',
  'var(--app-chart-color-5)',
  'var(--app-chart-color-6)',
  'var(--app-chart-color-7)',
  'var(--app-chart-color-8)',
]

const CSS_VARIABLE_PATTERN = /^var\((--[\w-]+)(?:,\s*(.+))?\)$/

function isRecord(value: unknown): value is Record<string, unknown> {
  return Object.prototype.toString.call(value) === '[object Object]'
}

export function resolveCssVariables<T>(value: T, element: Element): T {
  if (typeof value === 'string') {
    const match = value.match(CSS_VARIABLE_PATTERN)
    if (!match) {
      return value
    }

    const [, variableName, fallback = ''] = match
    const resolvedValue = getComputedStyle(element).getPropertyValue(variableName).trim()
    return (resolvedValue || fallback.trim() || value) as T
  }

  if (Array.isArray(value)) {
    return value.map((item) => resolveCssVariables(item, element)) as T
  }

  if (isRecord(value)) {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, resolveCssVariables(item, element)]),
    ) as T
  }

  return value
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
      color: 'var(--app-chart-label)',
      fontSize: 12,
    },
    data: names,
  }
}

export function createTooltip(unit = '') {
  return {
    trigger: 'axis',
    backgroundColor: 'var(--app-chart-tooltip-bg)',
    borderWidth: 0,
    textStyle: {
      color: 'var(--app-chart-tooltip-text)',
    },
    axisPointer: {
      type: 'line',
      lineStyle: {
        color: 'var(--app-chart-tooltip-axis)',
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
