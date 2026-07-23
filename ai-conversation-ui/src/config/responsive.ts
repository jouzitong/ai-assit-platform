export type ResponsiveViewportFit = 'contain' | 'cover' | 'width' | 'height'
export type ResponsiveViewportUnderflow = 'scroll' | 'clip'
export type ResponsiveViewportAlign = 'center' | 'start'

export interface ResponsiveViewportSize {
  width: number
  height: number
}

export interface ResponsiveViewportConfig {
  referenceSize: ResponsiveViewportSize
  minScale: number
  maxScale: number
  fit: ResponsiveViewportFit
  underflow: ResponsiveViewportUnderflow
  align: ResponsiveViewportAlign
}

export type ResponsiveViewportConfigOverrides = Partial<
  Omit<ResponsiveViewportConfig, 'referenceSize'>
> & {
  referenceSize?: Partial<ResponsiveViewportSize>
}

export interface ResponsiveViewportPreset {
  extends?: string
  config?: ResponsiveViewportConfigOverrides
}

export interface ResponsiveViewportGlobalOptions {
  defaultPreset: string
  defaults?: ResponsiveViewportConfigOverrides
  presets: Record<string, ResponsiveViewportPreset>
}

export interface ResponsiveViewportOptionsExtension {
  defaultPreset?: string
  defaults?: ResponsiveViewportConfigOverrides
  presets?: Record<string, ResponsiveViewportPreset>
}

export interface ResponsiveViewportResolveOptions {
  preset?: string
  config?: ResponsiveViewportConfigOverrides
  options?: ResponsiveViewportGlobalOptions
}

export interface ResolvedResponsiveViewport {
  preset: string
  config: ResponsiveViewportConfig
}

export const DEFAULT_RESPONSIVE_VIEWPORT_CONFIG: Readonly<ResponsiveViewportConfig> = Object.freeze({
  referenceSize: Object.freeze({
    width: 700,
    height: 400,
  }),
  minScale: 0.5,
  maxScale: 1.4,
  fit: 'contain',
  underflow: 'scroll',
  align: 'center',
})

const copyConfigOverrides = (
  overrides: ResponsiveViewportConfigOverrides = {},
): ResponsiveViewportConfigOverrides => ({
  ...overrides,
  ...(overrides.referenceSize
    ? {
        referenceSize: {
          ...overrides.referenceSize,
        },
      }
    : {}),
})

const freezeConfigOverrides = (
  overrides: ResponsiveViewportConfigOverrides = {},
): Readonly<ResponsiveViewportConfigOverrides> => {
  const copied = copyConfigOverrides(overrides)
  if (copied.referenceSize) {
    copied.referenceSize = Object.freeze({ ...copied.referenceSize })
  }
  return Object.freeze(copied)
}

export const defineResponsiveViewportOptions = (
  options: ResponsiveViewportGlobalOptions,
): ResponsiveViewportGlobalOptions => {
  const presets = Object.fromEntries(
    Object.entries(options.presets).map(([name, preset]) => [
      name,
      Object.freeze({
        ...preset,
        config: freezeConfigOverrides(preset.config),
      }),
    ]),
  )

  return Object.freeze({
    defaultPreset: options.defaultPreset,
    defaults: freezeConfigOverrides(options.defaults),
    presets: Object.freeze(presets),
  })
}

export const RESPONSIVE_VIEWPORT_GLOBAL_OPTIONS = defineResponsiveViewportOptions({
  defaultPreset: 'standard',
  defaults: {},
  presets: {
    standard: {
      config: {},
    },
    interactive: {
      extends: 'standard',
      config: {
        minScale: 0.75,
        maxScale: 1.25,
        underflow: 'scroll',
      },
    },
    preview: {
      extends: 'standard',
      config: {
        minScale: 0.45,
        maxScale: 1,
        underflow: 'clip',
      },
    },
    dashboard: {
      extends: 'interactive',
      config: {
        referenceSize: {
          width: 1200,
          height: 900,
        },
      },
    },
    chatArtifactPreview: {
      extends: 'dashboard',
      config: {
        minScale: 0.2,
        maxScale: 1.5,
        fit: 'contain',
        underflow: 'scroll',
        align: 'center',
      },
    },
    chatDashboard: {
      extends: 'dashboard',
      config: {
        minScale: 0.2,
        maxScale: 1.4,
        fit: 'contain',
        underflow: 'scroll',
        align: 'center',
      },
    },
  },
})

export const mergeResponsiveViewportOverrides = (
  ...layers: Array<ResponsiveViewportConfigOverrides | undefined>
): ResponsiveViewportConfigOverrides => {
  const merged: ResponsiveViewportConfigOverrides = {}
  let referenceSize: Partial<ResponsiveViewportSize> | undefined

  layers.forEach((layer) => {
    if (!layer) {
      return
    }

    Object.entries(layer).forEach(([key, value]) => {
      if (key !== 'referenceSize' && value !== undefined) {
        Object.assign(merged, { [key]: value })
      }
    })

    if (layer.referenceSize) {
      referenceSize = {
        ...referenceSize,
        ...(layer.referenceSize.width !== undefined
          ? { width: layer.referenceSize.width }
          : {}),
        ...(layer.referenceSize.height !== undefined
          ? { height: layer.referenceSize.height }
          : {}),
      }
    }
  })

  if (referenceSize) {
    merged.referenceSize = referenceSize
  }

  return merged
}

