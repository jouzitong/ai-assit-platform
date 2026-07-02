<script setup>
import { computed, ref, watch } from 'vue'
import { Connection, Microphone, Picture, Promotion, Setting } from '@element-plus/icons-vue'
import { useQueryAssistantPage } from '../service'

const {
  models,
  selectedModel,
  prompt,
  executions,
  stages,
  previewFullscreen,
  filteredHistoryList,
  composerInput,
  stageSummary,
  pieSegments,
  pieBackground,
  barSeries,
  resultRows,
  placeholder,
  submitQuery,
  resizeComposer
} = useQueryAssistantPage()

const processExpanded = ref(true)

const activeConversation = computed(() =>
  filteredHistoryList.value.find((item) => item.active) || filteredHistoryList.value[0] || null
)

const activeExecution = computed(() =>
  executions.value.find((item) => item.active) || executions.value[0] || null
)

const completedStageCount = computed(() =>
  stages.value.filter((item) => item.status === 'done').length
)

const isProcessing = computed(() =>
  stages.value.some((item) => item.status === 'running') || executions.value.some((item) => item.active)
)

watch(
  isProcessing,
  (running) => {
    processExpanded.value = running
  },
  { immediate: true }
)
</script>

<template>
  <main class="page query-page">
    <section class="query-shell">
      <div class="query-main">
        <section class="conversation-stage">
          <article class="conversation-hero">
            <div class="conversation-hero-copy">
              <p class="panel-eyebrow">当前会话</p>
              <h1>{{ activeConversation?.title || '智能问数' }}</h1>
              <p class="conversation-summary">
                {{ activeConversation?.summary || '继续输入问题，系统会沿用当前会话上下文继续分析。' }}
              </p>
            </div>

            <div class="conversation-hero-actions">
              <span class="conversation-tag">{{ activeConversation?.tag || '智能问数' }}</span>
              <button class="inline-toggle" type="button" @click="processExpanded = !processExpanded">
                {{ processExpanded ? '收起过程' : '查看过程' }}
              </button>
            </div>
          </article>

          <section class="result-stage">
            <div class="result-stage-head">
              <div>
                <p class="panel-eyebrow">执行结果</p>
                <h2>AI 执行结果预览</h2>
                <p class="result-stage-summary">先看结论，需要时再展开过程和完整图表。</p>
              </div>
              <button class="inline-action" type="button" @click="previewFullscreen = true">全屏查看</button>
            </div>

            <section class="result-overview-grid">
              <article class="result-overview-card">
                <span class="result-overview-label">已完成阶段</span>
                <strong>{{ completedStageCount }}/{{ stages.length }}</strong>
              </article>
              <article class="result-overview-card">
                <span class="result-overview-label">结果记录数</span>
                <strong>{{ resultRows.length }}</strong>
              </article>
              <article class="result-overview-card">
                <span class="result-overview-label">当前模型</span>
                <strong>{{ selectedModel }}</strong>
              </article>
            </section>

            <section class="result-block">
              <div class="result-table-shell">
                <div class="result-table-headline">
                  <strong>结果列表</strong>
                  <span>主回答摘要</span>
                </div>
                <div class="result-table">
                  <div class="result-row result-head">
                    <span>部门</span>
                    <span>人力成本</span>
                    <span>环比</span>
                    <span>说明</span>
                  </div>
                  <div v-for="row in resultRows" :key="row.dept" class="result-row">
                    <span>{{ row.dept }}</span>
                    <span>{{ row.cost }}</span>
                    <span>{{ row.change }}</span>
                    <span>{{ row.risk }}</span>
                  </div>
                </div>
              </div>

              <details class="process-fold" :open="processExpanded">
                <summary class="process-summary">
                  <div class="process-summary-main">
                    <span class="process-summary-label">AI思考过程</span>
                    <strong>{{ activeExecution?.title || '执行计划生成' }}</strong>
                    <span class="process-summary-text">
                      {{ activeExecution?.detail || '系统正在整理分析步骤。' }}
                    </span>
                  </div>
                  <span class="process-summary-meta">{{ stageSummary }}</span>
                </summary>

                <div class="process-body">
                  <div class="composer-status-chips">
                    <article
                      v-for="(stage, index) in stages"
                      :key="stage.name"
                      class="stage-chip compact"
                      :class="`status-${stage.status}`"
                      :title="`${stage.name}：${stage.desc}`"
                    >
                      <span class="stage-chip-index">{{ index + 1 }}</span>
                      <span class="stage-chip-text">{{ stage.name }}</span>
                      <span class="stage-chip-state" :class="`state-${stage.status}`"></span>
                    </article>
                  </div>

                  <div class="execution-list compact">
                    <article
                      v-for="(item, index) in executions"
                      :key="`${item.title}-${index}`"
                      class="execution-item"
                      :class="`tone-${item.tone}`"
                    >
                      <div class="execution-dot" :class="{ active: item.active }"></div>
                      <div>
                        <h3>{{ item.title }}</h3>
                        <p>{{ item.detail }}</p>
                      </div>
                    </article>
                  </div>

                  <section class="charts-inline">
                    <article class="chart-inline-card">
                      <div class="chart-inline-head">
                        <strong>饼图</strong>
                      </div>
                      <div class="pie-chart" :style="{ background: pieBackground }"></div>
                      <div class="chart-legend">
                        <div v-for="item in pieSegments" :key="item.label" class="legend-item">
                          <span class="legend-dot" :style="{ background: item.color }"></span>
                          <span>{{ item.label }} {{ item.value }}%</span>
                        </div>
                      </div>
                    </article>

                    <article class="chart-inline-card">
                      <div class="chart-inline-head">
                        <strong>柱状图</strong>
                      </div>
                      <div class="bar-chart compact">
                        <div v-for="item in barSeries" :key="item.label" class="bar-item">
                          <div class="bar-track">
                            <div class="bar-fill" :style="{ height: `${item.value}%` }"></div>
                          </div>
                          <strong>{{ item.label }}</strong>
                        </div>
                      </div>
                    </article>
                  </section>
                </div>
              </details>
            </section>
          </section>
        </section>

        <section class="composer-status">
          <div class="composer-status-head">
            <div>
              <p class="panel-eyebrow">执行状态</p>
              <strong>{{ activeExecution?.title || '执行计划生成' }}</strong>
            </div>
            <span class="panel-status">{{ stageSummary }}</span>
          </div>
          <p class="composer-status-detail">
            {{ activeExecution?.detail || '系统正在整理分析步骤。' }}
          </p>
        </section>

        <section class="query-composer">
          <form class="composer-form" @submit.prevent="submitQuery">
            <textarea
              ref="composerInput"
              v-model="prompt"
              class="composer-input"
              :placeholder="placeholder"
              rows="2"
              @input="resizeComposer"
            />

            <div class="composer-toolbar">
              <div class="toolbar-left">
                <button class="toolbar-icon-btn" type="button" aria-label="工具">
                  <Setting class="toolbar-svg" />
                </button>
                <button class="toolbar-icon-btn" type="button" aria-label="上传文件">
                  <Picture class="toolbar-svg" />
                </button>
              </div>

              <div class="toolbar-right">
                <button class="toolbar-icon-btn" type="button" aria-label="语音输入">
                  <Microphone class="toolbar-svg" />
                </button>
                <select id="model-select" v-model="selectedModel" class="model-select">
                  <option v-for="model in models" :key="model.value" :value="model.value">
                    {{ model.label }}
                  </option>
                </select>
                <button class="toolbar-icon-btn" type="button" aria-label="模型连接">
                  <Connection class="toolbar-svg" />
                </button>
                <button class="composer-submit icon-submit" type="submit" aria-label="发送">
                  <Promotion class="toolbar-svg submit-svg" />
                </button>
              </div>
            </div>
          </form>
        </section>
      </div>
    </section>

    <div v-if="previewFullscreen" class="preview-modal">
      <div class="preview-modal-mask" @click="previewFullscreen = false"></div>
      <section class="preview-modal-card">
        <div class="preview-modal-head">
          <div>
            <p class="panel-eyebrow">结果预览</p>
            <h2>AI 执行结果预览</h2>
          </div>
          <button class="panel-action" type="button" @click="previewFullscreen = false">退出全屏</button>
        </div>

        <div class="preview-modal-body">
          <section class="preview-block">
            <div class="preview-block-head">
              <h3>结果列表</h3>
              <span class="preview-hint">全屏视图</span>
            </div>
            <div class="result-table">
              <div class="result-row result-head">
                <span>部门</span>
                <span>人力成本</span>
                <span>环比</span>
                <span>说明</span>
              </div>
              <div v-for="row in resultRows" :key="`modal-${row.dept}`" class="result-row">
                <span>{{ row.dept }}</span>
                <span>{{ row.cost }}</span>
                <span>{{ row.change }}</span>
                <span>{{ row.risk }}</span>
              </div>
            </div>
          </section>

          <section class="charts-grid modal-charts-grid">
            <article class="preview-block chart-card">
              <div class="preview-block-head">
                <h3>饼图</h3>
              </div>
              <div class="pie-chart large" :style="{ background: pieBackground }"></div>
              <div class="chart-legend">
                <div v-for="item in pieSegments" :key="`modal-${item.label}`" class="legend-item">
                  <span class="legend-dot" :style="{ background: item.color }"></span>
                  <span>{{ item.label }} {{ item.value }}%</span>
                </div>
              </div>
            </article>

            <article class="preview-block chart-card">
              <div class="preview-block-head">
                <h3>柱状图</h3>
              </div>
              <div class="bar-chart large">
                <div v-for="item in barSeries" :key="`modal-${item.label}`" class="bar-item">
                  <div class="bar-track">
                    <div class="bar-fill" :style="{ height: `${item.value}%` }"></div>
                  </div>
                  <strong>{{ item.label }}</strong>
                </div>
              </div>
            </article>
          </section>
        </div>
      </section>
    </div>
  </main>
</template>
