<script setup lang="ts">
import {
  ArrowLeftBold,
  ArrowRightBold,
  ArrowDown,
  CopyDocument,
  Document,
  EditPen,
  Microphone,
  MoreFilled,
  Operation,
  Promotion,
  Search,
  ChatDotRound,
  UserFilled,
} from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, ref, useTemplateRef, watch } from 'vue'
import chatTransportProtocol from '../../../../../data/chatMessage/chat-transport-protocol.json'
import brandLogo from '../../../assets/icons/brand-logo.svg'
import brandMark from '../../../assets/icons/brand-mark.svg'
import DashboardCanvasPreview from '../components/DashboardCanvasPreview.vue'

type ChatRole = 'assistant' | 'user'

type ThinkingActivityStatus = 'done' | 'running' | 'pending' | 'failed'

type ThinkingActivityDetail = {
  id: string
  title: string
  content: string
  url?: string
}

type ThinkingActivitySource = {
  id: string
  label: string
  icon: string
}

type ThinkingActivity = {
  id: string
  title: string
  status: ThinkingActivityStatus
  description: string
  sources?: ThinkingActivitySource[]
  details?: ThinkingActivityDetail[]
}

type StaticMessage = {
  id: string
  role: ChatRole
  content: string
  status?: 'pending' | 'running' | 'completed'
  createdAt?: string
  thinkingStatus?: 'running' | 'completed'
  thinkingStartedAt?: number
  thinkingElapsedSeconds?: number
  thinking?: ThinkingActivity[]
  canvas?: boolean
}

type StaticSession = {
  id: string
  title: string
  meta: string
  messages: StaticMessage[]
}

type ProtocolContentBlock = {
  type: string
  text?: string
  markdown?: string
}

type ProtocolEvent = {
  id: string
  event: string
  data: {
    payload: Record<string, any>
  }
}

const initialSessions: StaticSession[] = [
  {
    id: 'static-risk-session',
    title: '风控日报分析',
    meta: '刚刚',
    messages: [
      {
        id: 'risk-user-0',
        role: 'user',
        createdAt: '2026-07-08 22:58',
        content: '加载今天的风控日报，先帮我看一下异常指标。',
      },
      {
        id: 'risk-assistant-1',
        role: 'assistant',
        status: 'completed',
        createdAt: '2026-07-08 22:58',
        content: '我已加载静态风控日报。当前异常集中在登录失败率、提现拦截率和工单响应时长三项指标。',
      },
      {
        id: 'risk-user-1',
        role: 'user',
        createdAt: '2026-07-08 22:59',
        content: '帮我按优先级列出需要排查的事项。',
      },
      {
        id: 'risk-assistant-2',
        role: 'assistant',
        status: 'completed',
        createdAt: '2026-07-08 23:00',
        thinkingStatus: 'completed',
        thinkingElapsedSeconds: 83,
        thinking: [
          {
            id: 'risk-analysis',
            title: '当前分析结果',
            status: 'done',
            description: '用户需要按优先级整理排查事项。静态日报里提现拦截、登录失败率、工单响应时长同时异常。',
          },
          {
            id: 'risk-ai-query',
            title: '查询 AI 活动',
            status: 'done',
            description: '模拟读取最近一次 AI 总结活动，确认资金链路风险权重高于访问稳定性和客服响应指标。',
            sources: [
              { id: 'ai-activity-log', label: 'AI 活动日志', icon: 'ai' },
              { id: 'workflow-run', label: 'workflow.run', icon: 'wf' },
            ],
          },
          {
            id: 'risk-kb-query',
            title: '查询知识库',
            status: 'running',
            description: '模拟命中《风控异常排查手册》，提现拦截命中来源应优先按规则、渠道和用户标签拆分。',
            sources: [
              { id: 'risk-kb', label: '风控知识库', icon: 'kb' },
              { id: 'runbook', label: '排查手册', icon: 'doc' },
            ],
          },
          {
            id: 'risk-search',
            title: '搜索功能',
            status: 'pending',
            description: '模拟搜索近 24 小时相关异常记录，登录失败主要集中在部分地区，客服工单可用于交叉验证用户样本。',
            sources: [
              { id: 'search-risk', label: 'risk-search.internal', icon: 's' },
              { id: 'ticket-system', label: 'tickets.okx.internal', icon: 't' },
            ],
            details: [
              {
                id: 'risk-search-result-1',
                title: '搜索结果',
                content: '命中 18 条异常记录，其中登录失败集中在 APAC-East 和 EU-West 两个地区；同批用户中有 7 条客服工单。',
                url: 'https://search.internal.example/risk?query=login-failed-withdraw-block',
              },
              {
                id: 'risk-search-result-2',
                title: '内容摘要',
                content: '提现拦截命中规则集中在“高频小额提现”和“新设备登录后提现”，建议优先核对规则版本和渠道来源。',
              },
            ],
          },
          {
            id: 'risk-plan',
            title: '计划下一步内容',
            status: 'pending',
            description: '下一步可以补充负责人、截止时间和复盘口径，形成完整处置计划。',
          },
        ],
        canvas: true,
        content: [
          '### 执行完成摘要',
          '',
          '- **优先排查提现拦截规则命中来源**：重点核对高频小额提现、新设备登录后提现两个规则命中来源。',
          '- **其次核对登录失败地区分布**：当前异常集中在 `APAC-East` 与 `EU-West`，建议按地区、渠道、设备维度拆分。',
          '- **最后查看客服工单样本**：用工单用户与异常用户做交叉验证，确认是否为同一批用户触发。',
          '',
          '> 建议输出负责人、截止时间和复盘口径，形成完整处置计划。',
        ].join('\n'),
      },
    ],
  },
  {
    id: 'static-product-session',
    title: '产品需求拆解',
    meta: '今天',
    messages: [
      {
        id: 'product-assistant-1',
        role: 'assistant',
        status: 'completed',
        content: '这个静态会话用于验证首页同款聊天交互：切换会话、发送消息、快捷建议和模拟回复。',
      },
      {
        id: 'product-user-1',
        role: 'user',
        content: '给我一个页面交互清单。',
      },
      {
        id: 'product-assistant-2',
        role: 'assistant',
        status: 'completed',
        thinking: [
          {
            id: 'product-analysis',
            title: '当前分析结果',
            status: 'done',
            description: '用户希望验证静态聊天页的完整交互链路，需要覆盖导航、状态、消息流和输入区。',
          },
          {
            id: 'product-plan',
            title: '计划下一步内容',
            status: 'done',
            description: '按用户操作路径排序：先切换会话，再发送消息，最后检查模拟回复和快捷建议。',
          },
          {
            id: 'product-kb-query',
            title: '查询知识库',
            status: 'pending',
            description: '接入真实接口后可查询组件规范，补充消息状态、工具调用和错误态展示规则。',
          },
        ],
        content: '清单包含：左侧会话切换、顶部模型状态、中部消息流、底部输入框、快捷问题填充、模拟回复生成。',
      },
    ],
  },
]

