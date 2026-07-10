import type { Component } from 'vue'

export interface ApplicationRendererDefinition<Props = Record<string, unknown>> {
  key: string
  aliases?: readonly string[]
  name: string
  category: string
  version: string
  sourcePath: string
  component: Component
  defaultProps?: Partial<Props>
  normalizeProps?: (props: unknown) => Props
  resolveData?: (options: unknown) => unknown | Promise<unknown>
}
