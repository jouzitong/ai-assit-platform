import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  barSeries,
  deleteAssistantConversation,
  fetchAssistantConversationDetail,
  fetchAssistantConversationList,
  fetchAssistantModels,
  initialExecutions,
  initialHistoryList,
  initialStages,
  models as defaultModels,
  pinAssistantConversation,
  placeholder,
  pieSegments,
  queryAssistantConversationStream,
  renameAssistantConversation,
  resultRows
} from '../data'
import { USER_STORAGE_KEY } from '../../../../../utils/session'
import { clearPendingAiChatDraft, consumePendingAiChatDraft } from '../../../../../utils/aiChatDraft'

function buildPieBackground(segments) {
  const total = segments.reduce((sum, item) => sum + item.value, 0)
  if (total <= 0) return 'none'

  let start = 0
  const parts = segments.map((item) => {
    const end = start + item.value
    const part = `${item.color} ${start}% ${end}%`
    start = end
    return part
  })
  return `conic-gradient(${parts.join(', ')})`
}

function normalizeText(text) {
  return String(text || '')
    .replace(/\s+/g, ' ')
    .trim()
}

function shortenText(text, limit) {
  const value = normalizeText(text)
  if (value.length <= limit) return value
  return `${value.slice(0, limit).trimEnd()}…`
}

function buildConversationTitle(text) {
  const value = normalizeText(text)
  if (!value) return '未命名会话'

  const prefixes = [
    '请帮我',
    '帮我',
    '麻烦帮我',
    '帮忙',
    '请',
    '分析一下',
    '请分析',
    '帮我分析',
    '帮我看看',
    '看看',
    '帮我查',
    '帮我找',
    '帮我对比',
    '帮我复盘',
    '帮我生成'
  ]

  let candidate = value
  const matchedPrefix = prefixes.find((prefix) => candidate.startsWith(prefix))
  if (matchedPrefix) {
    candidate = candidate.slice(matchedPrefix.length).trim()
  }

  candidate = candidate.split(/[，。！？!?；;\n]/)[0].trim()
  candidate = candidate.replace(/^(一下|一下子|一下儿)/, '').trim()

  return shortenText(candidate || value, 18)
}

function buildConversationSummary(text) {
  const value = shortenText(text, 36)
  return value ? `最近输入：${value}` : '等待首个问题'
}

function buildConversationTag(text) {
  const value = normalizeText(text)
  const rules = [
    { keyword: ['人力成本', '成本'], label: '人力成本' },
    { keyword: ['考勤', '打卡', '补贴', '夜班'], label: '考勤补贴' },
    { keyword: ['绩效'], label: '绩效分析' },
    { keyword: ['销售', '提成'], label: '销售提成' },
    { keyword: ['研发', '研发中心'], label: '研发组织' },
    { keyword: ['离职', '流失'], label: '人效观察' }
  ]

  const matched = rules.find((rule) => rule.keyword.some((keyword) => value.includes(keyword)))
  return matched?.label || '智能问数'
}

function parseStoredUserId() {
  try {
    const raw = window.localStorage.getItem(USER_STORAGE_KEY)
    if (!raw) {
      return 0
    }
    const user = JSON.parse(raw)
    const value = Number(user?.id ?? user?.userId ?? 0)
    return Number.isFinite(value) ? value : 0
  } catch {
    return 0
  }
}

function formatHistoryTime(value) {
  if (!value) {
    return '刚刚'
  }

  const text = String(value)
  if (text.includes('刚刚') || text.includes('今天') || text.includes('昨天')) {
    return text
  }

  return text.slice(0, 16)
}

function mapSessionToHistoryItem(session, activeSessionCode) {
  const sessionCode = session?.sessionCode
  return {
    id: sessionCode,
    title: session?.sessionName || '未命名会话',
    summary: '这个是分析摘要汇总',
    tag: session?.businessType || '智能问数',
    time: formatHistoryTime(session?.updateTime || session?.createTime),
    active: sessionCode === activeSessionCode,
    pinned: Boolean(session?.pinned)
  }
}

function buildLocalFallbackHistory() {
  return initialHistoryList.map((item) => ({ ...item }))
}

function normalizeModelOptions(items) {
  return items
    .map((item) => {
      const value = item?.apiModel || item?.modelCode || item?.id
      if (!value) {
        return null
      }
      const providerName = normalizeText(item?.providerName)
      const apiModel = normalizeText(item?.apiModel)
      const composedLabel = providerName && apiModel
        ? `${providerName}-${apiModel}`
        : providerName || apiModel
      return {
        value,
        label: composedLabel || item?.modelName || item?.modelCode || value
      }
    })
    .filter(Boolean)
}

