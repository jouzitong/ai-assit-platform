type JsonRecord = Record<string, unknown>

type ChartRendererKey =
  | 'line-chart-renderer'
  | 'combo-chart-renderer'
  | 'radar-chart-renderer'
  | 'pie-chart-renderer'
  | 'bar-chart-renderer'
  | 'gauge-chart-renderer'
  | 'funnel-chart-renderer'
  | 'scatter-chart-renderer'
  | 'heatmap-chart-renderer'

const CHART_DATA_KEYS = new Set([
  'categories',
  'series',
  'barSeries',
  'lineSeries',
  'indicators',
  'xCategories',
  'yCategories',
  'data',
  'value',
])

function isRecord(value: unknown): value is JsonRecord {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function recordsFrom(value: unknown): JsonRecord[] {
  if (!isRecord(value) || !Array.isArray(value.records)) return []
  return value.records.filter(isRecord)
}

function pathValue(record: JsonRecord, path: string) {
  return path.split('.').reduce<unknown>((current, segment) => (
    isRecord(current) ? current[segment] : undefined
  ), record)
}

function bindingField(value: unknown) {
  if (typeof value === 'string' && value.trim()) return value.trim()
  if (isRecord(value) && typeof value.field === 'string' && value.field.trim()) return value.field.trim()
  if (isRecord(value) && typeof value.source === 'string' && value.source.trim()) return value.source.trim()
  return ''
}

function bindingName(value: unknown, fallback: string) {
  if (isRecord(value) && typeof value.name === 'string' && value.name.trim()) return value.name.trim()
  return fallback
}

function numericValue(value: unknown) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}

function uniqueValues(values: unknown[]) {
  return [...new Set(values.filter(value => typeof value === 'string' || typeof value === 'number'))]
}

function normalizeProportionProps(props: JsonRecord, records: JsonRecord[], bindings: JsonRecord) {
  const nameField = bindingField(bindings.name || bindings.category)
  const valueField = bindingField(bindings.value)
  if (!nameField || !valueField || !records.length) return

  props.data = records.map(record => ({
    name: String(pathValue(record, nameField) ?? ''),
    value: numericValue(pathValue(record, valueField)),
  }))
}

function normalizeGaugeProps(props: JsonRecord, records: JsonRecord[], bindings: JsonRecord) {
  const valueField = bindingField(bindings.value)
  if (valueField && records.length) {
    props.value = numericValue(pathValue(records[0], valueField))
  }
}

function normalizeCategorySeriesProps(
  props: JsonRecord,
  records: JsonRecord[],
  bindings: JsonRecord,
  seriesKey: 'series' | 'barSeries' | 'lineSeries',
  categoryKey = 'category',
) {
  const categoryField = bindingField(bindings[categoryKey] || bindings.x)
  const seriesBindings = Array.isArray(bindings[seriesKey]) ? bindings[seriesKey] : []
  if (!categoryField || !seriesBindings.length || !records.length) return

  props.categories = records.map(record => pathValue(record, categoryField) as string | number)
  props[seriesKey] = seriesBindings.map((binding, index) => {
    const field = bindingField(binding)
    return {
      name: bindingName(binding, `系列${index + 1}`),
      data: records.map(record => numericValue(pathValue(record, field))),
      ...(isRecord(binding) && binding.color ? { color: binding.color } : {}),
      ...(isRecord(binding) && binding.stack ? { stack: binding.stack } : {}),
      ...(isRecord(binding) && binding.yAxisIndex !== undefined ? { yAxisIndex: binding.yAxisIndex } : {}),
    }
  })
}

function normalizeRadarProps(props: JsonRecord, records: JsonRecord[], bindings: JsonRecord) {
  const indicatorBindings = Array.isArray(bindings.indicators) ? bindings.indicators.filter(isRecord) : []
  if (!indicatorBindings.length || !records.length) return

  props.indicators = indicatorBindings.map((binding, index) => ({
    name: bindingName(binding, `指标${index + 1}`),
    max: numericValue(binding.max) || 100,
  }))
  const seriesNameField = bindingField(bindings.seriesName)
  props.series = records.map((record, index) => ({
    name: seriesNameField
      ? String(pathValue(record, seriesNameField) ?? `系列${index + 1}`)
      : String(record.name ?? `系列${index + 1}`),
    data: indicatorBindings.map(binding => numericValue(pathValue(record, bindingField(binding)))),
  }))
}

