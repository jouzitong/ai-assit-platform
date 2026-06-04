import { ref } from 'vue'
import { sections } from '../data'

const MIN_SIDEBAR_WIDTH = 70
const DEFAULT_SIDEBAR_WIDTH = 296
const MAX_SIDEBAR_WIDTH = 420
const COLLAPSE_THRESHOLD = 120

export function useSystemPage() {
  const sidebarCollapsed = ref(false)
  const sidebarWidth = ref(DEFAULT_SIDEBAR_WIDTH)
  const lastExpandedWidth = ref(DEFAULT_SIDEBAR_WIDTH)
  const isSidebarResizing = ref(false)

  function clampSidebarWidth(value) {
    return Math.min(MAX_SIDEBAR_WIDTH, Math.max(MIN_SIDEBAR_WIDTH, value))
  }

  function setSidebarWidth(value) {
    const nextWidth = clampSidebarWidth(value)
    sidebarWidth.value = nextWidth
    if (nextWidth <= MIN_SIDEBAR_WIDTH + 6) {
      sidebarCollapsed.value = true
    } else if (nextWidth >= COLLAPSE_THRESHOLD) {
      sidebarCollapsed.value = false
    }

    if (!sidebarCollapsed.value) {
      lastExpandedWidth.value = nextWidth
    }
  }

  function toggleSidebar() {
    if (sidebarCollapsed.value) {
      sidebarCollapsed.value = false
      setSidebarWidth(lastExpandedWidth.value)
      return
    }

    lastExpandedWidth.value = sidebarWidth.value
    sidebarCollapsed.value = true
    sidebarWidth.value = MIN_SIDEBAR_WIDTH
  }

  function startSidebarResize(event) {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return
    }

    event.preventDefault()

    isSidebarResizing.value = true

    const startX = event.clientX
    const startWidth = sidebarWidth.value

    const handleMove = moveEvent => {
      setSidebarWidth(startWidth + (moveEvent.clientX - startX))
    }

    const handleUp = () => {
      isSidebarResizing.value = false
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', handleUp)
      window.removeEventListener('pointercancel', handleUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'

    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', handleUp)
    window.addEventListener('pointercancel', handleUp)
  }

  function bindSidebarResizeFromEdge(event) {
    if (event.target?.closest?.('.sidebar-item, .sidebar-toggle, a, button, input, textarea, select')) {
      return
    }

    startSidebarResize(event)
  }

  return {
    sections,
    sidebarCollapsed,
    sidebarWidth,
    isSidebarResizing,
    toggleSidebar,
    setSidebarWidth,
    startSidebarResize,
    bindSidebarResizeFromEdge
  }
}