const selectedModelLabel = 'ZG GPT-Static'
const prompt = ref('')
const activeSessionId = ref(initialSessions[0]?.id || '')
const sidebarExpanded = ref(true)
const isStreaming = ref(false)
const thinkingDrawerVisible = ref(false)
const thinkingDrawerTransitioning = ref(false)
const activeThinking = ref<ThinkingActivity[]>([])
const sessions = ref<StaticSession[]>(cloneSessions(initialSessions))
const isSimulationRunning = ref(false)
const isSimulationPaused = ref(false)
const simulationEventIndex = ref(0)
let simulationTimer: number | null = null
let thinkingClockTimer: number | null = null
const welcomeTextarea = useTemplateRef<HTMLTextAreaElement>('welcomeTextarea')
const conversationTextarea = useTemplateRef<HTMLTextAreaElement>('conversationTextarea')

const welcomeCards = [
  '总结这组静态数据的关键结论',
  '生成一份三步排查计划',
  '把回复改成更适合管理层的版本',
  '列出还缺哪些字段才能接真实接口',
]

const welcomeSuggestions = [
  { title: '分析静态数据', subtitle: '输出关键结论和异常指标', prompt: welcomeCards[0] },
  { title: '生成排查计划', subtitle: '拆成三步行动项', prompt: welcomeCards[1] },
  { title: '管理层摘要', subtitle: '改写成简短汇报口径', prompt: welcomeCards[2] },
]

const quickNavItems = [
  { key: 'new-chat', label: '新对话', icon: EditPen },
  { key: 'search', label: '搜索', icon: Search },
  { key: 'notes', label: '笔记', icon: Document },
  { key: 'workspace', label: '工作空间', icon: Operation },
]

const activeSession = computed(() => sessions.value.find((session) => session.id === activeSessionId.value))
const chatMessages = computed(() => activeSession.value?.messages || [])
const isConversationMode = computed(() => Boolean(activeSessionId.value))
const currentSessionName = computed(() => activeSession.value?.title || '静态测试会话')
const shouldReserveThinkingDrawer = computed(() => thinkingDrawerVisible.value || thinkingDrawerTransitioning.value)
const lastAssistantMessageId = computed(() => {
  return [...chatMessages.value].reverse().find((message) => message.role === 'assistant' && message.status === 'completed')?.id
})
const thinkingTaskNodes = computed(() =>
  activeThinking.value.map((activity, index) => ({
    id: activity.id,
    title: activity.title,
    description: activity.description,
    status: activity.status,
    step: `${index + 1}`.padStart(2, '0'),
  })),
)
const pinnedConversations = computed(() =>
  sessions.value.map((session) => ({
    id: session.id,
    title: session.title,
    meta: session.meta,
  })),
)

function cloneSessions(value: StaticSession[]) {
  return JSON.parse(JSON.stringify(value)) as StaticSession[]
}