function normalizeScatterProps(props: JsonRecord, records: JsonRecord[], bindings: JsonRecord) {
  const xField = bindingField(bindings.x)
  const yField = bindingField(bindings.y)
  if (!xField || !yField || !records.length) return

  const groupField = bindingField(bindings.group)
  if (!groupField) {
    props.series = [{
      name: typeof bindings.name === 'string' ? bindings.name : '数据',
      data: records.map(record => [numericValue(pathValue(record, xField)), numericValue(pathValue(record, yField))]),
    }]
    return
  }

  const groups = new Map<string, Array<[number, number]>>()
  records.forEach(record => {
    const group = String(pathValue(record, groupField) ?? '数据')
    const values = groups.get(group) || []
    values.push([numericValue(pathValue(record, xField)), numericValue(pathValue(record, yField))])
    groups.set(group, values)
  })
  props.series = [...groups.entries()].map(([name, data]) => ({ name, data }))
}

function normalizeHeatmapProps(props: JsonRecord, records: JsonRecord[], bindings: JsonRecord) {
  const xField = bindingField(bindings.x)
  const yField = bindingField(bindings.y)
  const valueField = bindingField(bindings.value)
  if (!xField || !yField || !valueField || !records.length) return

  const xCategories = uniqueValues(records.map(record => pathValue(record, xField)))
  const yCategories = uniqueValues(records.map(record => pathValue(record, yField)))
  const xIndex = new Map(xCategories.map((value, index) => [String(value), index]))
  const yIndex = new Map(yCategories.map((value, index) => [String(value), index]))
  props.xCategories = xCategories
  props.yCategories = yCategories
  props.data = records.map(record => [
    xIndex.get(String(pathValue(record, xField))) ?? 0,
    yIndex.get(String(pathValue(record, yField))) ?? 0,
    numericValue(pathValue(record, valueField)),
  ])
}

export function normalizeChartRendererProps(rendererKey: ChartRendererKey, input: unknown) {
  const source = isRecord(input) ? { ...input } : {}
  const schema = isRecord(source.__runtimeSchema) ? source.__runtimeSchema : {}
  const runtimeData = isRecord(source.__runtimeData) ? source.__runtimeData : {}
  const runtimeState = isRecord(source.__runtimeState) ? source.__runtimeState : {}
  delete source.__runtimeSchema
  delete source.__runtimeData
  delete source.__runtimeState
  delete source.schema
  delete source.datasource
  delete source.bindings

  if (source.loading === undefined && typeof runtimeState.loading === 'boolean') {
    source.loading = runtimeState.loading
  }

  // 允许 direct-json 直接返回已经整理好的图表 props，同时保留 bindings + records 的适配路径。
  Object.keys(runtimeData).forEach((key) => {
    if (CHART_DATA_KEYS.has(key) && source[key] === undefined) {
      source[key] = runtimeData[key]
    }
  })

  const bindings = isRecord(schema.bindings) ? schema.bindings : {}
  const records = recordsFrom(runtimeData)
  if (!Object.keys(bindings).length || !records.length) {
    return source
  }

  if (rendererKey === 'pie-chart-renderer' || rendererKey === 'funnel-chart-renderer') {
    normalizeProportionProps(source, records, bindings)
  } else if (rendererKey === 'gauge-chart-renderer') {
    normalizeGaugeProps(source, records, bindings)
  } else if (rendererKey === 'line-chart-renderer' || rendererKey === 'bar-chart-renderer') {
    normalizeCategorySeriesProps(source, records, bindings, 'series')
  } else if (rendererKey === 'combo-chart-renderer') {
    normalizeCategorySeriesProps(source, records, bindings, 'barSeries')
    normalizeCategorySeriesProps(source, records, bindings, 'lineSeries')
    if (source.categories === undefined) {
      const categoryField = bindingField(bindings.category || bindings.x)
      if (categoryField) {
        source.categories = records.map(record => pathValue(record, categoryField) as string | number)
      }
    }
  } else if (rendererKey === 'radar-chart-renderer') {
    normalizeRadarProps(source, records, bindings)
  } else if (rendererKey === 'scatter-chart-renderer') {
    normalizeScatterProps(source, records, bindings)
  } else if (rendererKey === 'heatmap-chart-renderer') {
    normalizeHeatmapProps(source, records, bindings)
  }

  return source
}

export function createChartPropsNormalizer(rendererKey: ChartRendererKey) {
  return (input: unknown) => normalizeChartRendererProps(rendererKey, input)
}
