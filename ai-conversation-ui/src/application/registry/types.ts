import type { Component } from 'vue'
import type { ApplicationRendererExposure } from './catalog'

export interface ApplicationRendererDefinition<
  Props = Record<string, unknown>,
  ResolveOptions = unknown,
> {
  key: string
  aliases?: readonly string[]
  name: string
  category: string
  version: string
  sourcePath: string
  exposure: ApplicationRendererExposure
  component: Component
  defaultProps?: Partial<Props>
  normalizeProps?: (props: unknown) => Props
  resolveData?: (options: ResolveOptions) => unknown | Promise<unknown>
}