function firstConversationCode(historyList) {
  return historyList.find((item) => item.active)?.id || historyList[0]?.id || ''
}

function unwrapResponse(payload) {
  return payload?.data ?? payload
}

function normalizeConversationDetail(payload) {
  const detail = payload && typeof payload === 'object' ? payload : {}
  const rounds = Array.isArray(detail.rounds) ? detail.rounds : []
  return {
    session: detail.session || null,
    rounds: rounds.map((item) => ({
      round: item?.round || null,
      messages: Array.isArray(item?.messages) ? item.messages : [],
      artifacts: Array.isArray(item?.artifacts) ? item.artifacts : [],
      renderType: item?.renderType || '',
      // TODO: 后续基于 round.artifacts 识别 render json 等业务产物，并切换对应渲染组件。
      pendingArtifactRenderer: Array.isArray(item?.artifacts) && item.artifacts.length > 0
    }))
  }
}

function sortByNumberField(items, field) {
  return [...items].sort((a, b) => {
    const left = Number(a?.[field] ?? 0)
    const right = Number(b?.[field] ?? 0)
    return left - right
  })
}

function formatMessageContent(content) {
  const text = normalizeText(content)
  if (!text) {
    return ''
  }
  return text.length > 240 ? `${text.slice(0, 240).trimEnd()}…` : text
}

function buildExecutionTimeline(roundDetail) {
  if (!roundDetail) {
    return initialExecutions.map((item) => ({ ...item }))
  }

  const messages = sortByNumberField(roundDetail.messages || [], 'sortNo')
  const artifacts = sortByNumberField(roundDetail.artifacts || [], 'seqNo')
  const items = []

  for (const message of messages) {
    const content = formatMessageContent(message?.content)
    if (!content) {
      continue
    }
    if (String(message?.role || '').toUpperCase() === 'USER') {
      continue
    }
    items.push({
      title: message?.messageType || '消息',
      detail: content,
      tone: String(message?.status || '').toUpperCase() === 'FAILED' ? 'warning' : 'info',
      active: false
    })
  }

  for (const artifact of artifacts) {
    const summary = artifact?.title || artifact?.artifactType || '产物'
    items.push({
      title: '产物',
      detail: `${summary} 待接入业务渲染`,
      tone: 'success',
      active: false
    })
  }

  return items.length > 0 ? items : initialExecutions.map((item) => ({ ...item }))
}

function buildArtifactStages(roundDetail) {
  if (!roundDetail || !Array.isArray(roundDetail.artifacts) || roundDetail.artifacts.length === 0) {
    return initialStages.map((item) => ({ ...item }))
  }

  return sortByNumberField(roundDetail.artifacts, 'seqNo').map((artifact) => ({
    name: artifact?.title || artifact?.artifactType || '产物',
    desc: artifact?.renderType
      ? `检测到 ${artifact.renderType} 产物，前端业务渲染待实现`
      : '检测到结构化产物，前端业务渲染待实现',
    status: String(artifact?.status || '').toUpperCase() === 'FAILED' ? 'pending' : 'done'
  }))
}

function resolveActiveRoundDetail(detail) {
  const rounds = Array.isArray(detail?.rounds) ? detail.rounds : []
  if (rounds.length === 0) {
    return null
  }
  return rounds[rounds.length - 1] || null
}

function resolveRoundUserInput(roundDetail, session) {
  const messages = sortByNumberField(roundDetail?.messages || [], 'sortNo')
  const userMessage = messages.find((item) => String(item?.role || '').toUpperCase() === 'USER')
  if (userMessage?.content) {
    return normalizeText(userMessage.content)
  }
  return extractUserInput(session?.summary || '')
}

function resolveRoundSummary(roundDetail, session) {
  const messages = sortByNumberField(roundDetail?.messages || [], 'sortNo')
  const assistantMessages = messages.filter((item) => String(item?.role || '').toUpperCase() !== 'USER')
  const finalMessage = [...assistantMessages].reverse().find((item) => formatMessageContent(item?.content))
  const text = formatMessageContent(finalMessage?.content)
  if (text) {
    return text
  }
  return session?.summary || '系统正在整理查询结论。'
}

