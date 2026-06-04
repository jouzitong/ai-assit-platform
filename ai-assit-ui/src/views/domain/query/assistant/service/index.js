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
    if (!keyword) return historyList.value
    return historyList.value.filter((item) => item.title.toLowerCase().includes(keyword))
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
    historyList.value = historyList.value.map((item) => ({ ...item, active: false }))
    historyList.value.unshift({
      title: `新对话 ${historyList.value.length + 1}`,
      time: '刚刚',
      active: true
    })
  }

  onMounted(() => {
    document.documentElement.classList.add('query-lock-scroll')
    document.body.classList.add('query-lock-scroll')
    nextTick(resizeComposer)
  })

  onBeforeUnmount(() => {
    document.documentElement.classList.remove('query-lock-scroll')
    document.body.classList.remove('query-lock-scroll')
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
    resizeComposer
  }
}
