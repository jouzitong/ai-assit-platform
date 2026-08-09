import { computed, reactive, ref } from 'vue'
import {
  clearLongTermMemories,
  correctConversationMemory,
  disableConversationMemory,
  excludeConversationMemoryFromSession,
  fetchConversationContext,
  fetchLongTermMemories,
  forgetConversationMemory,
  promoteConversationMemory,
  restoreConversationMemory,
} from '../api'
import type {
  ChatMemoryContextResponse,
  ChatMemoryCounts,
  ChatMemoryItem,
  ChatMemoryListResponse,
  ChatMemoryOperationResponse,
} from '../types'

export const CLEAR_LONG_TERM_MEMORY_ACTION_KEY = 'long-term:clear'

export function conversationMemoryActionKey(memoryRef: string) {
  return `memory:${memoryRef}`
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback
}

function normalizeItems(items?: ChatMemoryItem[] | null) {
  return Array.isArray(items) ? items : []
}

function normalizeContext(
  response: ChatMemoryContextResponse,
  sessionCode: string,
): ChatMemoryContextResponse {
  return {
    ...response,
    sessionCode: response.sessionCode || sessionCode,
    sessionMemories: normalizeItems(response.sessionMemories),
    longTermMemories: normalizeItems(response.longTermMemories),
    processingMemories: normalizeItems(response.processingMemories),
    disabledMemories: normalizeItems(response.disabledMemories),
  }
}

export function useConversationContext() {
  const context = ref<ChatMemoryContextResponse | null>(null)
  const longTermList = ref<ChatMemoryListResponse | null>(null)
  const currentSessionCode = ref('')
  const isLoading = ref(false)
  const isLoadingLongTerm = ref(false)
  const error = ref('')
  const isOpen = ref(false)
  const actionLoading = reactive<Record<string, boolean>>({})
  let contextRequestSequence = 0
  let longTermRequestSequence = 0

  const counts = computed<ChatMemoryCounts>(() => {
    const responseCounts = context.value?.counts
    return {
      sessionMemories: responseCounts?.sessionMemories
        ?? normalizeItems(context.value?.sessionMemories).length,
      longTermMemories: responseCounts?.longTermMemories
        ?? normalizeItems(context.value?.longTermMemories).length,
      processing: responseCounts?.processing
        ?? normalizeItems(context.value?.processingMemories).length,
      disabled: responseCounts?.disabled
        ?? normalizeItems(context.value?.disabledMemories).length,
    }
  })

  function clear(options: { closeDrawer?: boolean } = {}) {
    contextRequestSequence += 1
    longTermRequestSequence += 1
    currentSessionCode.value = ''
    context.value = null
    longTermList.value = null
    isLoading.value = false
    isLoadingLongTerm.value = false
    error.value = ''
    if (options.closeDrawer !== false) {
      isOpen.value = false
    }
  }

  async function load(sessionCode: string, options: { preserve?: boolean } = {}) {
    const normalizedSessionCode = sessionCode.trim()
    if (!normalizedSessionCode) {
      clear()
      return null
    }

    const sessionChanged = currentSessionCode.value !== normalizedSessionCode
    currentSessionCode.value = normalizedSessionCode
    if (sessionChanged || !options.preserve) {
      context.value = null
    }
    const sequence = ++contextRequestSequence
    isLoading.value = true
    error.value = ''

    try {
      const response = await fetchConversationContext(normalizedSessionCode)
      if (sequence !== contextRequestSequence
        || currentSessionCode.value !== normalizedSessionCode) {
        return null
      }
      context.value = normalizeContext(response, normalizedSessionCode)
      return context.value
    } catch (requestError) {
      if (sequence === contextRequestSequence
        && currentSessionCode.value === normalizedSessionCode) {
        error.value = errorMessage(requestError, '上下文加载失败')
      }
      throw requestError
    } finally {
      if (sequence === contextRequestSequence) {
        isLoading.value = false
      }
    }
  }

  async function loadLongTerm() {
    const sequence = ++longTermRequestSequence
    isLoadingLongTerm.value = true
    try {
      const response = await fetchLongTermMemories()
      if (sequence !== longTermRequestSequence) {
        return null
      }
      longTermList.value = {
        ...response,
        items: normalizeItems(response.items),
        processingItems: normalizeItems(response.processingItems),
      }
      return longTermList.value
    } catch (requestError) {
      if (sequence === longTermRequestSequence) {
        error.value = errorMessage(requestError, '长期记忆加载失败')
      }
      throw requestError
    } finally {
      if (sequence === longTermRequestSequence) {
        isLoadingLongTerm.value = false
      }
    }
  }

  async function refresh() {
    return currentSessionCode.value
      ? load(currentSessionCode.value, { preserve: true })
      : null
  }

  async function open(sessionCode: string) {
    isOpen.value = true
    return load(sessionCode, { preserve: true })
  }

  function close() {
    isOpen.value = false
  }

  async function runAction(
    key: string,
    action: () => Promise<ChatMemoryOperationResponse>,
  ) {
    if (actionLoading[key]) {
      return null
    }
    actionLoading[key] = true
    try {
      const response = await action()
      // The Provider mutation is the operation's source of truth. A follow-up
      // refresh is useful for the UI, but a temporary read failure must not make
      // a successful mutation look like a failed operation to the caller.
      const sessionCodeAtAction = currentSessionCode.value
      try {
        await refresh()
      } catch (refreshError) {
        if (sessionCodeAtAction === currentSessionCode.value) {
          error.value = errorMessage(refreshError, '上下文刷新失败')
        }
      }
      return response
    } finally {
      delete actionLoading[key]
    }
  }

  function disable(memoryRef: string) {
    return runAction(conversationMemoryActionKey(memoryRef), () => (
      disableConversationMemory(memoryRef)
    ))
  }

  function restore(memoryRef: string) {
    return runAction(conversationMemoryActionKey(memoryRef), () => (
      restoreConversationMemory(memoryRef)
    ))
  }

  function correct(memoryRef: string, content: string) {
    return runAction(conversationMemoryActionKey(memoryRef), () => (
      correctConversationMemory(memoryRef, { content, confirmed: true })
    ))
  }

  function promote(memoryRef: string) {
    return runAction(conversationMemoryActionKey(memoryRef), () => (
      promoteConversationMemory(memoryRef, { confirmed: true })
    ))
  }

  function excludeFromSession(memoryRef: string, sessionCode: string) {
    return runAction(conversationMemoryActionKey(memoryRef), () => (
      excludeConversationMemoryFromSession(memoryRef, { sessionCode })
    ))
  }

  function forget(memoryRef: string) {
    return runAction(conversationMemoryActionKey(memoryRef), () => (
      forgetConversationMemory(memoryRef)
    ))
  }

  function clearLongTerm() {
    return runAction(CLEAR_LONG_TERM_MEMORY_ACTION_KEY, () => (
      clearLongTermMemories({ confirmed: true })
    ))
  }

  return {
    context,
    longTermList,
    currentSessionCode,
    counts,
    isLoading,
    isLoadingLongTerm,
    error,
    isOpen,
    actionLoading,
    clear,
    load,
    loadLongTerm,
    refresh,
    open,
    close,
    disable,
    restore,
    correct,
    promote,
    excludeFromSession,
    forget,
    clearLongTerm,
  }
}
