import { reactive } from 'vue'

const DEFAULT_DURATION = 2400
const DEFAULT_OFFSET_TOP = '10vh'

const TYPE_META = {
  success: {
    badge: 'OK',
    title: 'Success'
  },
  error: {
    badge: 'X',
    title: 'Error'
  },
  warning: {
    badge: '!',
    title: 'Warning'
  },
  info: {
    badge: 'i',
    title: 'Info'
  }
}

export const popupState = reactive({
  visible: false,
  type: 'success',
  title: TYPE_META.success.title,
  badge: TYPE_META.success.badge,
  message: '',
  offsetTop: DEFAULT_OFFSET_TOP
})

let popupTimer = null

export function showPopup(options, type) {
  const normalized = normalizePopupOptions(options, type)
  const meta = TYPE_META[normalized.type]

  popupState.visible = true
  popupState.type = normalized.type
  popupState.title = normalized.title || meta.title
  popupState.badge = meta.badge
  popupState.message = normalized.message
  popupState.offsetTop = normalized.offsetTop

  clearPopupTimer()
  popupTimer = window.setTimeout(() => {
    popupState.visible = false
  }, normalized.duration)
}

showPopup.success = (message, options = {}) => showPopup({ ...options, message, type: 'success' })
showPopup.error = (message, options = {}) => showPopup({ ...options, message, type: 'error' })
showPopup.warning = (message, options = {}) => showPopup({ ...options, message, type: 'warning' })
showPopup.info = (message, options = {}) => showPopup({ ...options, message, type: 'info' })
showPopup.close = closePopup

export function closePopup() {
  clearPopupTimer()
  popupState.visible = false
}

function clearPopupTimer() {
  if (popupTimer) {
    window.clearTimeout(popupTimer)
    popupTimer = null
  }
}

function normalizePopupOptions(options, type) {
  if (typeof options === 'string') {
    return {
      message: options,
      type: normalizeType(type),
      title: '',
      duration: DEFAULT_DURATION,
      offsetTop: DEFAULT_OFFSET_TOP
    }
  }

  return {
    message: String(options?.message || ''),
    type: normalizeType(options?.type || type),
    title: String(options?.title || ''),
    duration: resolveDuration(options?.duration),
    offsetTop: String(options?.offsetTop || DEFAULT_OFFSET_TOP)
  }
}

function normalizeType(type) {
  if (type === 'warn') {
    return 'warning'
  }
  if (type === 'fail') {
    return 'error'
  }
  return TYPE_META[type] ? type : 'success'
}

function resolveDuration(duration) {
  const parsed = Number(duration)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_DURATION
}
