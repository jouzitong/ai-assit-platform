<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Pointer, Warning, Share, Plus, Grid, FullScreen, Download, Star } from '@element-plus/icons-vue'
import AiChat from '../../../../../components/AiChat.vue'
import { buildAssistantQueryViewModel } from '../data'
import { useHomeOverviewPage } from '../service'

const {
  models,
  selectedModel,
  prompt,
  executions,
  stages,
  previewFullscreen,
  filteredHistoryList,
  routeSessionCode,
  activeConversationRound,
  activeConversationUserInput,
  activeConversationSummary,
  activeArtifactNotice,
  stageSummary,
  pieSegments,
  pieBackground,
  barSeries,
  resultRows,
  placeholder,
  submitQuery
} = useHomeOverviewPage()

const aiChatRef = ref(null)
const processExpanded = ref(true)
const selectedBizType = ref('')
const pieSegmentList = computed(() => (Array.isArray(pieSegments) ? pieSegments : []))
const barSeriesList = computed(() => (Array.isArray(barSeries) ? barSeries : []))
const resultRowList = computed(() => (Array.isArray(resultRows) ? resultRows : []))
const placeholderText = computed(() => (typeof placeholder === 'string' ? placeholder : ''))
const bizTypeOptions = [
  { value: 'query', label: '智能问数' },
  { value: 'report', label: '报告分析' },
  { value: 'app', label: '应用生成' }
]

const suggestionCards = [
  {
    title: '部门成本波动',
    desc: '分析最近 30 天各部门人力成本变化，定位异常上涨原因。'
  },
  {
    title: '组织编制变化',
    desc: '对比研发、销售团队编制变化和预算偏差，输出重点风险。'
  },
  {
    title: '绩效与离职率',
    desc: '观察绩效分布变化与离职率联动，找出高风险团队。'
  },
  {
    title: '夜班补贴排查',
    desc: '检查客服团队夜班补贴规则、发放范围与异常人员。'
  }
]

const pageModel = computed(() =>
  buildAssistantQueryViewModel({
    conversations: filteredHistoryList.value,
    stages: stages.value,
    executions: executions.value,
    resultRows: resultRowList.value,
    selectedModel: selectedModel.value,
    prompt: prompt.value,
    placeholder: placeholderText.value
  })
)

const currentRound = computed(() => pageModel.value.currentRound)
const activeConversation = computed(() => pageModel.value.session)
const showWelcome = computed(() => !routeSessionCode.value && !activeConversationRound.value)
const activeExecution = computed(() => executions.value.find((item) => item.active) || executions.value[0] || null)
const detailUserInput = computed(() => activeConversationUserInput.value || currentRound.value?.userInput || '')
const detailSummary = computed(() => activeConversationSummary.value || activeConversation.value.summary || activeExecution.value?.detail || '系统正在整理查询结论。')
const artifactNotice = computed(() => activeArtifactNotice.value || '')
const modelLabel = computed(() => {
  const target = models.value.find((item) => item.value === selectedModel.value)
  return target?.label || selectedModel.value || '智能问数'
})
const resultTotalText = computed(() => artifactNotice.value || `${resultRowList.value.length} 条结果`)

watch(
  () => pageModel.value.currentRound?.progress?.isProcessing,
  (running) => {
    processExpanded.value = Boolean(running)
  },
  { immediate: true }
)

watch(previewFullscreen, (value) => {
  document.body.classList.toggle('canvas-fullscreen', Boolean(value))
})

onMounted(() => {
  aiChatRef.value?.syncTextareaHeight?.()
})

onBeforeUnmount(() => {
  document.body.classList.remove('canvas-fullscreen')
})

function stageStatusLabel(status) {
  if (status === 'done') return '已完成'
  if (status === 'running') return '进行中'
  return '待执行'
}

function handleSelectSuggestion(text) {
  prompt.value = text
  nextTick(() => {
    aiChatRef.value?.focusInput?.()
  })
}

function handleSubmit() {
  submitQuery()
}

function handleUploadAttachment() {
  window.alert('附件上传入口待接入')
}

