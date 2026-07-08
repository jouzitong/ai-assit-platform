export const DEFAULT_CHART_COLORS = [
  '#0f766e',
  '#2563eb',
  '#f97316',
  '#dc2626',
  '#7c3aed',
  '#0891b2',
  '#65a30d',
  '#ea580c'
]

function isObject(value) {
  return Object.prototype.toString.call(value) === '[object Object]'
}

export function mergeChartOption(baseOption = {}, overrideOption = {}) {
  if (Array.isArray(baseOption) || Array.isArray(overrideOption)) {
    return Array.isArray(overrideOption) ? [...overrideOption] : [...(baseOption || [])]
  }

  const result = { ...baseOption }
  Object.keys(overrideOption || {}).forEach((key) => {
    const baseValue = result[key]
    const overrideValue = overrideOption[key]

    if (Array.isArray(overrideValue)) {
      result[key] = [...overrideValue]
      return
    }

    if (isObject(baseValue) && isObject(overrideValue)) {
      result[key] = mergeChartOption(baseValue, overrideValue)
      return
    }

    result[key] = overrideValue
  })

  return result
}

export function normalizeSeries(series = []) {
  return Array.isArray(series) ? series.filter((item) => item && Array.isArray(item.data)) : []
}

export function createLegend(names = [], enabled = true) {
  if (!enabled || !names.length) {
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
      fontSize: 12
    },
    data: names
  }
}

export function createTooltip(unit = '') {
  return {
    trigger: 'axis',
    backgroundColor: 'rgba(15, 23, 42, 0.88)',
    borderWidth: 0,
    textStyle: {
      color: '#f8fafc'
    },
    axisPointer: {
      type: 'line',
      lineStyle: {
        color: 'rgba(148, 163, 184, 0.45)'
      }
    },
    valueFormatter: unit ? (value) => `${value}${unit}` : undefined
  }
}

export function createAxisLabelFormatter(unit = '') {
  if (!unit) {
    return undefined
  }
  return (value) => `${value}${unit}`
}

export function hasChartData(option) {
  const series = option?.series
  return Array.isArray(series) && series.some((item) => Array.isArray(item?.data) && item.data.length > 0)
}
