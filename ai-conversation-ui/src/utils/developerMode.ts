export const DEVELOPER_MODE_STORAGE_KEY = 'ai-conversation-ui-developer-mode'

export function getDeveloperModeEnabled() {
  return window.localStorage.getItem(DEVELOPER_MODE_STORAGE_KEY) === '1'
}

export function setDeveloperModeEnabled(enabled: boolean) {
  window.localStorage.setItem(DEVELOPER_MODE_STORAGE_KEY, enabled ? '1' : '0')
}
