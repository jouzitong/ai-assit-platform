<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ScrollMinimapAnchor } from './types'

const props = withDefaults(defineProps<{
  scrollTarget?: HTMLElement | null
  anchors?: ScrollMinimapAnchor[]
  side?: 'left' | 'right'
  interactive?: boolean
  minThumbHeight?: number
  maxGeneratedMarks?: number
}>(), {
  scrollTarget: null,
  anchors: () => [],
  side: 'left',
  interactive: false,
  minThumbHeight: 22,
  maxGeneratedMarks: 28,
})

const scrollTop = ref(0)
const scrollHeight = ref(1)
const clientHeight = ref(1)
const hostHeight = ref(1)
const hostRef = ref<HTMLElement | null>(null)
let resizeObserver: ResizeObserver | null = null
let animationFrame = 0

const canScroll = computed(() => scrollHeight.value > clientHeight.value + 1)

const scrollRange = computed(() => Math.max(1, scrollHeight.value - clientHeight.value))

const thumbHeight = computed(() => {
  if (!canScroll.value) {
    return 0
  }
  const ratio = clientHeight.value / scrollHeight.value
  return Math.max(props.minThumbHeight, Math.round(hostHeight.value * ratio))
})

const thumbTop = computed(() => {
  if (!canScroll.value) {
    return 0
  }
  const maxTop = Math.max(0, hostHeight.value - thumbHeight.value)
  return Math.round((scrollTop.value / scrollRange.value) * maxTop)
})

const generatedMarks = computed(() => {
  if (!canScroll.value || props.anchors.length > 0) {
    return []
  }
  const count = Math.min(
    props.maxGeneratedMarks,
    Math.max(8, Math.ceil(scrollHeight.value / Math.max(clientHeight.value / 2, 120))),
  )
  return Array.from({ length: count }, (_, index) => ({
    id: `generated-${index}`,
    top: count === 1 ? 0 : index / (count - 1),
    kind: 'default' as const,
  }))
})

const marks = computed(() => {
  if (props.anchors.length === 0) {
    return generatedMarks.value
  }
  return props.anchors.map((anchor) => {
    const anchorTop = Math.min(Math.max(anchor.top / scrollHeight.value, 0), 1)
    const anchorBottom = Math.min(Math.max((anchor.top + (anchor.height || 0)) / scrollHeight.value, 0), 1)
    const viewportTop = scrollTop.value / scrollHeight.value
    const viewportBottom = (scrollTop.value + clientHeight.value) / scrollHeight.value
    const isVisible = anchorBottom >= viewportTop && anchorTop <= viewportBottom
    return {
      id: anchor.id,
      label: anchor.label,
      top: anchorTop,
      kind: isVisible ? 'current' as const : anchor.kind || 'default' as const,
    }
  })
})

const rootStyle = computed(() => ({
  [`--app-scroll-minimap-thumb-top`]: `${thumbTop.value}px`,
  [`--app-scroll-minimap-thumb-height`]: `${thumbHeight.value}px`,
}))

const resolveMarkStyle = (top: number) => ({
  top: `${Math.round(top * hostHeight.value)}px`,
})

const updateMetrics = () => {
  if (animationFrame) {
    cancelAnimationFrame(animationFrame)
  }
  animationFrame = requestAnimationFrame(() => {
    const target = props.scrollTarget
    const host = hostRef.value
    if (!target || !host) {
      return
    }
    scrollTop.value = target.scrollTop
    scrollHeight.value = Math.max(1, target.scrollHeight)
    clientHeight.value = Math.max(1, target.clientHeight)
    hostHeight.value = Math.max(1, host.clientHeight)
  })
}

const bindTarget = (target: HTMLElement | null) => {
  resizeObserver?.disconnect()
  resizeObserver = null

  if (!target) {
    return
  }

  target.addEventListener('scroll', updateMetrics, { passive: true })
  resizeObserver = new ResizeObserver(updateMetrics)
  resizeObserver.observe(target)
  if (hostRef.value) {
    resizeObserver.observe(hostRef.value)
  }
  void nextTick(updateMetrics)
}

const unbindTarget = (target: HTMLElement | null) => {
  target?.removeEventListener('scroll', updateMetrics)
}

const handlePointerDown = (event: PointerEvent) => {
  if (!props.interactive || !props.scrollTarget || !hostRef.value || !canScroll.value) {
    return
  }
  const rect = hostRef.value.getBoundingClientRect()
  const ratio = Math.min(Math.max((event.clientY - rect.top) / rect.height, 0), 1)
  props.scrollTarget.scrollTo({
    top: ratio * scrollRange.value,
    behavior: 'smooth',
  })
}

watch(() => props.scrollTarget, (target, previousTarget) => {
  unbindTarget(previousTarget || null)
  bindTarget(target || null)
}, { flush: 'post' })

onMounted(() => {
  bindTarget(props.scrollTarget)
  window.addEventListener('resize', updateMetrics)
})

onBeforeUnmount(() => {
  unbindTarget(props.scrollTarget)
  resizeObserver?.disconnect()
  if (animationFrame) {
    cancelAnimationFrame(animationFrame)
  }
  window.removeEventListener('resize', updateMetrics)
})
</script>

<template>
  <div
    v-show="canScroll"
    ref="hostRef"
    :class="['app-scroll-minimap', `is-${side}`, { 'is-interactive': interactive }]"
    :style="rootStyle"
    aria-hidden="true"
    @pointerdown="handlePointerDown"
  >
    <span
      v-for="mark in marks"
      :key="mark.id"
      :class="['app-scroll-minimap__mark', `is-${mark.kind}`]"
      :style="resolveMarkStyle(mark.top)"
    ></span>
    <span class="app-scroll-minimap__thumb"></span>
  </div>
</template>

<style scoped>
.app-scroll-minimap {
  position: absolute;
  top: 14px;
  bottom: 14px;
  z-index: 5;
  width: 34px;
  pointer-events: none;
  user-select: none;
}

.app-scroll-minimap.is-left {
  left: 12px;
}

.app-scroll-minimap.is-right {
  right: 12px;
}

.app-scroll-minimap.is-interactive {
  pointer-events: auto;
  cursor: pointer;
}

.app-scroll-minimap__mark {
  position: absolute;
  left: 0;
  width: 9px;
  height: 1px;
  border-radius: var(--app-radius-round);
  background: color-mix(in srgb, var(--chat-text-muted) 58%, transparent);
  transform: translateY(-50%);
}

.app-scroll-minimap.is-right .app-scroll-minimap__mark {
  right: 0;
  left: auto;
}

.app-scroll-minimap__mark.is-current {
  width: 15px;
  background: color-mix(in srgb, var(--chat-text-secondary) 82%, transparent);
}

.app-scroll-minimap__mark.is-muted {
  width: 6px;
  opacity: 0.55;
}

.app-scroll-minimap__thumb {
  position: absolute;
  top: var(--app-scroll-minimap-thumb-top);
  left: 0;
  width: 28px;
  height: var(--app-scroll-minimap-thumb-height);
  min-height: 1px;
  border-radius: var(--app-radius-round);
  border-left: 2px solid var(--chat-text-secondary);
}

.app-scroll-minimap.is-right .app-scroll-minimap__thumb {
  right: 0;
  left: auto;
  border-right: 2px solid var(--chat-text-secondary);
  border-left: 0;
}

@media (prefers-reduced-motion: no-preference) {
  .app-scroll-minimap__thumb {
    transition:
      top 0.12s ease,
      height 0.12s ease;
  }
}
</style>
