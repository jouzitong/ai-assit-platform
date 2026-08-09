import { ref } from 'vue'
import {
  fetchConversationHistoryPage,
  fetchConversationHistoryWindow,
} from '../api'
import type {
  ChatConversationRound,
  ConversationHistoryPageResponse,
  ConversationHistoryWindowResponse,
} from '../types'

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback
}

function roundKey(round: ChatConversationRound, index: number) {
  return round.round?.roundCode || `anonymous-round-${index}`
}

function mergeRounds(
  left: ChatConversationRound[],
  right: ChatConversationRound[],
) {
  const merged = new Map<string, ChatConversationRound>()
  const candidates = [...left, ...right]
  candidates.forEach((round, index) => {
    const key = roundKey(round, index)
    const current = merged.get(key)
    merged.set(key, current ? { ...current, ...round } : round)
  })
  return [...merged.values()]
}

export function useConversationHistory() {
  const sessionCode = ref('')
  const rounds = ref<ChatConversationRound[]>([])
  const nextCursor = ref<string | null>(null)
  const hasMore = ref(false)
  const isLoading = ref(false)
  const error = ref('')
  let requestSequence = 0

  function reset() {
    requestSequence += 1
    sessionCode.value = ''
    rounds.value = []
    nextCursor.value = null
    hasMore.value = false
    isLoading.value = false
    error.value = ''
  }

  async function loadInitial(targetSessionCode: string, limit = 20) {
    const normalizedSessionCode = targetSessionCode.trim()
    if (!normalizedSessionCode) {
      reset()
      return null
    }

    const sequence = ++requestSequence
    sessionCode.value = normalizedSessionCode
    rounds.value = []
    nextCursor.value = null
    hasMore.value = false
    isLoading.value = true
    error.value = ''

    try {
      const response = await fetchConversationHistoryPage(normalizedSessionCode, undefined, limit)
      if (sequence !== requestSequence || sessionCode.value !== normalizedSessionCode) {
        return null
      }
      rounds.value = Array.isArray(response.rounds) ? response.rounds : []
      nextCursor.value = response.nextCursor || null
      hasMore.value = Boolean(response.hasMore && nextCursor.value)
      return response
    } catch (requestError) {
      if (sequence === requestSequence && sessionCode.value === normalizedSessionCode) {
        error.value = errorMessage(requestError, '会话历史加载失败')
      }
      throw requestError
    } finally {
      if (sequence === requestSequence) {
        isLoading.value = false
      }
    }
  }

  async function loadOlder(targetSessionCode = sessionCode.value) {
    const normalizedSessionCode = targetSessionCode.trim()
    const cursor = nextCursor.value
    if (!normalizedSessionCode || normalizedSessionCode !== sessionCode.value
      || !hasMore.value || !cursor || isLoading.value) {
      return null
    }

    const sequence = ++requestSequence
    isLoading.value = true
    error.value = ''
    try {
      const response = await fetchConversationHistoryPage(normalizedSessionCode, cursor)
      if (sequence !== requestSequence || sessionCode.value !== normalizedSessionCode) {
        return null
      }
      const olderRounds = Array.isArray(response.rounds) ? response.rounds : []
      rounds.value = mergeRounds(olderRounds, rounds.value)
      nextCursor.value = response.nextCursor || null
      hasMore.value = Boolean(response.hasMore && nextCursor.value)
      return response
    } catch (requestError) {
      if (sequence === requestSequence && sessionCode.value === normalizedSessionCode) {
        error.value = errorMessage(requestError, '更早的会话记录加载失败')
      }
      throw requestError
    } finally {
      if (sequence === requestSequence) {
        isLoading.value = false
      }
    }
  }

  async function loadWindow(targetSessionCode: string, aroundRoundCode: string) {
    const normalizedSessionCode = targetSessionCode.trim()
    const normalizedRoundCode = aroundRoundCode.trim()
    if (!normalizedSessionCode || !normalizedRoundCode) {
      return null
    }

    const sequence = ++requestSequence
    sessionCode.value = normalizedSessionCode
    isLoading.value = true
    error.value = ''
    try {
      const response = await fetchConversationHistoryWindow(
        normalizedSessionCode,
        normalizedRoundCode,
      )
      if (sequence !== requestSequence || sessionCode.value !== normalizedSessionCode) {
        return null
      }
      const windowRounds = Array.isArray(response.rounds) ? response.rounds : []
      rounds.value = mergeRounds(windowRounds, rounds.value)
      if (response.hasEarlier && response.beforeCursor) {
        nextCursor.value = response.beforeCursor
        hasMore.value = true
      }
      return response
    } catch (requestError) {
      if (sequence === requestSequence && sessionCode.value === normalizedSessionCode) {
        error.value = errorMessage(requestError, '来源记录加载失败')
      }
      throw requestError
    } finally {
      if (sequence === requestSequence) {
        isLoading.value = false
      }
    }
  }

  return {
    sessionCode,
    rounds,
    nextCursor,
    hasMore,
    isLoading,
    error,
    reset,
    loadInitial,
    loadOlder,
    loadWindow,
  }
}

export type ConversationHistoryPage = ConversationHistoryPageResponse
export type ConversationHistoryWindow = ConversationHistoryWindowResponse