function handleResultAction(action) {
  const actionTextMap = {
    like: '已记录正向反馈',
    dislike: '已记录改进反馈',
    feedback: '已打开结果反馈入口',
    branch: '已创建基于本次结果的新分支任务'
  }
  window.alert(actionTextMap[action] || '操作已触发')
}

function handleCanvasAction(action) {
  if (action === 'fullscreen') {
    togglePreviewFullscreen()
    return
  }
  const actionTextMap = {
    export: '导出能力待接入',
    share: '分享能力待接入',
    favorite: '收藏能力待接入'
  }
  window.alert(actionTextMap[action] || '操作已触发')
}

function togglePreviewFullscreen() {
  previewFullscreen.value = !previewFullscreen.value
}
</script>

<template>
  <main class="page query-page">
    <div class="app query-app-shell">
      <section class="main">
        <AiChat
          ref="aiChatRef"
          layout="workspace"
          v-model="prompt"
          :show-welcome="showWelcome"
          :placeholder="placeholderText"
          submit-mode="emit"
          @submit="handleSubmit"
        >
          <template #welcome>
            <section class="welcome">
              <h1>让 AI 直接帮你完成问数分析</h1>
              <p>{{ placeholderText }}</p>
            </section>

            <section class="suggestions">
              <button
                v-for="item in suggestionCards"
                :key="item.title"
                class="suggestion-card"
                type="button"
                @click="handleSelectSuggestion(item.desc)"
              >
                <strong>{{ item.title }}</strong>
                <span>{{ item.desc }}</span>
              </button>
            </section>
          </template>

          <template #conversation>
            <div class="message-list">
                  <article class="message user-message">
                <div class="message-body">
                  <div class="message-text">{{ detailUserInput }}</div>
                </div>
              </article>

              <article class="message">
                <div class="message-avatar ai">AI</div>
                <div class="message-body">
                  <div class="message-name">{{ modelLabel }}</div>
                  <div class="ai-response">
                    <section class="ai-section ai-summary">
                      <div class="ai-section-title">分析摘要</div>
                      <div class="ai-section-body">
                        {{ detailSummary }}
                      </div>
                    </section>

                    <details class="ai-section ai-thoughts" :open="processExpanded">
                      <summary>
                        <div class="ai-thoughts-head">
                          <div class="ai-section-title">分析过程</div>
                          <span class="ai-processing-time">{{ stageSummary }}</span>
                        </div>
                      </summary>
                      <div class="ai-thoughts-body">
                        <div class="ai-progress">
                          <div v-for="stage in stages" :key="stage.name" class="ai-progress-item">
                            <span class="ai-progress-dot" :class="stage.status" />
                            <div class="ai-progress-content">
                              <div class="ai-progress-label">
                                {{ stage.name }} · {{ stageStatusLabel(stage.status) }}
                              </div>
                              <div class="ai-progress-desc">{{ stage.desc }}</div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </details>

                    <section class="ai-result" :class="{ 'is-fullscreen': previewFullscreen }">
                      <div class="result-canvas-header">
                        <div>
                          <div class="result-canvas-title">{{ currentRound?.result?.title || 'AI 执行结果预览' }}</div>
                          <div class="result-canvas-summary">{{ resultTotalText }}</div>
                        </div>
                        <div class="result-canvas-actions">
                          <button class="result-canvas-btn" type="button" @click="handleCanvasAction('fullscreen')">
                            <el-icon><FullScreen /></el-icon>
                            <span>{{ previewFullscreen ? '退出全屏' : '全屏' }}</span>
                          </button>
                          <button class="result-canvas-btn" type="button" @click="handleCanvasAction('export')">
                            <el-icon><Download /></el-icon>
                            <span>导出</span>
                          </button>
                          <button class="result-canvas-btn" type="button" @click="handleCanvasAction('share')">
                            <el-icon><Share /></el-icon>
                            <span>分享</span>
                          </button>
                          <button class="result-canvas-btn" type="button" @click="handleCanvasAction('favorite')">
                            <el-icon><Star /></el-icon>
                            <span>收藏</span>
                          </button>
                        </div>
                      </div>

                      <div class="result-canvas-grid">
                        <div v-if="artifactNotice" class="ai-result-card canvas-list">
                          <strong>业务产物</strong>
                          <div class="result-list">
                            <div class="result-list-row">
                              <span>{{ artifactNotice }}</span>
                            </div>
                          </div>
                        </div>

                        <div class="ai-result-card canvas-list">
                          <strong>明细结果</strong>
                          <div class="result-list">
                            <div class="result-list-row header">
                              <span>部门</span>
                              <span>成本</span>
                              <span>变动</span>
                              <span>风险</span>
                            </div>
                            <div v-for="row in resultRowList" :key="row.dept" class="result-list-row">
                              <span>{{ row.dept }}</span>
                              <span>{{ row.cost }}</span>
                              <span>{{ row.change }}</span>
                              <span>{{ row.risk }}</span>
                            </div>
                          </div>
                        </div>

                        <div class="ai-result-card canvas-bar">
                          <strong>部门趋势对比</strong>
                          <div class="bar-chart">
                            <div v-for="item in barSeriesList" :key="item.label" class="bar-item">
                              <div class="bar-value">{{ item.value }}</div>
                              <div class="bar-track">
                                <div class="bar-fill" :style="{ height: `${item.value}%` }" />
                              </div>
                              <div class="bar-label">{{ item.label }}</div>
                            </div>
                          </div>
                        </div>

                        <div class="ai-result-card canvas-pie">
                          <strong>成本占比</strong>
                          <div class="pie-wrap">
                            <div class="pie-chart" :style="{ background: pieBackground }" />
                            <div class="pie-legend">
                              <div v-for="item in pieSegmentList" :key="item.label" class="pie-legend-item">
                                <div class="pie-legend-main">
                                  <span class="pie-dot" :style="{ background: item.color }" />
                                  <span>{{ item.label }}</span>
                                </div>
                                <span class="pie-legend-value">{{ item.value }}%</span>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </section>

                    <div class="ai-actions">
                      <button class="ai-action-btn icon-only" type="button" title="点赞" @click="handleResultAction('like')">
                        <el-icon><Pointer /></el-icon>
                      </button>
                      <button class="ai-action-btn icon-only" type="button" title="反馈" @click="handleResultAction('feedback')">
                        <el-icon><Warning /></el-icon>
                      </button>
                      <button class="ai-action-btn icon-only" type="button" title="分叉" @click="handleResultAction('branch')">
                        <el-icon><Share /></el-icon>
                      </button>
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </template>

          <template #composer-before>
            <section v-if="currentRound?.progress?.isProcessing" class="progress-dock">
              <details class="progress-dock-inner" :open="processExpanded">
                <summary class="progress-dock-header" @click.prevent="processExpanded = !processExpanded">
                  <div>
                    <div class="ai-section-title">处理中</div>
                    <div class="result-canvas-summary">{{ stageSummary }}</div>
                  </div>
                  <span class="progress-dock-toggle">⌄</span>
                </summary>
                <div class="progress-dock-body">
                  <div class="ai-progress">
                    <div v-for="stage in stages" :key="stage.name" class="ai-progress-item">
                      <span class="ai-progress-dot" :class="stage.status" />
                      <div class="ai-progress-content">
                        <div class="ai-progress-label">{{ stage.name }}</div>
                        <div class="ai-progress-desc">{{ stage.desc }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </details>
            </section>
          </template>

          <template #composer-tools>
            <button class="composer-icon-btn" type="button" title="上传附件" @click="handleUploadAttachment">
              <el-icon><Plus /></el-icon>
            </button>
            <label class="composer-select-shell" title="业务类型">
              <span class="composer-select-icon">
                <el-icon><Grid /></el-icon>
              </span>
              <select v-model="selectedBizType" class="composer-select biz-select">
                <option value=""> </option>
                <option v-for="item in bizTypeOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <select v-model="selectedModel" class="model-select">
              <option v-for="model in models" :key="model.value" :value="model.value">
                {{ model.label }}
              </option>
            </select>
          </template>

          <template #composer-hint>
            <span class="composer-hint">Enter 发送，Shift + Enter 换行</span>
          </template>
        </AiChat>
      </section>
    </div>

    <div class="canvas-backdrop" :class="{ show: previewFullscreen }" @click="togglePreviewFullscreen" />
  </main>
</template>