function buildArtifactNotice(roundDetail) {
  const artifacts = Array.isArray(roundDetail?.artifacts) ? roundDetail.artifacts : []
  if (artifacts.length === 0) {
    return ''
  }
  const renderArtifact = artifacts.find((item) => item?.renderType)
  if (renderArtifact?.renderType) {
    return `检测到 ${renderArtifact.renderType} 产物，前端业务渲染待实现`
  }
  return `检测到 ${artifacts.length} 个产物，前端业务渲染待实现`
}

function resolveRouteSessionCode(route) {
  return typeof route.params?.sessionCode === 'string' ? route.params.sessionCode.trim() : ''
}

function parseSseEventBlock(block) {
  const lines = String(block || '')
    .split('\n')
    .map((line) => line.trimEnd())
    .filter(Boolean)

  const event = { eventType: '', data: '' }
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event.eventType = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      const dataLine = line.slice('data:'.length).trim()
      event.data = event.data ? `${event.data}\n${dataLine}` : dataLine
    }
  }
  return event
}

async function consumeSseResponse(response, handlers) {
  if (!response?.body) {
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        if (buffer.trim()) {
          handlers.onEvent?.(parseSseEventBlock(buffer))
        }
        break
      }

      buffer += decoder.decode(value, { stream: true })

      let splitIndex = buffer.indexOf('\n\n')
      while (splitIndex !== -1) {
        const block = buffer.slice(0, splitIndex)
        buffer = buffer.slice(splitIndex + 2)
        if (block.trim()) {
          handlers.onEvent?.(parseSseEventBlock(block))
        }
        splitIndex = buffer.indexOf('\n\n')
      }
    }
  } finally {
    reader.releaseLock?.()
  }
}