export const extendResponsiveViewportOptions = (
  base: ResponsiveViewportGlobalOptions,
  extension: ResponsiveViewportOptionsExtension,
): ResponsiveViewportGlobalOptions => defineResponsiveViewportOptions({
  defaultPreset: extension.defaultPreset ?? base.defaultPreset,
  defaults: mergeResponsiveViewportOverrides(base.defaults, extension.defaults),
  presets: {
    ...base.presets,
    ...extension.presets,
  },
})

const warnResponsiveConfig = (message: string) => {
  if (import.meta.env?.DEV && typeof console !== 'undefined') {
    console.warn(`[ResponsiveViewport] ${message}`)
  }
}

const resolvePresetOverrides = (
  presetName: string,
  options: ResponsiveViewportGlobalOptions,
  chain: string[] = [],
): ResponsiveViewportConfigOverrides => {
  if (chain.includes(presetName)) {
    warnResponsiveConfig(`检测到预设循环继承: ${[...chain, presetName].join(' -> ')}`)
    return {}
  }

  const preset = options.presets[presetName]
  if (!preset) {
    warnResponsiveConfig(`未找到预设 "${presetName}"`)
    return {}
  }

  const parentConfig = preset.extends
    ? resolvePresetOverrides(preset.extends, options, [...chain, presetName])
    : undefined

  return mergeResponsiveViewportOverrides(parentConfig, preset.config)
}

const resolvePresetName = (
  requestedPreset: string | undefined,
  options: ResponsiveViewportGlobalOptions,
) => {
  if (requestedPreset && options.presets[requestedPreset]) {
    return requestedPreset
  }

  if (requestedPreset) {
    warnResponsiveConfig(
      `未找到预设 "${requestedPreset}"，已回退到 "${options.defaultPreset}"`,
    )
  }

  if (options.presets[options.defaultPreset]) {
    return options.defaultPreset
  }

  const firstPreset = Object.keys(options.presets)[0]
  if (firstPreset) {
    warnResponsiveConfig(
      `默认预设 "${options.defaultPreset}" 不存在，已回退到 "${firstPreset}"`,
    )
    return firstPreset
  }

  warnResponsiveConfig('当前全局配置没有可用预设，已直接使用内置默认值')
  return options.defaultPreset
}

const positiveOrFallback = (value: number | undefined, fallback: number) =>
  Number.isFinite(value) && Number(value) > 0 ? Number(value) : fallback

const nonNegativeOrFallback = (value: number | undefined, fallback: number) =>
  Number.isFinite(value) && Number(value) >= 0 ? Number(value) : fallback

const resolveFit = (value: ResponsiveViewportFit | undefined): ResponsiveViewportFit =>
  value === 'cover' || value === 'width' || value === 'height' || value === 'contain'
    ? value
    : DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.fit

const resolveUnderflow = (
  value: ResponsiveViewportUnderflow | undefined,
): ResponsiveViewportUnderflow => value === 'clip' || value === 'scroll'
  ? value
  : DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.underflow

const resolveAlign = (value: ResponsiveViewportAlign | undefined): ResponsiveViewportAlign =>
  value === 'start' || value === 'center'
    ? value
    : DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.align

export const resolveResponsiveViewport = (
  resolveOptions: ResponsiveViewportResolveOptions = {},
): ResolvedResponsiveViewport => {
  const options = resolveOptions.options ?? RESPONSIVE_VIEWPORT_GLOBAL_OPTIONS
  const preset = resolvePresetName(resolveOptions.preset, options)
  const presetConfig = options.presets[preset]
    ? resolvePresetOverrides(preset, options)
    : undefined
  const merged = mergeResponsiveViewportOverrides(
    DEFAULT_RESPONSIVE_VIEWPORT_CONFIG,
    options.defaults,
    presetConfig,
    resolveOptions.config,
  )
  const minScale = nonNegativeOrFallback(
    merged.minScale,
    DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.minScale,
  )
  const requestedMaxScale = positiveOrFallback(
    merged.maxScale,
    DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.maxScale,
  )

  return {
    preset,
    config: Object.freeze({
      referenceSize: Object.freeze({
        width: positiveOrFallback(
          merged.referenceSize?.width,
          DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.referenceSize.width,
        ),
        height: positiveOrFallback(
          merged.referenceSize?.height,
          DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.referenceSize.height,
        ),
      }),
      minScale,
      maxScale: Math.max(minScale, requestedMaxScale),
      fit: resolveFit(merged.fit),
      underflow: resolveUnderflow(merged.underflow),
      align: resolveAlign(merged.align),
    }),
  }
}

const isResolveOptions = (
  input: ResponsiveViewportConfigOverrides | ResponsiveViewportResolveOptions,
): input is ResponsiveViewportResolveOptions => 'preset' in input || 'config' in input || 'options' in input

export const resolveResponsiveViewportConfig = (
  input: ResponsiveViewportConfigOverrides | ResponsiveViewportResolveOptions = {},
): ResponsiveViewportConfig => resolveResponsiveViewport(
  isResolveOptions(input) ? input : { config: input },
).config
