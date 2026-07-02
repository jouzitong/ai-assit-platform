const PENDING_AI_CHAT_DRAFT_KEY = 'emp-console:pending-ai-chat-draft'

export function savePendingAiChatDraft(draft) {
  if (!draft?.sessionCode || !draft?.message) {
    return
  }

  window.sessionStorage.setItem(PENDING_AI_CHAT_DRAFT_KEY, JSON.stringify({
    sessionCode: String(draft.sessionCode).trim(),
    message: String(draft.message).trim(),
    apiModel: draft.apiModel ? String(draft.apiModel).trim() : '',
    createdAt: Date.now()
  }))
}

export function readPendingAiChatDraft() {
  try {
    const raw = window.sessionStorage.getItem(PENDING_AI_CHAT_DRAFT_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw)
    if (!parsed?.sessionCode || !parsed?.message) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export function consumePendingAiChatDraft(sessionCode) {
  const draft = readPendingAiChatDraft()
  if (!draft) {
    return null
  }
  if (sessionCode && String(draft.sessionCode).trim() !== String(sessionCode).trim()) {
    return null
  }
  clearPendingAiChatDraft()
  return draft
}

export function clearPendingAiChatDraft() {
  window.sessionStorage.removeItem(PENDING_AI_CHAT_DRAFT_KEY)
}