export function useHomeOverviewPage() {
  const route = useRoute()
  const router = useRouter()
  const models = ref(defaultModels.map((item) => ({ ...item })))
  const selectedModel = ref(models.value[0]?.value || '')
  const prompt = ref('')
  const executions = ref(initialExecutions.map((item) => ({ ...item })))
  const stages = ref(initialStages.map((item) => ({ ...item })))
  const historyCollapsed = ref(false)
  const previewFullscreen = ref(false)
  const historyKeyword = ref('')
  const historyList = ref([])
  const historyMenuOpenId = ref(null)
  const activeSessionCode = ref('')
  const activeConversationDetail = ref(null)
  const activeConversationRound = ref(null)
  const activeConversationUserInput = ref('')
  const activeConversationSummary = ref('')
  const activeArtifactNotice = ref('')
  const pageReady = ref(false)
  const routeSessionCode = computed(() => resolveRouteSessionCode(route))

  const stageSummary = computed(() => {
    const total = stages.value.length
    const done = stages.value.filter((item) => item.status === 'done').length
    return `${done}/${total} 已完成`
  })

  const filteredHistoryList = computed(() => {
    const keyword = historyKeyword.value.trim().toLowerCase()
    const list = keyword
      ? historyList.value.filter((item) => {
          return [item.title, item.summary, item.tag]
            .filter(Boolean)
            .some((field) => String(field).toLowerCase().includes(keyword))
        })
      : historyList.value

    return [...list]
      .map((item, index) => ({ item, index }))
      .sort((a, b) => {
        const pinDiff = Number(b.item.pinned) - Number(a.item.pinned)
        if (pinDiff !== 0) return pinDiff
        return a.index - b.index
      })
      .map(({ item }) => item)
  })

  const pieBackground = buildPieBackground(pieSegments)

  function syncSelectedConversation(code) {
    activeSessionCode.value = code || ''
    historyList.value = historyList.value.map((item) => ({
      ...item,
      active: item.id === activeSessionCode.value
    }))
  }

  function upsertConversationItem(nextItem) {
    if (!nextItem?.id) {
      return
    }

    const index = historyList.value.findIndex((item) => item.id === nextItem.id)
    if (index >= 0) {
      const current = historyList.value[index]
      historyList.value.splice(index, 1, {
        ...current,
        ...nextItem,
        active: current.active || nextItem.active
      })
      return
    }

    historyList.value.unshift(nextItem)
  }

  function applyFallbackHistory() {
    historyList.value = buildLocalFallbackHistory()
    const firstCode = firstConversationCode(historyList.value)
    syncSelectedConversation(firstCode)
  }

  async function loadModels() {
    try {
      const response = unwrapResponse(await fetchAssistantModels())
      const list = Array.isArray(response) ? response : []
      const normalized = normalizeModelOptions(Array.isArray(list) ? list : [])
      if (normalized.length > 0) {
        models.value = normalized
        if (!selectedModel.value || !models.value.some((item) => item.value === selectedModel.value)) {
          selectedModel.value = models.value[0].value
        }
      }
    } catch {
      models.value = defaultModels.map((item) => ({ ...item }))
      selectedModel.value = models.value[0]?.value || ''
    }
  }

  async function loadConversations() {
    try {
      const response = unwrapResponse(await fetchAssistantConversationList({ userId: parseStoredUserId() }))
      const list = Array.isArray(response) ? response : []
      if (Array.isArray(list) && list.length > 0) {
        const routeSessionCode = typeof route.params?.sessionCode === 'string' ? route.params.sessionCode.trim() : ''
        const preferredCode = routeSessionCode || activeSessionCode.value
        historyList.value = list.map((session) => mapSessionToHistoryItem(session, preferredCode))
        const firstCode = preferredCode || firstConversationCode(historyList.value)
        syncSelectedConversation(firstCode)
        return
      }
      historyList.value = []
      activeSessionCode.value = ''
    } catch {
      applyFallbackHistory()
    }
  }

  async function loadConversationDetail(sessionCode) {
    if (!sessionCode) {
      activeConversationDetail.value = null
      activeConversationRound.value = null
      activeConversationUserInput.value = ''
      activeConversationSummary.value = ''
      activeArtifactNotice.value = ''
      return null
    }

    try {
      const response = unwrapResponse(await fetchAssistantConversationDetail({
        userId: parseStoredUserId(),
        sessionCode
      }))
      const detail = normalizeConversationDetail(response)
      activeConversationDetail.value = detail
      const roundDetail = resolveActiveRoundDetail(detail)
      activeConversationRound.value = roundDetail
      activeConversationUserInput.value = resolveRoundUserInput(roundDetail, detail?.session)
      activeConversationSummary.value = resolveRoundSummary(roundDetail, detail?.session)
      activeArtifactNotice.value = buildArtifactNotice(roundDetail)
      executions.value = buildExecutionTimeline(roundDetail)
      stages.value = buildArtifactStages(roundDetail)
      return detail
    } catch {
      activeConversationDetail.value = null
      activeConversationRound.value = null
      activeConversationUserInput.value = ''
      activeConversationSummary.value = ''
      activeArtifactNotice.value = ''
      return null
    }
  }

  function updateHistoryBySessionCode(sessionCode, patch) {
    if (!sessionCode) {
      return
    }

    const target = historyList.value.find((item) => item.id === sessionCode)
    if (!target) {
      return
    }

    Object.assign(target, patch)
  }

  function setExecutionStatus(status, message) {
    if (status === 'running') {
      stages.value = stages.value.map((item, index) => {
        if (index < 2) return { ...item, status: 'done' }
        if (index === 2) return { ...item, status: 'running' }
        return { ...item, status: 'pending' }
      })
    }
    if (message) {
      executions.value.unshift({
        title: status === 'error' ? '执行失败' : '执行事件',
        detail: message,
        tone: status === 'error' ? 'warning' : 'info',
        active: status === 'running'
      })
    }
  }

  function updateExecutionTrack(text) {
    executions.value.unshift({
      title: '用户提问',
      detail: `模型 ${selectedModel.value} 已接收问题：${text}`,
      tone: 'user',
      active: false
    })
    executions.value.unshift({
      title: '执行计划生成',
      detail: 'AI 正在拆解问题，准备按时间范围、组织维度和成本口径组合查询。',
      tone: 'info',
      active: true
    })
    executions.value.unshift({
      title: '工具调用',
      detail: '开始请求元数据服务、指标中心与查询执行引擎。',
      tone: 'success',
      active: false
    })

    stages.value = stages.value.map((item, index) => {
      if (index < 2) return { ...item, status: 'done' }
      if (index === 2) return { ...item, status: 'running' }
      return { ...item, status: 'pending' }
    })
  }

  function clearDraftConversationState() {
    activeSessionCode.value = ''
    activeConversationDetail.value = null
    activeConversationRound.value = null
    activeConversationUserInput.value = ''
    activeConversationSummary.value = ''
    activeArtifactNotice.value = ''
    prompt.value = ''
    historyMenuOpenId.value = null
    clearPendingAiChatDraft()
    executions.value = initialExecutions.map((item) => ({ ...item }))
    stages.value = initialStages.map((item) => ({ ...item }))
    historyList.value = historyList.value.map((item) => ({
      ...item,
      active: false
    }))
  }

  function resetDraftConversation() {
    clearDraftConversationState()
    router.replace({ path: '/' })
  }

  async function submitQuery(options = {}) {
    const text = String(options.message ?? prompt.value ?? '').trim()
    if (!text) return

    const request = {
      sessionCode: options.sessionCode || activeSessionCode.value || undefined,
      apiModel: options.apiModel || selectedModel.value || undefined,
      message: text
    }

    updateExecutionTrack(text)
    prompt.value = ''

    try {
      const response = await queryAssistantConversationStream(request)
      let finalResult = null

      await consumeSseResponse(response, {
        onEvent: ({ eventType, data }) => {
          if (!data) {
            return
          }

          let payload = null
          try {
            payload = JSON.parse(data)
          } catch {
            payload = { message: data }
          }

          const sessionCode = payload?.sessionCode || request.sessionCode || ''
          const roundCode = payload?.roundCode || ''

          if (eventType === 'init') {
            if (sessionCode) {
              activeSessionCode.value = sessionCode
            }
            upsertConversationItem({
              id: sessionCode || `local-${Date.now()}`,
              title: payload?.sessionName || buildConversationTitle(text),
              summary: buildConversationSummary(text),
              tag: buildConversationTag(text),
              time: '刚刚',
              active: true,
              pinned: false
            })
            syncSelectedConversation(sessionCode)
            setExecutionStatus('running', '任务已开始，正在建立执行上下文。')
            window.dispatchEvent(new CustomEvent('ai-chat-session-updated'))
            return
          }

          if (eventType === 'chunk') {
            const message = payload?.delta || ''
            if (message) {
              const activeExecution = executions.value.find((item) => item.active)
              if (activeExecution) {
                activeExecution.detail = `${activeExecution.detail}${activeExecution.detail.endsWith('。') ? '' : '。'}${message}`
              } else {
                executions.value.unshift({
                  title: '流式输出',
                  detail: message,
                  tone: 'info',
                  active: true
                })
              }
            }
            if (sessionCode) {
              updateHistoryBySessionCode(sessionCode, {
                active: true,
                time: '刚刚'
              })
            }
            return
          }

          if (eventType === 'complete') {
            finalResult = payload
            executions.value = executions.value.map((item) => ({
              ...item,
              active: false
            }))
            stages.value = stages.value.map((item) => ({
              ...item,
              status: 'done'
            }))
            const sessionCode = payload?.sessionCode || request.sessionCode || activeSessionCode.value
            if (sessionCode) {
              activeSessionCode.value = sessionCode
              updateHistoryBySessionCode(sessionCode, {
                summary: payload?.answer ? shortenText(payload.answer, 36) : buildConversationSummary(text),
                time: '刚刚',
                active: true
              })
            }

            if (payload?.answer) {
              const target = historyList.value.find((item) => item.id === sessionCode)
              if (target) {
                target.summary = shortenText(payload.answer, 36)
              }
            }

            executions.value.unshift({
              title: '执行完成',
              detail: payload?.answer ? shortenText(payload.answer, 60) : '任务已完成。',
              tone: 'success',
              active: false
            })
            window.dispatchEvent(new CustomEvent('ai-chat-session-updated'))
          }

          if (eventType === 'error') {
            executions.value = executions.value.map((item) => ({
              ...item,
              active: false
            }))
            setExecutionStatus('error', payload?.message || '请求 ai-chat 失败')
          }
        }
      })

      const sessionCode = finalResult?.sessionCode || activeSessionCode.value || request.sessionCode || ''
      if (sessionCode) {
        const detail = await loadConversationDetail(sessionCode)
        const session = detail?.session
        if (session) {
          upsertConversationItem({
            id: session.sessionCode,
            title: session.sessionName || buildConversationTitle(text),
            summary: buildConversationSummary(text),
            tag: session.businessType || buildConversationTag(text),
            time: '刚刚',
            active: true,
            pinned: Boolean(session.pinned)
          })
          syncSelectedConversation(session.sessionCode)
        }
      }
    } catch (error) {
      executions.value.unshift({
        title: '执行失败',
        detail: error instanceof Error ? error.message : '请求 ai-chat 失败',
        tone: 'warning',
        active: false
      })
    }
  }

  async function trySubmitPendingDraft(sessionCode) {
    if (!sessionCode) {
      return
    }

    const draft = consumePendingAiChatDraft(sessionCode)
    if (!draft?.message) {
      return
    }

    if (draft.apiModel && models.value.some((item) => item.value === draft.apiModel)) {
      selectedModel.value = draft.apiModel
    }

    activeSessionCode.value = sessionCode
    await submitQuery({
      message: draft.message,
      apiModel: draft.apiModel || undefined,
      sessionCode
    })
  }

  async function createConversation() {
    resetDraftConversation()
  }

  async function activateConversation(id) {
    await router.push(`/c/${id}`)
    historyMenuOpenId.value = null
  }

  function toggleHistoryMenu(id) {
    historyMenuOpenId.value = historyMenuOpenId.value === id ? null : id
  }

  async function renameConversation(id) {
    const target = historyList.value.find((item) => item.id === id)
    if (!target) return

    const nextTitle = window.prompt('编辑会话标题', target.title)?.trim()
    if (!nextTitle || nextTitle === target.title) {
      historyMenuOpenId.value = null
      return
    }

    try {
      const response = unwrapResponse(await renameAssistantConversation({
        userId: parseStoredUserId(),
        sessionCode: id,
        sessionName: nextTitle
      }))
      const session = response
      target.title = session?.sessionName || nextTitle
    } catch {
      target.title = nextTitle
    } finally {
      historyMenuOpenId.value = null
    }
  }

  async function pinConversation(id) {
    const target = historyList.value.find((item) => item.id === id)
    if (!target) return

    const nextPinned = !target.pinned
    try {
      const response = unwrapResponse(await pinAssistantConversation({
        userId: parseStoredUserId(),
        sessionCode: id,
        pinned: nextPinned
      }))
      const session = response
      target.pinned = Boolean(session?.pinned ?? nextPinned)
    } catch {
      target.pinned = nextPinned
    } finally {
      historyMenuOpenId.value = null
    }
  }

  async function deleteConversation(id) {
    const target = historyList.value.find((item) => item.id === id)
    if (!target) return

    if (!window.confirm(`确认删除「${target.title}」？`)) {
      historyMenuOpenId.value = null
      return
    }

    try {
      await deleteAssistantConversation({
        userId: parseStoredUserId(),
        sessionCode: id
      })
      const nextList = historyList.value.filter((item) => item.id !== id)
      historyList.value = nextList
      syncSelectedConversation(firstConversationCode(nextList))
    } catch {
      historyList.value = historyList.value.filter((item) => item.id !== id)
      syncSelectedConversation(firstConversationCode(historyList.value))
    } finally {
      historyMenuOpenId.value = null
    }
  }

  function closeHistoryMenuByOutsideClick(event) {
    if (event.target?.closest?.('.history-item-actions')) {
      return
    }

    historyMenuOpenId.value = null
  }

  onMounted(async () => {
    document.documentElement.classList.add('query-lock-scroll')
    document.body.classList.add('query-lock-scroll')
    document.addEventListener('click', closeHistoryMenuByOutsideClick)
    window.addEventListener('ai-chat-reset-session', clearDraftConversationState)
    await Promise.all([loadModels(), loadConversations()])
    pageReady.value = true
    await trySubmitPendingDraft(routeSessionCode.value)
  })

  watch(
    () => route.params?.sessionCode,
    async (sessionCode) => {
      const nextCode = typeof sessionCode === 'string' ? sessionCode.trim() : ''
      if (!nextCode || activeSessionCode.value === nextCode) {
        return
      }
      syncSelectedConversation(nextCode)
      await loadConversationDetail(nextCode)
      if (!pageReady.value) {
        return
      }
      await trySubmitPendingDraft(nextCode)
    },
    { immediate: true }
  )

  onBeforeUnmount(() => {
    document.documentElement.classList.remove('query-lock-scroll')
    document.body.classList.remove('query-lock-scroll')
    document.removeEventListener('click', closeHistoryMenuByOutsideClick)
    window.removeEventListener('ai-chat-reset-session', clearDraftConversationState)
    clearPendingAiChatDraft()
  })

  return {
    models,
    selectedModel,
    prompt,
    executions,
    stages,
    historyCollapsed,
    previewFullscreen,
    historyKeyword,
    historyList,
    historyMenuOpenId,
    routeSessionCode,
    activeConversationDetail,
    activeConversationRound,
    activeConversationUserInput,
    activeConversationSummary,
    activeArtifactNotice,
    stageSummary,
    filteredHistoryList,
    pieSegments,
    pieBackground,
    barSeries,
    resultRows,
    placeholder,
    submitQuery,
    createConversation,
    activateConversation,
    toggleHistoryMenu,
    renameConversation,
    pinConversation,
    deleteConversation
  }
}
