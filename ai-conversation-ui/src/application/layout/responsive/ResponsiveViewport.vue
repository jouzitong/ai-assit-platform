<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  provide,
  readonly,
  ref,
  watch,
  type CSSProperties,
} from 'vue'
import {
  resolveResponsiveViewport,
  type ResponsiveViewportConfigOverrides,
  type ResponsiveViewportFit,
  type ResponsiveViewportGlobalOptions,
} from '../../../config/responsive'
import { responsiveViewportKey } from '../../../composables/useResponsiveViewport'

interface Props {
  preset?: string
  config?: ResponsiveViewportConfigOverrides
  options?: ResponsiveViewportGlobalOptions
  scaleMultiplier?: number
}

const props = withDefaults(defineProps<Props>(), {
  config: () => ({}),
  scaleMultiplier: 1,
})

const emit = defineEmits<{
  'scale-change': [payload: { scale: number; rawScale: number; multiplier: number }]
}>()

const viewportRef = ref<HTMLElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)
const overlayRef = ref<HTMLElement | null>(null)
const containerWidth = ref(0)
const containerHeight = ref(0)
const contentHeight = ref(0)
const rawScale = ref(0)
const scale = ref(0)
const resolvedViewport = computed(() => resolveResponsiveViewport({
  preset: props.preset,
  config: props.config,
  options: props.options,
}))
const resolvedConfig = computed(() => resolvedViewport.value.config)
const resolvedPreset = computed(() => resolvedViewport.value.preset)
const isUnderflow = computed(() => (
  rawScale.value > 0 && rawScale.value < resolvedConfig.value.minScale
))
const isOverflowing = computed(() => (
  scale.value > 0
  && (
    resolvedConfig.value.referenceSize.width * scale.value > containerWidth.value + 0.5
    || contentHeight.value * scale.value > containerHeight.value + 0.5
  )
))

let resizeObserver: ResizeObserver | null = null
let contentMutationObserver: MutationObserver | null = null
let contentMeasureFrame: number | null = null

const calculateRawScale = (
  widthRatio: number,
  heightRatio: number,
  fit: ResponsiveViewportFit,
) => {
  if (fit === 'cover') {
    return Math.max(widthRatio, heightRatio)
  }
  if (fit === 'width') {
    return widthRatio
  }
  if (fit === 'height') {
    return heightRatio
  }
  return Math.min(widthRatio, heightRatio)
}

const updateScale = (width: number, height: number) => {
  containerWidth.value = Math.max(0, width)
  containerHeight.value = Math.max(0, height)

  if (width <= 0 || height <= 0) {
    rawScale.value = 0
    scale.value = 0
    return
  }

  const config = resolvedConfig.value
  const nextRawScale = calculateRawScale(
    width / config.referenceSize.width,
    height / config.referenceSize.height,
    config.fit,
  )

  rawScale.value = nextRawScale
  const multiplier = Number.isFinite(props.scaleMultiplier) && props.scaleMultiplier > 0
    ? props.scaleMultiplier
    : 1
  const requestedScale = nextRawScale * multiplier
  scale.value = Math.min(config.maxScale, Math.max(config.minScale, requestedScale))
}

const measureViewport = () => {
  const viewport = viewportRef.value
  if (!viewport) {
    return
  }
  const rect = viewport.getBoundingClientRect()
  updateScale(rect.width, rect.height)
}

const measureContent = () => {
  const content = contentRef.value
  if (!content) {
    return
  }

  const nextHeight = Math.max(
    content.scrollHeight,
    Math.ceil(content.getBoundingClientRect().height),
  )
  if (nextHeight > 0 && Math.abs(nextHeight - contentHeight.value) > 0.5) {
    contentHeight.value = nextHeight
  }
}

const scheduleContentMeasure = () => {
  if (contentMeasureFrame !== null) {
    return
  }
  contentMeasureFrame = requestAnimationFrame(() => {
    contentMeasureFrame = null
    measureContent()
  })
}

const stageStyle = computed<CSSProperties>(() => ({
  width: `${resolvedConfig.value.referenceSize.width * scale.value}px`,
  height: `${resolvedConfig.value.referenceSize.height * scale.value}px`,
}))

const canvasStyle = computed<CSSProperties>(() => ({
  width: `${resolvedConfig.value.referenceSize.width}px`,
  height: `${resolvedConfig.value.referenceSize.height}px`,
  transform: `scale(${scale.value})`,
}))

