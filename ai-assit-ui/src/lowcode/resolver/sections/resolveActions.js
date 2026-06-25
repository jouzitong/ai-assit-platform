function normalizeAction(action) {
  return {
    key: String(action?.key || action?.action || ''),
    label: action?.label || action?.name || action?.action || '',
    action: action?.action || action?.key || '',
    func: action?.func || null,
    type: action?.type || '',
    variant: action?.variant || '',
    visible: action?.visible !== false,
    disabled: action?.disabled === true
  }
}

export function resolveActions(actions = []) {
  return Array.isArray(actions) ? actions.map(normalizeAction) : []
}