function resizeTextarea(element: HTMLTextAreaElement | null) {
  if (!element) {
    return
  }

  const computedStyle = window.getComputedStyle(element)
  const lineHeight = Number.parseFloat(computedStyle.lineHeight) || 24
  const maxHeight = lineHeight * 8
  element.style.height = '0px'
  element.style.height = `${Math.min(element.scrollHeight, maxHeight)}px`
  element.style.overflowY = element.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

async function syncTextareaHeights() {
  await nextTick()
  resizeTextarea(welcomeTextarea.value)
  resizeTextarea(conversationTextarea.value)
}

function applySuggestion(text: string) {
  prompt.value = text
  void syncTextareaHeights()
}

function createMessage(role: ChatRole, content: string): StaticMessage {
  return {
    id: `${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role,
    content,
    createdAt: formatMessageCreatedAt(new Date()),
  }
}

function formatMessageCreatedAt(date: Date) {
  const pad = (value: number) => `${value}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function copyMessageContent(content: string) {
  if (!navigator.clipboard) {
    return
  }
  await navigator.clipboard.writeText(content)
}

function createAssistantMessage(content: string, sourcePrompt: string): StaticMessage {
  return {
    ...createMessage('assistant', content),
    status: 'completed',
    thinking: createThinkingActivities(sourcePrompt),
  }
}

function createThinkingActivities(sourcePrompt: string): ThinkingActivity[] {
  return [
    {
      id: `analysis-${Date.now()}`,
      title: '当前分析结果',
      status: 'done',
      description: `识别用户输入：“${sourcePrompt}”。当前命中静态测试会话，不调用真实接口。`,
    },
    {
      id: `plan-${Date.now()}`,
      title: '计划下一步内容',
      status: 'done',
      description: '先匹配问题意图，再选择静态回复模板，最后把结果追加到首页同款消息流。',
    },
    {
      id: `ai-activity-${Date.now()}`,
      title: '查询 AI 活动',
      status: 'running',
      description: '模拟查询最近 AI 执行记录，返回一条可复用的回复生成活动。',
      sources: [
        { id: `activity-log-${Date.now()}`, label: 'AI 活动日志', icon: 'ai' },
      ],
    },
    {
      id: `knowledge-${Date.now()}`,
      title: '查询知识库',
      status: 'pending',
      description: '当前未接入知识库接口，保留静态占位描述，后续可替换为真实检索结果。',
      sources: [
        { id: `kb-${Date.now()}`, label: '知识库占位', icon: 'kb' },
      ],
    },
    {
      id: `search-${Date.now()}`,
      title: '搜索功能',
      status: 'pending',
      description: '当前未触发真实搜索，后续可展示搜索词、命中数量和引用来源。',
      sources: [
        { id: `search-source-${Date.now()}`, label: 'search.internal', icon: 's' },
      ],
      details: [
        {
          id: `search-result-${Date.now()}`,
          title: '搜索结果占位',
          content: `搜索词：“${sourcePrompt}”。当前为静态数据，后续可替换为真实搜索结果列表、摘要和来源链接。`,
          url: 'https://search.internal.example/demo',
        },
      ],
    },
  ]
}

function getThinkingStatusText(status: ThinkingActivityStatus) {
  const statusMap: Record<ThinkingActivityStatus, string> = {
    done: '已完成',
    running: '进行中',
    pending: '待处理',
    failed: '失败',
  }
  return statusMap[status]
}

function normalizeProtocolStatus(status?: string): ThinkingActivityStatus {
  if (status === 'completed') {
    return 'done'
  }
  if (status === 'failed') {
    return 'failed'
  }
  if (status === 'running') {
    return 'running'
  }
  return 'pending'
}

function formatProtocolCreatedAt(value?: string) {
  if (!value) {
    return undefined
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.slice(0, 16).replace('T', ' ')
  }
  return formatMessageCreatedAt(date)
}

function formatThinkingDuration(seconds: number) {
  const safeSeconds = Math.max(0, Math.floor(seconds))
  const minutes = Math.floor(safeSeconds / 60)
  const restSeconds = safeSeconds % 60
  if (minutes > 0) {
    return `${minutes}分${restSeconds}秒`
  }
  return `${restSeconds}秒`
}

function resolveThinkingElapsedSeconds(message?: StaticMessage) {
  if (!message) {
    return 0
  }
  if (message.thinkingStatus === 'running' && message.thinkingStartedAt) {
    return Math.floor((Date.now() - message.thinkingStartedAt) / 1000)
  }
  return message.thinkingElapsedSeconds || 0
}

function resolveThinkingTitle(message?: StaticMessage) {
  return message?.thinkingStatus === 'running' ? '思考中' : '思考过程'
}

function resolveThinkingMeta(message?: StaticMessage) {
  return `已处理 ${formatThinkingDuration(resolveThinkingElapsedSeconds(message))}`
}

function clearThinkingClock() {
  if (thinkingClockTimer !== null) {
    window.clearInterval(thinkingClockTimer)
    thinkingClockTimer = null
  }
}

function startThinkingClock(message: StaticMessage) {
  clearThinkingClock()
  message.thinkingStatus = 'running'
  message.thinkingStartedAt = Date.now()
  message.thinkingElapsedSeconds = 0
  thinkingClockTimer = window.setInterval(() => {
    message.thinkingElapsedSeconds = resolveThinkingElapsedSeconds(message)
  }, 1000)
}

function completeThinkingClock(message: StaticMessage) {
  message.thinkingElapsedSeconds = resolveThinkingElapsedSeconds(message)
  message.thinkingStatus = 'completed'
  clearThinkingClock()
}

function resolveProtocolContent(blocks?: ProtocolContentBlock[]) {
  if (!blocks?.length) {
    return ''
  }
  return blocks.map((block) => block.markdown || block.text || '').join('\n')
}

function resolveSimulationSession(payload?: Record<string, any>) {
  const conversation = payload?.conversation
  const sessionCode = conversation?.sessionCode || conversation?.id || 'static-risk-session'
  let session = sessions.value.find((item) => item.id === sessionCode)

  if (!session) {
    session = {
      id: sessionCode,
      title: conversation?.title || '风控日报分析',
      meta: '刚刚',
      messages: [],
    }
    sessions.value.unshift(session)
  }

  session.title = conversation?.title || session.title
  activeSessionId.value = session.id
  return session
}

function resolveSimulationAssistant(session: StaticSession, assistantId = 'risk-assistant-round-2') {
  let message = session.messages.find((item) => item.id === assistantId)
  if (!message) {
    message = {
      id: assistantId,
      role: 'assistant',
      status: 'pending',
      content: '',
      createdAt: formatMessageCreatedAt(new Date()),
      thinking: [],
    }
    session.messages.push(message)
  }
  return message
}

function applyThinkingNodes(nodes?: Array<Record<string, any>>) {
  if (!nodes?.length) {
    return
  }

  const nextActivities = [...activeThinking.value]
  for (const node of nodes) {
    const activity: ThinkingActivity = {
      id: node.id,
      title: node.title,
      status: normalizeProtocolStatus(node.status),
      description: node.description || '',
    }
    const index = nextActivities.findIndex((item) => item.id === activity.id)
    if (index >= 0) {
      nextActivities[index] = { ...nextActivities[index], ...activity }
    } else {
      nextActivities.push(activity)
    }
  }
  activeThinking.value = nextActivities
}

function showThinkingDrawer() {
  thinkingDrawerTransitioning.value = true
  thinkingDrawerVisible.value = true
}

function hideThinkingDrawer() {
  thinkingDrawerVisible.value = false
}

function handleThinkingDrawerAfterLeave() {
  thinkingDrawerTransitioning.value = false
}

function applySimulationEvent(protocolEvent: ProtocolEvent) {
  const payload = protocolEvent.data.payload

  if (protocolEvent.event === 'session.initialized') {
    const session = resolveSimulationSession(payload)
    session.messages = []
    hideThinkingDrawer()
    activeThinking.value = []
    return
  }

  const session = ensureSession()

  if (protocolEvent.event === 'round.initialized') {
    const userMessage = payload.round?.userMessage
    if (userMessage && !session.messages.some((message) => message.id === userMessage.id)) {
      session.messages.push({
        id: userMessage.id,
        role: 'user',
        createdAt: formatProtocolCreatedAt(userMessage.createdAt),
        content: resolveProtocolContent(userMessage.content),
      })
    }
    return
  }

  if (protocolEvent.event === 'assistant.started') {
    const assistant = payload.assistant
    const assistantMessage = resolveSimulationAssistant(session, assistant?.id)
    assistantMessage.status = 'running'
    assistantMessage.content = ''
    isStreaming.value = true
    return
  }

  if (protocolEvent.event === 'thinking.started') {
    const assistantMessage = resolveSimulationAssistant(session)
    activeThinking.value = []
    assistantMessage.thinking = activeThinking.value
    startThinkingClock(assistantMessage)
    showThinkingDrawer()
    return
  }

  if (protocolEvent.event === 'thinking.updated') {
    const assistantMessage = resolveSimulationAssistant(session)
    applyThinkingNodes(payload.nodes)
    assistantMessage.thinking = activeThinking.value
    return
  }

  if (protocolEvent.event === 'assistant.message.delta') {
    const message = payload.message
    const assistantMessage = resolveSimulationAssistant(session)
    assistantMessage.createdAt = formatProtocolCreatedAt(message?.createdAt) || assistantMessage.createdAt
    assistantMessage.content = resolveProtocolContent(message?.content)
    assistantMessage.thinking = activeThinking.value
    assistantMessage.status = 'running'
    isStreaming.value = false
    return
  }

  if (protocolEvent.event === 'artifacts.build') {
    const assistantMessage = resolveSimulationAssistant(session)
    assistantMessage.canvas = true
    return
  }

  if (protocolEvent.event === 'thinking.completed') {
    applyThinkingNodes(payload.nodes)
    const assistantMessage = resolveSimulationAssistant(session)
    assistantMessage.thinking = activeThinking.value
    completeThinkingClock(assistantMessage)
    hideThinkingDrawer()
    return
  }

  if (protocolEvent.event === 'round.completed') {
    const round = payload.round
    const assistant = round?.assistant
    const firstMessage = assistant?.messages?.[0]
    const assistantMessage = resolveSimulationAssistant(session)
    assistantMessage.content = resolveProtocolContent(firstMessage?.content) || assistantMessage.content
    assistantMessage.createdAt = formatProtocolCreatedAt(firstMessage?.createdAt) || assistantMessage.createdAt
    assistantMessage.thinking = activeThinking.value
    assistantMessage.canvas = Boolean(assistant?.artifacts?.length)
    assistantMessage.status = 'completed'
    isStreaming.value = false
    return
  }
}

function startProtocolSimulation() {
  isSimulationRunning.value = true
  isSimulationPaused.value = false
  scheduleNextSimulationEvent(0)
}

function clearSimulationTimer() {
  if (simulationTimer !== null) {
    window.clearTimeout(simulationTimer)
    simulationTimer = null
  }
}

function scheduleNextSimulationEvent(delay = 3000) {
  clearSimulationTimer()
  const stream = chatTransportProtocol.sampleEventStream as ProtocolEvent[]
  if (simulationEventIndex.value >= stream.length) {
    isSimulationRunning.value = false
    isSimulationPaused.value = false
    return
  }

  simulationTimer = window.setTimeout(() => {
    if (isSimulationPaused.value) {
      return
    }
    applySimulationEvent(stream[simulationEventIndex.value])
    simulationEventIndex.value += 1
    scheduleNextSimulationEvent()
  }, delay)
}

function resetSimulationState() {
  clearSimulationTimer()
  clearThinkingClock()
  isSimulationRunning.value = false
  isSimulationPaused.value = false
  simulationEventIndex.value = 0
  const session = sessions.value.find((item) => item.id === 'static-risk-session')
  if (session) {
    session.messages = []
  }
  activeSessionId.value = 'static-risk-session'
  activeThinking.value = []
  hideThinkingDrawer()
  isStreaming.value = false
}

function restartProtocolSimulation() {
  resetSimulationState()
  startProtocolSimulation()
}

function toggleProtocolSimulation() {
  const stream = chatTransportProtocol.sampleEventStream as ProtocolEvent[]
  if (!isSimulationRunning.value && simulationEventIndex.value >= stream.length) {
    simulationEventIndex.value = 0
  }

  if (!isSimulationRunning.value) {
    startProtocolSimulation()
    return
  }

  if (isSimulationPaused.value) {
    isSimulationPaused.value = false
    scheduleNextSimulationEvent()
    return
  }

  isSimulationPaused.value = true
  clearSimulationTimer()
}

function resolveSimulationButtonText() {
  const stream = chatTransportProtocol.sampleEventStream as ProtocolEvent[]
  if (isSimulationRunning.value && !isSimulationPaused.value) {
    return '暂停模拟'
  }
  if (isSimulationRunning.value && isSimulationPaused.value) {
    return '继续模拟'
  }
  if (simulationEventIndex.value > 0 && simulationEventIndex.value < stream.length) {
    return '继续模拟'
  }
  return '开始模拟'
}

function openThinkingDrawer(thinking: ThinkingActivity[]) {
  activeThinking.value = thinking
  showThinkingDrawer()
}

function closeThinkingDrawer() {
  hideThinkingDrawer()
}

function getStaticReply(message: string) {
  if (message.includes('接口') || message.includes('字段')) {
    return [
      '### 接口字段建议',
      '',
      '- 固定字段：`sessionCode`、`messageCode`、`role/content`、`status`',
      '- 扩展字段：`ext` 用于承载图表、工具调用或渲染组件结果',
      '- 状态字段建议覆盖：`RUNNING`、`SUCCESS`、`FAILED`',
    ].join('\n')
  }
  if (message.includes('计划') || message.includes('排查')) {
    return [
      '### 三步排查计划',
      '',
      '1. **确认指标口径**：核对异常指标来源、统计窗口和触发规则。',
      '2. **拆分样本**：按用户、地区、渠道、设备维度定位主要异常来源。',
      '3. **输出处置计划**：补充负责人、处理动作、截止时间和复盘口径。',
    ].join('\n')
  }
  if (message.includes('管理层')) {
    return [
      '### 管理层摘要',
      '',
      '**当前风险可控**，但提现拦截和登录失败率存在短时波动。',
      '',
      '- 当天完成异常归因',
      '- 同步处置进展',
      '- 输出复盘结论',
    ].join('\n')
  }
  return `已基于静态数据生成回复：${message}。这里不会调用接口，适合先验证首页同款交互和消息状态流转。`
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function renderInlineMarkdown(value: string) {
  return escapeHtml(value)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
}

function renderMarkdown(markdown: string) {
  const lines = markdown.split('\n')
  const html: string[] = []
  let listType: 'ul' | 'ol' | null = null

  function closeList() {
    if (listType) {
      html.push(`</${listType}>`)
      listType = null
    }
  }

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed) {
      closeList()
      continue
    }

    if (trimmed.startsWith('### ')) {
      closeList()
      html.push(`<h3>${renderInlineMarkdown(trimmed.slice(4))}</h3>`)
      continue
    }

    if (trimmed.startsWith('> ')) {
      closeList()
      html.push(`<blockquote>${renderInlineMarkdown(trimmed.slice(2))}</blockquote>`)
      continue
    }

    const orderedMatch = trimmed.match(/^\d+\.\s+(.*)$/)
    if (orderedMatch) {
      if (listType !== 'ol') {
        closeList()
        html.push('<ol>')
        listType = 'ol'
      }
      html.push(`<li>${renderInlineMarkdown(orderedMatch[1])}</li>`)
      continue
    }

    if (trimmed.startsWith('- ')) {
      if (listType !== 'ul') {
        closeList()
        html.push('<ul>')
        listType = 'ul'
      }
      html.push(`<li>${renderInlineMarkdown(trimmed.slice(2))}</li>`)
      continue
    }

    closeList()
    html.push(`<p>${renderInlineMarkdown(trimmed)}</p>`)
  }

  closeList()
  return html.join('')
}

function ensureSession() {
  if (activeSession.value) {
    return activeSession.value
  }

  const session: StaticSession = {
    id: `static-session-${Date.now()}`,
    title: '新的静态会话',
    meta: '刚刚',
    messages: [],
  }
  sessions.value.unshift(session)
  activeSessionId.value = session.id
  return session
}

async function handlePrimaryAction() {
  const message = prompt.value.trim()
  if (!message || isStreaming.value) {
    return
  }

  const session = ensureSession()
  session.messages.push(createMessage('user', message))
  prompt.value = ''
  isStreaming.value = true
  await syncTextareaHeights()

  window.setTimeout(() => {
    session.messages.push(createAssistantMessage(getStaticReply(message), message))
    isStreaming.value = false
  }, 360)
}

function handlePromptKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }

  event.preventDefault()
  void handlePrimaryAction()
}

