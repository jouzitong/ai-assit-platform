export type RendererActionType =
  | 'default'
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'

export interface RendererActionOptions {
  type?: RendererActionType
  style?: Record<string, string | number>
  class?: string
  icon?: string
}

export interface RendererAction {
  key: string
  name: string
  action: string
  options?: RendererActionOptions
}

const ACTION_TYPES = new Set<RendererActionType>([
  'default',
  'primary',
  'success',
  'warning',
  'danger',
  'info',
])
const ACTION_CLASS_PATTERN = /^[A-Za-z_][A-Za-z0-9_-]*(?:\s+[A-Za-z_][A-Za-z0-9_-]*)*$/
const ACTION_ICON_PATTERN = /^[A-Za-z][A-Za-z0-9_-]*$/
const UNSAFE_STYLE_VALUE_PATTERN = /(?:url\s*\(|expression\s*\(|javascript\s*:|data\s*:)/i
const SAFE_ACTION_STYLE_KEYS = new Set([
  'background-color',
  'backgroundColor',
  'border-color',
  'border-radius',
  'borderColor',
  'borderRadius',
  'color',
  'font-size',
  'font-weight',
  'fontSize',
  'fontWeight',
  'height',
  'margin',
  'margin-block',
  'margin-inline',
  'marginBlock',
  'marginInline',
  'max-height',
  'max-width',
  'maxHeight',
  'maxWidth',
  'min-height',
  'min-width',
  'minHeight',
  'minWidth',
  'opacity',
  'padding',
  'padding-block',
  'padding-inline',
  'paddingBlock',
  'paddingInline',
  'width',
])

export function normalizeRendererActions(value: unknown): RendererAction[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!isRecord(item)) return []
    const key = readText(item.key)
    const name = readText(item.name)
    const action = readText(item.action)
    if (!key || !name || !action) return []

    const options = normalizeRendererActionOptions(item.options)
    return [{ key, name, action, ...(options ? { options } : {}) }]
  })
}

function normalizeRendererActionOptions(value: unknown): RendererActionOptions | undefined {
  if (!isRecord(value)) return undefined
  const options: RendererActionOptions = {}
  const type = readText(value.type) as RendererActionType
  if (ACTION_TYPES.has(type)) options.type = type

  const className = readText(value.class)
  if (ACTION_CLASS_PATTERN.test(className)) options.class = className

  const icon = readText(value.icon)
  if (ACTION_ICON_PATTERN.test(icon)) options.icon = icon

  if (isRecord(value.style)) {
    const style = Object.fromEntries(Object.entries(value.style).filter(([key, styleValue]) => (
      SAFE_ACTION_STYLE_KEYS.has(key)
      && (typeof styleValue === 'string' || typeof styleValue === 'number')
      && !UNSAFE_STYLE_VALUE_PATTERN.test(String(styleValue))
    ))) as Record<string, string | number>
    if (Object.keys(style).length) options.style = style
  }

  return Object.keys(options).length ? options : undefined
}

function readText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
