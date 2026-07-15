import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  type CSSProperties,
  type Ref,
} from 'vue'

interface Point {
  x: number
  y: number
}

interface Size {
  width: number
  height: number
}

interface Rect extends Point, Size {}

interface PersistedLayout {
  launcher?: Point
  panel?: Rect
}

type PointerInteraction = {
  type: 'launcher' | 'panel-drag' | 'panel-resize'
  pointerId: number
  startClientX: number
  startClientY: number
  originPoint?: Point
  originRect?: Rect
  moved: boolean
}

const STORAGE_KEY = 'ai-assistant:layout:v1'
const EDGE_GAP = 12
const DEFAULT_LAUNCHER_GAP = 24
const PANEL_DEFAULT_WIDTH = 440
const PANEL_DEFAULT_HEIGHT = 720
const PANEL_MIN_WIDTH = 360
const PANEL_MIN_HEIGHT = 420
const PANEL_MAX_WIDTH = 720
const PANEL_MAX_HEIGHT = 900
const COMPACT_WIDTH = 520
const COMPACT_HEIGHT = 560
const DRAG_THRESHOLD = 5

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), Math.max(min, max))
}

function finitePoint(value: unknown): value is Point {
  if (!value || typeof value !== 'object') return false
  const point = value as Partial<Point>
  return Number.isFinite(point.x) && Number.isFinite(point.y)
}

function finiteRect(value: unknown): value is Rect {
  if (!finitePoint(value)) return false
  const rect = value as Partial<Rect>
  return Number.isFinite(rect.width) && Number.isFinite(rect.height)
    && Number(rect.width) > 0 && Number(rect.height) > 0
}

function readPersistedLayout(): PersistedLayout {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || '{}') as PersistedLayout
    return {
      ...(finitePoint(parsed.launcher) ? { launcher: parsed.launcher } : {}),
      ...(finiteRect(parsed.panel) ? { panel: parsed.panel } : {}),
    }
  }
  catch {
    return {}
  }
}

function writePersistedLayout(layout: PersistedLayout) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(layout))
  }
  catch {
    // Local storage can be unavailable in privacy-restricted browsers.
  }
}