const viewportStyle = computed<CSSProperties>(() => ({
  '--responsive-viewport-scale': String(scale.value),
  '--responsive-viewport-raw-scale': String(rawScale.value),
  '--responsive-viewport-width': `${containerWidth.value}px`,
  '--responsive-viewport-height': `${containerHeight.value}px`,
  aspectRatio: `${resolvedConfig.value.referenceSize.width} / ${resolvedConfig.value.referenceSize.height}`,
} as CSSProperties))

provide(responsiveViewportKey, {
  containerWidth: readonly(containerWidth),
  containerHeight: readonly(containerHeight),
  rawScale: readonly(rawScale),
  scale: readonly(scale),
  isUnderflow,
  isOverflowing,
  preset: resolvedPreset,
  overlayTarget: readonly(overlayRef),
  config: resolvedConfig,
})

watch([resolvedConfig, () => props.scaleMultiplier], () => {
  contentHeight.value = 0
  measureViewport()
  void nextTick(scheduleContentMeasure)
}, { deep: true })
watch(scale, (value) => {
  emit('scale-change', {
    scale: value,
    rawScale: rawScale.value,
    multiplier: props.scaleMultiplier,
  })
}, { immediate: true })

onMounted(() => {
  measureViewport()
  void nextTick(scheduleContentMeasure)
  if (typeof ResizeObserver !== 'undefined' && viewportRef.value) {
    resizeObserver = new ResizeObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.target === viewportRef.value) {
          updateScale(entry.contentRect.width, entry.contentRect.height)
        }
      })
      scheduleContentMeasure()
    })
    resizeObserver.observe(viewportRef.value)
    if (contentRef.value) {
      resizeObserver.observe(contentRef.value)
    }
  }
  if (typeof MutationObserver !== 'undefined' && contentRef.value) {
    contentMutationObserver = new MutationObserver(scheduleContentMeasure)
    contentMutationObserver.observe(contentRef.value, {
      childList: true,
      subtree: true,
      attributes: true,
    })
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  contentMutationObserver?.disconnect()
  contentMutationObserver = null
  if (contentMeasureFrame !== null) {
    cancelAnimationFrame(contentMeasureFrame)
    contentMeasureFrame = null
  }
})
</script>

<template>
  <div
    ref="viewportRef"
    class="responsive-viewport"
    :style="viewportStyle"
    :data-preset="resolvedPreset"
    :data-fit="resolvedConfig.fit"
    :data-align="resolvedConfig.align"
    :data-underflow="resolvedConfig.underflow"
    :data-underflow-active="isUnderflow"
    :data-overflow-active="isOverflowing"
    :data-scale="scale"
  >
    <div class="responsive-viewport__stage" :style="stageStyle">
      <div class="responsive-viewport__canvas" :style="canvasStyle">
        <div ref="contentRef" class="responsive-viewport__content">
          <slot
            :container-width="containerWidth"
            :container-height="containerHeight"
            :raw-scale="rawScale"
            :scale="scale"
            :is-underflow="isUnderflow"
            :is-overflowing="isOverflowing"
            :preset="resolvedPreset"
            :config="resolvedConfig"
          />
        </div>
        <div ref="overlayRef" class="responsive-viewport__overlay" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.responsive-viewport {
  display: grid;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  place-items: center;
  container-type: size;
}

.responsive-viewport[data-align='start'] {
  place-items: start;
}

.responsive-viewport[data-underflow='scroll'][data-underflow-active='true'],
.responsive-viewport[data-underflow='scroll'][data-overflow-active='true'] {
  overflow: auto;
  place-items: start;
}

.responsive-viewport__stage {
  position: relative;
  flex: none;
}

.responsive-viewport__canvas {
  position: absolute;
  inset: 0 auto auto 0;
  overflow: visible;
  transform-origin: top left;
}

.responsive-viewport__content {
  width: 100%;
  height: 100%;
  min-height: 100%;
}

.responsive-viewport__overlay {
  position: absolute;
  inset: 0;
  z-index: 2000;
  pointer-events: none;
}

.responsive-viewport__overlay :deep(.el-popper),
.responsive-viewport__overlay :deep(.el-overlay),
.responsive-viewport__overlay :deep([role='dialog']),
.responsive-viewport__overlay :deep([role='tooltip']) {
  pointer-events: auto;
}
</style>