function startNewChat() {
  activeSessionId.value = ''
  prompt.value = ''
  void syncTextareaHeights()
}

function selectSession(sessionId: string) {
  activeSessionId.value = sessionId
  prompt.value = ''
  void syncTextareaHeights()
}

watch(prompt, () => {
  void syncTextareaHeights()
})

watch(isConversationMode, () => {
  void syncTextareaHeights()
}, { immediate: true })

onBeforeUnmount(() => {
  clearSimulationTimer()
  clearThinkingClock()
})
</script>

<template>
  <div
    :class="[
      'chat-home-shell',
      {
        'is-sidebar-collapsed': !sidebarExpanded,
        'has-thinking-drawer': shouldReserveThinkingDrawer,
      },
    ]"
    :style="{ '--chat-sidebar-width': sidebarExpanded ? '210px' : '88px' }"
  >
    <aside class="chat-home-sidebar">
      <div class="chat-home-brand">
        <button
          class="chat-home-brand__logo"
          type="button"
          aria-label="返回静态聊天首页"
          title="返回静态聊天首页"
          @click="startNewChat"
        >
          <img
            class="chat-home-brand__logo-image"
            :src="sidebarExpanded ? brandLogo : brandMark"
            :alt="sidebarExpanded ? '智能问数 ZG' : '智能问数'"
          />
        </button>
      </div>

      <nav class="chat-home-nav">
        <button
          v-for="item in quickNavItems"
          :key="item.key"
          :class="['chat-home-nav__item', { 'is-active': item.key === 'new-chat' && !isConversationMode }]"
          type="button"
          @click="item.key === 'new-chat' ? startNewChat() : undefined"
        >
          <span class="chat-home-nav__leading">
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-if="sidebarExpanded">{{ item.label }}</span>
          </span>
        </button>
      </nav>

      <div v-if="sidebarExpanded" class="chat-home-sidebar__section chat-home-sidebar__section--models">
        <div class="chat-home-sidebar__header">
          <span>模型</span>
        </div>
        <div class="chat-home-model-inline">
          <span class="chat-home-model-inline__dot"></span>
          <span>{{ selectedModelLabel }}</span>
        </div>
      </div>

      <div v-if="sidebarExpanded" class="chat-home-sidebar__section">
        <div class="chat-home-sidebar__header"><span>分组</span></div>
        <div class="chat-home-group-label">静态对话</div>
        <div class="chat-home-group-label chat-home-group-label--muted">
          共 {{ pinnedConversations.length }} 个会话
        </div>

        <button
          v-for="conversation in pinnedConversations"
          :key="conversation.id"
          :class="['chat-home-thread', { 'is-current': activeSessionId === conversation.id }]"
          type="button"
          @click="selectSession(conversation.id)"
        >
          <div class="chat-home-thread__leading">
            <div class="chat-home-thread__copy">
              <strong>{{ conversation.title }}</strong>
            </div>
          </div>
          <span class="chat-home-thread__meta">{{ conversation.meta }}</span>
          <button
            v-if="activeSessionId === conversation.id"
            class="chat-home-thread__more"
            type="button"
            aria-label="More"
          >
            <el-icon><MoreFilled /></el-icon>
          </button>
        </button>
      </div>

      <div class="chat-home-sidebar__footer">
        <button class="chat-home-user" type="button">
          <div class="chat-home-user__avatar">周</div>
          <div v-if="sidebarExpanded" class="chat-home-user__copy">
            <strong>周志通</strong>
          </div>
        </button>
      </div>
    </aside>

    <button
      class="chat-home-shell__toggle"
      type="button"
      :aria-label="sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
      :title="sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
      @click="sidebarExpanded = !sidebarExpanded"
    >
      <el-icon>
        <ArrowLeftBold v-if="sidebarExpanded" />
        <ArrowRightBold v-else />
      </el-icon>
    </button>

    <main class="chat-home-main">
      <header class="chat-home-topbar">
        <div class="chat-home-topbar__left">
          <div class="chat-home-model-switcher">
            <span>{{ selectedModelLabel }}</span>
            <span class="chat-home-model-switcher__caret">⌄</span>
            <div class="chat-home-model-switcher__simulate-group">
              <button
                class="chat-home-model-switcher__simulate"
                type="button"
                @click="toggleProtocolSimulation"
              >
                {{ resolveSimulationButtonText() }}
              </button>
              <button
                class="chat-home-model-switcher__simulate"
                type="button"
                @click="restartProtocolSimulation"
              >
                重新开始
              </button>
            </div>
          </div>
        </div>

        <div class="chat-home-topbar__right">
          <button class="ghost-icon-button" type="button"><el-icon><MoreFilled /></el-icon></button>
          <button class="ghost-icon-button" type="button"><el-icon><Operation /></el-icon></button>
          <button class="avatar-chip" type="button">
            <el-icon><UserFilled /></el-icon>
          </button>
        </div>
      </header>

      <div class="chat-home-content">
        <section v-if="!isConversationMode" class="chat-home-welcome">
          <div class="chat-home-welcome-stage">
            <div class="chat-home-welcome-model">
              <div class="chat-home-welcome-model__avatar">oi</div>
              <div class="chat-home-welcome-model__name">{{ selectedModelLabel }}</div>
            </div>
            <div class="chat-home-composer chat-home-composer--floating chat-home-composer--welcome">
              <textarea
                ref="welcomeTextarea"
                v-model="prompt"
                placeholder="有什么我能帮您的么?"
                rows="1"
                @keydown="handlePromptKeydown"
              ></textarea>
              <div class="chat-home-composer__footer">
                <div class="chat-home-composer__tools">
                  <button class="composer-tool-button" type="button"><span>+</span></button>
                  <button class="composer-tool-button" type="button">
                    <el-icon><Operation /></el-icon>
                  </button>
                </div>
                <div class="chat-home-composer__actions">
                  <button class="composer-icon-button" type="button"><el-icon><Microphone /></el-icon></button>
                  <button class="composer-send-button" type="button" :disabled="isStreaming" @click="handlePrimaryAction">
                    <el-icon><Promotion /></el-icon>
                  </button>
                </div>
              </div>
            </div>

            <div class="chat-home-welcome-suggestions">
              <div class="chat-home-welcome-suggestions__header">建议</div>
              <button
                v-for="item in welcomeSuggestions"
                :key="item.title"
                class="chat-home-welcome-suggestion"
                type="button"
                @click="applySuggestion(item.prompt)"
              >
                <strong>{{ item.title }}</strong>
                <span>{{ item.subtitle }}</span>
              </button>
            </div>
          </div>
        </section>

        <section v-else class="chat-home-conversation">
          <div class="chat-home-center-column chat-home-center-column--conversation">
            <div v-if="chatMessages.length === 0" class="chat-home-assistant">
              <div class="chat-home-assistant__avatar">pr</div>
              <div class="chat-home-assistant__body">
                <div class="chat-home-assistant__title">{{ selectedModelLabel }}</div>
                <div class="chat-home-assistant__meta">{{ currentSessionName }}</div>
                <div class="chat-home-assistant__text">你好！这是静态数据测试会话。</div>
                <div class="chat-home-assistant__actions">
                  <button
                    v-for="index in 6"
                    :key="index"
                    class="ghost-inline-icon"
                    type="button"
                  >
                    <span></span>
                  </button>
                </div>
              </div>
            </div>

            <div class="chat-home-message-list">
              <article
                v-for="message in chatMessages"
                :key="message.id"
                :class="['chat-home-message', `is-${message.role}`]"
              >
                <template v-if="message.role === 'assistant'">
                  <div class="chat-home-message__assistant-row">
                    <div class="chat-home-assistant__avatar chat-home-assistant__avatar--small">pr</div>
                    <div class="chat-home-message__assistant-copy">
                      <div class="chat-home-message__assistant-name">{{ selectedModelLabel }}</div>
                      <div v-if="message.thinking?.length" class="chat-home-thinking">
                        <button
                          class="chat-home-thinking__summary"
                          type="button"
                          @click="openThinkingDrawer(message.thinking)"
                        >
                          <span
                            v-if="message.thinkingStatus === 'running'"
                            class="chat-home-thinking__running"
                            aria-hidden="true"
                          ></span>
                          <span>{{ resolveThinkingTitle(message) }}</span>
                          <small>{{ resolveThinkingMeta(message) }}</small>
                          <el-icon><ArrowDown /></el-icon>
                        </button>
                      </div>
                      <div
                        v-if="message.content"
                        class="chat-home-message__assistant-text"
                        v-html="renderMarkdown(message.content)"
                      ></div>
                      <DashboardCanvasPreview v-if="message.canvas" />
                      <div
                        v-if="message.id === lastAssistantMessageId"
                        class="chat-home-feedback"
                        aria-label="回复操作"
                      >
                        <button class="chat-home-feedback__button" type="button">
                          <svg class="chat-home-feedback__icon" viewBox="0 0 16 16" aria-hidden="true">
                            <path d="M6.2 6.3 7 2.5c.1-.5.5-.9 1-.9.7 0 1.2.5 1.2 1.2v2.5h3.1c.8 0 1.4.7 1.3 1.5l-.6 4.5c-.1.9-.9 1.5-1.8 1.5H6.2V6.3Z" />
                            <path d="M2.3 6.4h2.4v6.4H2.3V6.4Z" />
                          </svg>
                          <span>喜欢</span>
                        </button>
                        <button class="chat-home-feedback__button" type="button">
                          <el-icon><ChatDotRound /></el-icon>
                          <span>反馈</span>
                        </button>
                      </div>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <div class="chat-home-message__user-stack">
                    <div class="chat-home-message__user-row">
                      <div class="chat-home-user-bubble">{{ message.content }}</div>
                    </div>
                    <div class="chat-home-message__user-meta">
                      <span>{{ message.createdAt }}</span>
                      <button type="button" @click="copyMessageContent(message.content)">
                        <el-icon><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                    </div>
                  </div>
                </template>
              </article>

            </div>

            <div class="chat-home-followups">
              <button
                v-for="card in welcomeCards.slice(0, 3)"
                :key="card"
                class="chat-home-followup-line"
                type="button"
                @click="applySuggestion(card)"
              >
                {{ card }}
              </button>
            </div>
          </div>

          <div class="chat-home-composer chat-home-composer--floating chat-home-composer--conversation">
            <textarea
              ref="conversationTextarea"
              v-model="prompt"
              placeholder="输入消息"
              rows="1"
              @keydown="handlePromptKeydown"
            ></textarea>
            <div class="chat-home-composer__footer">
              <div class="chat-home-composer__tools">
                <button class="composer-tool-button" type="button"><span>+</span></button>
                <button class="composer-tool-button" type="button">
                  <el-icon><Operation /></el-icon>
                </button>
              </div>
              <div class="chat-home-composer__actions">
                <button class="composer-icon-button" type="button"><el-icon><Microphone /></el-icon></button>
                <button class="composer-send-button" type="button" :disabled="isStreaming" @click="handlePrimaryAction">
                  <el-icon><Promotion /></el-icon>
                </button>
              </div>
            </div>
          </div>
        </section>

        <Transition name="chat-thinking-drawer" @after-leave="handleThinkingDrawerAfterLeave">
          <aside
            v-if="thinkingDrawerVisible"
            class="chat-home-thinking-drawer"
            aria-label="思考过程"
          >
            <header class="chat-home-thinking-drawer__header">
              <div>
                <h2>思考过程</h2>
                <p>{{ resolveThinkingMeta(chatMessages.find((message) => message.thinking?.length)) }} · {{ activeThinking.length }} 个活动</p>
              </div>
              <button type="button" aria-label="关闭思考过程" @click="closeThinkingDrawer">×</button>
            </header>

            <div class="chat-home-thinking-drawer__body">
            <section class="chat-home-thinking-task-nodes" aria-label="任务节点">
              <div class="chat-home-thinking-task-nodes__title">
                <span>任务节点</span>
                <strong>智能问数</strong>
              </div>
              <div class="chat-home-thinking-task-nodes__grid">
                <div
                  v-for="node in thinkingTaskNodes"
                  :key="node.id"
                  :class="['chat-home-thinking-task-node', `is-${node.status}`]"
                  :title="node.description"
                >
                  <span class="chat-home-thinking-task-node__status" aria-hidden="true"></span>
                  <div class="chat-home-thinking-task-node__copy">
                    <strong><span>{{ node.step }}</span>{{ node.title }}</strong>
                    <p>{{ node.description }}</p>
                  </div>
                </div>
              </div>
            </section>

            <div class="chat-home-thinking__content chat-home-thinking__content--drawer">
              <div
                v-for="activity in activeThinking"
                :key="activity.id"
                :class="['chat-home-thinking-activity', `is-${activity.status}`]"
              >
                <span class="chat-home-thinking-activity__marker"></span>
                <div class="chat-home-thinking-activity__header">
                  <strong>{{ activity.title }}</strong>
                  <span>{{ getThinkingStatusText(activity.status) }}</span>
                </div>
                <p>{{ activity.description }}</p>
                <div v-if="activity.sources?.length" class="chat-home-thinking-sources">
                  <span
                    v-for="source in activity.sources"
                    :key="source.id"
                    class="chat-home-thinking-source"
                  >
                    <span class="chat-home-thinking-source__icon">{{ source.icon }}</span>
                    <span>{{ source.label }}</span>
                  </span>
                </div>
                <details v-if="activity.details?.length" class="chat-home-thinking-submodule">
                  <summary class="chat-home-thinking-submodule__summary">
                    <span>再显示 {{ activity.details.length }} 个结果</span>
                    <el-icon><ArrowDown /></el-icon>
                  </summary>
                  <div class="chat-home-thinking-submodule__content">
                    <div
                      v-for="detail in activity.details"
                      :key="detail.id"
                      class="chat-home-thinking-detail"
                    >
                      <strong>{{ detail.title }}</strong>
                      <p>{{ detail.content }}</p>
                      <a v-if="detail.url" :href="detail.url" target="_blank" rel="noreferrer">
                        {{ detail.url }}
                      </a>
                    </div>
                  </div>
                </details>
              </div>
            </div>
            </div>
          </aside>
        </Transition>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
@use '../../../styles/chat-home';
</style>