export function useAssistantFloatingLayout(
  hostRef: Ref<HTMLElement | null>,
  launcherRef: Ref<HTMLButtonElement | null>,
) {
  const hostSize = ref<Size>({ width: 0, height: 0 })
  const launcherPosition = ref<Point | null>(null)
  const panelRect = ref<Rect>({
    x: EDGE_GAP,
    y: EDGE_GAP,
    width: PANEL_DEFAULT_WIDTH,
    height: PANEL_DEFAULT_HEIGHT,
  })
  const panelRestoreRect = ref<Rect | null>(null)
  const preferredPanelRect = ref<Rect | null>(null)
  const panelMaximized = ref(false)
  const interaction = ref<PointerInteraction | null>(null)
  const suppressNextLauncherClick = ref(false)
  const initialized = ref(false)
  let resizeObserver: ResizeObserver | null = null

  const isCompact = computed(() => (
    hostSize.value.width > 0
    && (hostSize.value.width < COMPACT_WIDTH || hostSize.value.height < COMPACT_HEIGHT)
  ))

  const panelConstraints = computed(() => {
    const availableWidth = Math.max(1, hostSize.value.width - EDGE_GAP * 2)
    const availableHeight = Math.max(1, hostSize.value.height - EDGE_GAP * 2)
    const maxWidth = Math.min(PANEL_MAX_WIDTH, availableWidth)
    const maxHeight = Math.min(PANEL_MAX_HEIGHT, availableHeight)
    return {
      minWidth: Math.min(PANEL_MIN_WIDTH, maxWidth),
      minHeight: Math.min(PANEL_MIN_HEIGHT, maxHeight),
      maxWidth,
      maxHeight,
    }
  })

  function launcherDimensions() {
    return {
      width: launcherRef.value?.offsetWidth || 52,
      height: launcherRef.value?.offsetHeight || 52,
    }
  }

  function clampLauncher(point: Point): Point {
    const launcher = launcherDimensions()
    return {
      x: clamp(point.x, EDGE_GAP, hostSize.value.width - launcher.width - EDGE_GAP),
      y: clamp(point.y, EDGE_GAP, hostSize.value.height - launcher.height - EDGE_GAP),
    }
  }

  function defaultLauncherPosition(): Point {
    const launcher = launcherDimensions()
    return clampLauncher({
      x: hostSize.value.width - launcher.width - DEFAULT_LAUNCHER_GAP,
      y: hostSize.value.height - launcher.height - DEFAULT_LAUNCHER_GAP,
    })
  }

  function clampPanel(rect: Rect): Rect {
    const constraints = panelConstraints.value
    const width = clamp(rect.width, constraints.minWidth, constraints.maxWidth)
    const height = clamp(rect.height, constraints.minHeight, constraints.maxHeight)
    return {
      x: clamp(rect.x, EDGE_GAP, hostSize.value.width - width - EDGE_GAP),
      y: clamp(rect.y, EDGE_GAP, hostSize.value.height - height - EDGE_GAP),
      width,
      height,
    }
  }

  function panelRectFits(rect: Rect) {
    const constraints = panelConstraints.value
    return rect.width <= constraints.maxWidth
      && rect.height <= constraints.maxHeight
      && rect.x >= EDGE_GAP
      && rect.y >= EDGE_GAP
      && rect.x + rect.width <= hostSize.value.width - EDGE_GAP
      && rect.y + rect.height <= hostSize.value.height - EDGE_GAP
  }

  function defaultPanelRect(): Rect {
    const constraints = panelConstraints.value
    const width = clamp(PANEL_DEFAULT_WIDTH, constraints.minWidth, constraints.maxWidth)
    const height = clamp(PANEL_DEFAULT_HEIGHT, constraints.minHeight, constraints.maxHeight)
    return clampPanel({
      x: hostSize.value.width - width - EDGE_GAP,
      y: EDGE_GAP,
      width,
      height,
    })
  }

  const resolvedPanelRect = computed<Rect>(() => {
    return clampPanel(panelRect.value)
  })

  const launcherStyle = computed<CSSProperties>(() => {
    if (!launcherPosition.value) {
      return { right: 'var(--app-space-6)', bottom: 'var(--app-space-6)' }
    }
    return {
      left: `${launcherPosition.value.x}px`,
      top: `${launcherPosition.value.y}px`,
    }
  })

  const panelStyle = computed<CSSProperties>(() => ({
    left: `${resolvedPanelRect.value.x}px`,
    top: `${resolvedPanelRect.value.y}px`,
    width: `${resolvedPanelRect.value.width}px`,
    height: `${resolvedPanelRect.value.height}px`,
  }))

  const panelSizeLabel = computed(() => (
    `${Math.round(resolvedPanelRect.value.width)} × ${Math.round(resolvedPanelRect.value.height)}`
  ))

  function persistLayout() {
    writePersistedLayout({
      ...(launcherPosition.value ? { launcher: launcherPosition.value } : {}),
      panel: preferredPanelRect.value
        || (panelMaximized.value && panelRestoreRect.value
          ? panelRestoreRect.value
          : panelRect.value),
    })
  }

  function measureHost() {
    const host = hostRef.value
    if (!host) return
    const rect = host.getBoundingClientRect()
    const previousHostSize = hostSize.value
    const wasCompact = previousHostSize.width > 0
      && (previousHostSize.width < COMPACT_WIDTH || previousHostSize.height < COMPACT_HEIGHT)
    const nextCompact = rect.width < COMPACT_WIDTH || rect.height < COMPACT_HEIGHT
    const enteredCompactWidth = previousHostSize.width >= COMPACT_WIDTH && rect.width < COMPACT_WIDTH
    const enteredCompactHeight = previousHostSize.height >= COMPACT_HEIGHT && rect.height < COMPACT_HEIGHT
    hostSize.value = { width: rect.width, height: rect.height }
    if (rect.width <= 0 || rect.height <= 0) return

    if (!initialized.value) {
      const stored = readPersistedLayout()
      launcherPosition.value = clampLauncher(stored.launcher || defaultLauncherPosition())
      const initialPanel = stored.panel || defaultPanelRect()
      if (stored.panel && (nextCompact || !panelRectFits(stored.panel))) {
        preferredPanelRect.value = { ...stored.panel }
      }
      panelRect.value = clampPanel({
        ...initialPanel,
        width: rect.width < COMPACT_WIDTH ? panelConstraints.value.minWidth : initialPanel.width,
        height: rect.height < COMPACT_HEIGHT ? panelConstraints.value.minHeight : initialPanel.height,
      })
      initialized.value = true
      return
    }

    if (interaction.value) finishPointerInteraction()
    if (launcherPosition.value) launcherPosition.value = clampLauncher(launcherPosition.value)
    if (!panelMaximized.value && !preferredPanelRect.value && !panelRectFits(panelRect.value)) {
      preferredPanelRect.value = { ...panelRect.value }
    }
    if (panelMaximized.value) {
      if (wasCompact && !nextCompact && preferredPanelRect.value) {
        panelRestoreRect.value = { ...preferredPanelRect.value }
        preferredPanelRect.value = null
      }
      const constraints = panelConstraints.value
      panelRect.value = {
        x: hostSize.value.width - constraints.maxWidth - EDGE_GAP,
        y: EDGE_GAP,
        width: constraints.maxWidth,
        height: constraints.maxHeight,
      }
    }
    else {
      let nextPanelRect = panelRect.value
      if (enteredCompactWidth || enteredCompactHeight) {
        preferredPanelRect.value ||= { ...panelRect.value }
        nextPanelRect = {
          ...panelRect.value,
          width: enteredCompactWidth ? panelConstraints.value.minWidth : panelRect.value.width,
          height: enteredCompactHeight ? panelConstraints.value.minHeight : panelRect.value.height,
        }
      }
      else if (!nextCompact && preferredPanelRect.value) {
        nextPanelRect = preferredPanelRect.value
        if (panelRectFits(preferredPanelRect.value)) preferredPanelRect.value = null
      }
      panelRect.value = clampPanel(nextPanelRect)
    }
  }

  function ensureLauncherPosition() {
    if (!launcherPosition.value) launcherPosition.value = defaultLauncherPosition()
  }

  function beginPointerInteraction(event: PointerEvent, nextInteraction: PointerInteraction) {
    if (!event.isPrimary || event.button !== 0) return false
    interaction.value = nextInteraction
    const target = event.currentTarget as HTMLElement | null
    target?.setPointerCapture?.(event.pointerId)
    return true
  }

  function startLauncherDrag(event: PointerEvent) {
    ensureLauncherPosition()
    if (!launcherPosition.value) return
    suppressNextLauncherClick.value = false
    beginPointerInteraction(event, {
      type: 'launcher',
      pointerId: event.pointerId,
      startClientX: event.clientX,
      startClientY: event.clientY,
      originPoint: { ...launcherPosition.value },
      moved: false,
    })
  }

  function startPanelDrag(event: PointerEvent) {
    if (panelMaximized.value) return
    beginPointerInteraction(event, {
      type: 'panel-drag',
      pointerId: event.pointerId,
      startClientX: event.clientX,
      startClientY: event.clientY,
      originRect: { ...resolvedPanelRect.value },
      moved: false,
    })
  }

  function startPanelResize(event: PointerEvent) {
    if (panelMaximized.value) return
    beginPointerInteraction(event, {
      type: 'panel-resize',
      pointerId: event.pointerId,
      startClientX: event.clientX,
      startClientY: event.clientY,
      originRect: { ...resolvedPanelRect.value },
      moved: false,
    })
  }

  function handlePointerMove(event: PointerEvent) {
    const current = interaction.value
    if (!current || event.pointerId !== current.pointerId) return
    const deltaX = event.clientX - current.startClientX
    const deltaY = event.clientY - current.startClientY
    if (!current.moved && Math.hypot(deltaX, deltaY) < DRAG_THRESHOLD) return
    current.moved = true
    if (current.type !== 'launcher' && !isCompact.value) preferredPanelRect.value = null
    event.preventDefault()

    if (current.type === 'launcher' && current.originPoint) {
      launcherPosition.value = clampLauncher({
        x: current.originPoint.x + deltaX,
        y: current.originPoint.y + deltaY,
      })
      return
    }

    if (current.type === 'panel-drag' && current.originRect) {
      panelRect.value = clampPanel({
        ...current.originRect,
        x: current.originRect.x + deltaX,
        y: current.originRect.y + deltaY,
      })
      return
    }

    if (current.type === 'panel-resize' && current.originRect) {
      const constraints = panelConstraints.value
      const width = clamp(
        current.originRect.width - deltaX,
        constraints.minWidth,
        constraints.maxWidth,
      )
      const height = clamp(
        current.originRect.height + deltaY,
        constraints.minHeight,
        constraints.maxHeight,
      )
      panelRect.value = clampPanel({
        x: current.originRect.x + current.originRect.width - width,
        y: current.originRect.y,
        width,
        height,
      })
    }
  }

  function finishPointerInteraction(event?: PointerEvent) {
    const current = interaction.value
    if (!current || event && event.pointerId !== current.pointerId) return
    if (current.type === 'launcher' && current.moved) suppressNextLauncherClick.value = true
    if (current.moved) persistLayout()
    interaction.value = null
  }

  function consumeLauncherClick(event: MouseEvent) {
    if (event.detail === 0) {
      suppressNextLauncherClick.value = false
      return false
    }
    if (!suppressNextLauncherClick.value) return false
    suppressNextLauncherClick.value = false
    return true
  }

  function moveLauncherWithKeyboard(event: KeyboardEvent) {
    const deltaByKey: Partial<Record<string, Point>> = {
      ArrowLeft: { x: -12, y: 0 },
      ArrowRight: { x: 12, y: 0 },
      ArrowUp: { x: 0, y: -12 },
      ArrowDown: { x: 0, y: 12 },
    }
    const delta = deltaByKey[event.key]
    if (!delta) return
    event.preventDefault()
    ensureLauncherPosition()
    if (!launcherPosition.value) return
    const scale = event.shiftKey ? 0.25 : 1
    launcherPosition.value = clampLauncher({
      x: launcherPosition.value.x + delta.x * scale,
      y: launcherPosition.value.y + delta.y * scale,
    })
    persistLayout()
  }

  function movePanelWithKeyboard(event: KeyboardEvent) {
    if (panelMaximized.value) return
    const deltaByKey: Partial<Record<string, Point>> = {
      ArrowLeft: { x: -12, y: 0 },
      ArrowRight: { x: 12, y: 0 },
      ArrowUp: { x: 0, y: -12 },
      ArrowDown: { x: 0, y: 12 },
    }
    const delta = deltaByKey[event.key]
    if (!delta) return
    event.preventDefault()
    if (!isCompact.value) preferredPanelRect.value = null
    const scale = event.shiftKey ? 0.25 : 1
    panelRect.value = clampPanel({
      ...resolvedPanelRect.value,
      x: resolvedPanelRect.value.x + delta.x * scale,
      y: resolvedPanelRect.value.y + delta.y * scale,
    })
    persistLayout()
  }

  function resizePanelWithKeyboard(event: KeyboardEvent) {
    if (panelMaximized.value) return
    const deltaByKey: Partial<Record<string, Size>> = {
      ArrowLeft: { width: 12, height: 0 },
      ArrowRight: { width: -12, height: 0 },
      ArrowUp: { width: 0, height: -12 },
      ArrowDown: { width: 0, height: 12 },
    }
    const delta = deltaByKey[event.key]
    if (!delta) return
    event.preventDefault()
    if (!isCompact.value) preferredPanelRect.value = null
    const scale = event.shiftKey ? 0.25 : 1
    const origin = resolvedPanelRect.value
    const constraints = panelConstraints.value
    const width = clamp(origin.width + delta.width * scale, constraints.minWidth, constraints.maxWidth)
    const height = clamp(origin.height + delta.height * scale, constraints.minHeight, constraints.maxHeight)
    panelMaximized.value = false
    panelRect.value = clampPanel({
      x: origin.x + origin.width - width,
      y: origin.y,
      width,
      height,
    })
    persistLayout()
  }

  function togglePanelMaximize() {
    if (panelMaximized.value) {
      const restoreRect = preferredPanelRect.value || panelRestoreRect.value || defaultPanelRect()
      const restoreFits = panelRectFits(restoreRect)
      if (!restoreFits) preferredPanelRect.value ||= { ...restoreRect }
      panelRect.value = clampPanel(isCompact.value
        ? {
            ...restoreRect,
            width: hostSize.value.width < COMPACT_WIDTH
              ? panelConstraints.value.minWidth
              : restoreRect.width,
            height: hostSize.value.height < COMPACT_HEIGHT
              ? panelConstraints.value.minHeight
              : restoreRect.height,
          }
        : restoreRect)
      if (restoreFits) preferredPanelRect.value = null
      panelRestoreRect.value = null
      panelMaximized.value = false
      persistLayout()
      return
    }

    panelRestoreRect.value = { ...resolvedPanelRect.value }
    const constraints = panelConstraints.value
    panelRect.value = {
      x: hostSize.value.width - constraints.maxWidth - EDGE_GAP,
      y: EDGE_GAP,
      width: constraints.maxWidth,
      height: constraints.maxHeight,
    }
    panelMaximized.value = true
  }

  onMounted(async () => {
    await nextTick()
    measureHost()
    if (typeof ResizeObserver !== 'undefined' && hostRef.value) {
      resizeObserver = new ResizeObserver(measureHost)
      resizeObserver.observe(hostRef.value)
    }
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
    interaction.value = null
  })

  return {
    isCompact,
    isLauncherDragging: computed(() => interaction.value?.type === 'launcher' && interaction.value.moved),
    isPanelInteracting: computed(() => Boolean(interaction.value && interaction.value.type !== 'launcher')),
    launcherStyle,
    panelStyle,
    panelSizeLabel,
    panelMaximized,
    startLauncherDrag,
    startPanelDrag,
    startPanelResize,
    handlePointerMove,
    finishPointerInteraction,
    consumeLauncherClick,
    moveLauncherWithKeyboard,
    movePanelWithKeyboard,
    resizePanelWithKeyboard,
    togglePanelMaximize,
  }
}
