import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  barSeries,
  initialExecutions,
  initialHistoryList,
  initialStages,
  models,
  pieSegments,
  placeholder,
  resultRows
} from '../data'

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

export function useQueryAssistantPage() {
  const selectedModel = ref(models[0].value)
  const prompt = ref('')
  const executions = ref(initialExecutions.map((item) => ({ ...item })))
  const stages = ref(initialStages.map((item) => ({ ...item })))
  const historyCollapsed = ref(false)
  const previewFullscreen = ref(false)
  const historyKeyword = ref('')
  const historyList = ref(initialHistoryList.map((item) => ({ ...item })))
  const historyMenuOpenId = ref(null)
  const composerInput = ref(null)
  const minInputHeight = 48
  const maxInputHeight = 320

  const stageSummary = computed(() => {
    const total = stages.value.length
    const done = stages.value.filter((item) => item.status === 'done').length
    return `${done}/${total} 已完成`
  })

  const filteredHistoryList = computed(() => {
    const keyword = historyKeyword.value.trim().toLowerCase()
    const list = keyword
      ? historyList.value.filter((item) => item.title.toLowerCase().includes(keyword))
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

  function resizeComposer() {
    const el = composerInput.value
    if (!el) return

    el.style.height = `${minInputHeight}px`
    const nextHeight = Math.min(el.scrollHeight, maxInputHeight)
    el.style.height = `${Math.max(nextHeight, minInputHeight)}px`
    el.style.overflowY = el.scrollHeight > maxInputHeight ? 'auto' : 'hidden'
  }

  function submitQuery() {
    const text = prompt.value.trim()
    if (!text) return

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

    prompt.value = ''
    nextTick(resizeComposer)
  }

  function createConversation() {
    historyMenuOpenId.value = null
    historyList.value = historyList.value.map((item) => ({ ...item, active: false }))
    historyList.value.unshift({
      id: `history-${Date.now()}`,
      title: `新对话 ${historyList.value.length + 1}`,
      time: '刚刚',
      active: true,
      pinned: false
    })
  }

  function activateConversation(id) {
    historyList.value = historyList.value.map((item) => ({
      ...item,
      active: item.id === id
    }))
    historyMenuOpenId.value = null
  }

  function toggleHistoryMenu(id) {
    historyMenuOpenId.value = historyMenuOpenId.value === id ? null : id
  }

  function renameConversation(id) {
    const target = historyList.value.find((item) => item.id === id)
    if (!target) return

    const nextTitle = window.prompt('编辑会话标题', target.title)?.trim()
    if (!nextTitle || nextTitle === target.title) {
      historyMenuOpenId.value = null
      return
    }

    target.title = nextTitle
    historyMenuOpenId.value = null
  }

  function pinConversation(id) {
    const target = historyList.value.find((item) => item.id === id)
    if (!target) return

    target.pinned = !target.pinned
    historyMenuOpenId.value = null
  }

  function deleteConversation(id) {
    const target = historyList.value.find((item) => item.id === id)
    if (!target) return

    if (!window.confirm(`确认删除「${target.title}」？`)) {
      historyMenuOpenId.value = null
      return
    }

    const nextList = historyList.value.filter((item) => item.id !== id)
    const wasActive = target.active
    historyList.value = nextList.length
      ? nextList.map((item, index) => ({
          ...item,
          active: wasActive ? index === 0 : item.active
        }))
      : []
    historyMenuOpenId.value = null
  }

  function closeHistoryMenuByOutsideClick(event) {
    if (event.target?.closest?.('.history-item-actions')) {
      return
    }

    historyMenuOpenId.value = null
  }

  onMounted(() => {
    document.documentElement.classList.add('query-lock-scroll')
    document.body.classList.add('query-lock-scroll')
    document.addEventListener('click', closeHistoryMenuByOutsideClick)
    nextTick(resizeComposer)
  })

  onBeforeUnmount(() => {
    document.documentElement.classList.remove('query-lock-scroll')
    document.body.classList.remove('query-lock-scroll')
    document.removeEventListener('click', closeHistoryMenuByOutsideClick)
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
    composerInput,
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
    deleteConversation,
    resizeComposer
  }
}
