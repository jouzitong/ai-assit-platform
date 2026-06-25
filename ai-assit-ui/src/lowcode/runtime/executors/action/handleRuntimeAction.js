function isFilterMutation(payload) {
  return payload?.field && payload.field.type !== 'button'
}

export async function handleRuntimeAction(runtime, payload = {}) {
  if (!payload?.action && !payload?.key) {
    return
  }

  if (isFilterMutation(payload)) {
    runtime.setQueryValue(payload.key, payload.value ?? '')
    return
  }

  const action = payload.action || payload.key
  if (action === 'search') {
    runtime.setPage(1)
    await runtime.reload()
    return
  }
  if (action === 'reset' || action === 'clear') {
    runtime.resetQuery()
    runtime.setPage(1)
    await runtime.reload()
    return
  }
  if (action === 'refresh') {
    await runtime.reload()
    return
  }

  runtime.setFeedbackMessage(`已接收动作 ${action}，请在 ActionHandler 中补充业务执行逻辑。`, 'info')
}
