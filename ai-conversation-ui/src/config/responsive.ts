export type ResponsiveViewportFit = 'contain' | 'cover' | 'width' | 'height'

export interface ResponsiveViewportSize {
  width: number
  height: number
}

export interface ResponsiveViewportConfig {
  referenceSize: ResponsiveViewportSize
  minScale: number
  maxScale: number
  fit: ResponsiveViewportFit
}

export type ResponsiveViewportConfigOverrides = Partial<
  Omit<ResponsiveViewportConfig, 'referenceSize'>
> & {
  referenceSize?: Partial<ResponsiveViewportSize>
}

export const DEFAULT_RESPONSIVE_VIEWPORT_CONFIG: Readonly<ResponsiveViewportConfig> = Object.freeze({
  referenceSize: Object.freeze({
    width: 700,
    height: 400,
  }),
  minScale: 0,
  maxScale: 1.4,
  fit: 'contain',
})

const positiveOrFallback = (value: number | undefined, fallback: number) =>
  Number.isFinite(value) && Number(value) > 0 ? Number(value) : fallback

const nonNegativeOrFallback = (value: number | undefined, fallback: number) =>
  Number.isFinite(value) && Number(value) >= 0 ? Number(value) : fallback

export const resolveResponsiveViewportConfig = (
  overrides: ResponsiveViewportConfigOverrides = {},
): ResponsiveViewportConfig => {
  const minScale = nonNegativeOrFallback(
    overrides.minScale,
    DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.minScale,
  )
  const requestedMaxScale = positiveOrFallback(
    overrides.maxScale,
    DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.maxScale,
  )

  return {
    referenceSize: {
      width: positiveOrFallback(
        overrides.referenceSize?.width,
        DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.referenceSize.width,
      ),
      height: positiveOrFallback(
        overrides.referenceSize?.height,
        DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.referenceSize.height,
      ),
    },
    minScale,
    maxScale: Math.max(minScale, requestedMaxScale),
    fit: overrides.fit ?? DEFAULT_RESPONSIVE_VIEWPORT_CONFIG.fit,
  }
}
