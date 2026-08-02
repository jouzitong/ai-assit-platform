import type { EChartsOption, SeriesOption } from 'echarts'

export type ChartValue = string | number

export type BaseChartSeries = {
  name: string
  data: number[]
  color?: string
}

export type ProportionChartData = {
  name: string
  value: number
  color?: string
}

export type PieDataItem = ProportionChartData
export type PieChartData = ProportionChartData
export type FunnelChartData = ProportionChartData

export type LineChartSeries = BaseChartSeries & {
  smooth?: boolean
  area?: boolean
  showSymbol?: boolean
  lineWidth?: number
  symbolSize?: number
  stack?: string
  yAxisIndex?: number
}

export type ComboBarSeries = BaseChartSeries & {
  stack?: string
  barMaxWidth?: number
  borderRadius?: number | number[]
  yAxisIndex?: number
}

export type ComboLineSeries = LineChartSeries

export type RadarIndicator = {
  name: string
  max: number
}

export type RadarSeries = BaseChartSeries & {
  opacity?: number
  lineWidth?: number
  symbol?: string
  symbolSize?: number
}

export type BarChartSeries = ComboBarSeries

export type ScatterPoint =
  | [number, number]
  | {
      name?: string
      value: [number, number]
    }

export type ScatterSeries = {
  name: string
  data: ScatterPoint[]
  color?: string
  symbol?: string
  symbolSize?: number
}

export type HeatmapPoint =
  | [number, number, number]
  | {
      name?: string
      value: [number, number, number]
    }

export type BaseChartProps = {
  option?: EChartsOption
  height?: number | string
  width?: number | string
  loading?: boolean
  emptyText?: string
}

export type SeriesList<T extends SeriesOption = SeriesOption> = T[]
