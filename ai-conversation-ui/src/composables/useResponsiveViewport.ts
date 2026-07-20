import { computed, inject, type ComputedRef, type InjectionKey, type Ref } from 'vue'
import type { ResponsiveViewportConfig } from '../config/responsive'

export interface ResponsiveViewportContext {
  containerWidth: Readonly<Ref<number>>
  containerHeight: Readonly<Ref<number>>
  rawScale: Readonly<Ref<number>>
  scale: Readonly<Ref<number>>
  isUnderflow: ComputedRef<boolean>
  isOverflowing: ComputedRef<boolean>
  preset: ComputedRef<string>
  overlayTarget: Readonly<Ref<HTMLElement | null>>
  config: ComputedRef<ResponsiveViewportConfig>
}

export const responsiveViewportKey: InjectionKey<ResponsiveViewportContext> = Symbol(
  'responsive-viewport',
)

export const useResponsiveViewport = () => inject(responsiveViewportKey, null)

export const useResponsiveInteractionScale = () => {
  const viewport = useResponsiveViewport()
  return computed(() => Math.max(viewport?.scale.value || 1, Number.EPSILON))
}

export const useResponsiveOverlayTarget = () => {
  const viewport = useResponsiveViewport()
  return computed(() => viewport?.overlayTarget.value || undefined)
}
