<script setup>
import { Connection, Microphone, Picture, Promotion, Setting } from '@element-plus/icons-vue'
import { useQueryAssistantPage } from '../service'

const {
  models,
  selectedModel,
  prompt,
  executions,
  stages,
  historyCollapsed,
  previewFullscreen,
  historyKeyword,
  filteredHistoryList,
  historyList,
  composerInput,
  stageSummary,
  pieSegments,
  pieBackground,
  barSeries,
  resultRows,
  placeholder,
  submitQuery,
  createConversation,
  resizeComposer
} = useQueryAssistantPage()
</script>

<template>
  <main class="page query-page">
    <section class="query-shell" :class="{ collapsed: historyCollapsed }">
      <aside class="history-panel" :class="{ collapsed: historyCollapsed }">
        <div class="history-head">
          <button class="panel-action history-toggle" type="button" @click="historyCollapsed = !historyCollapsed">
            {{ historyCollapsed ? '展开' : '收起' }}
          </button>

          <div v-if="!historyCollapsed" class="history-title">
            <p class="panel-eyebrow">历史对话</p>
            <h2>会话列表</h2>
          </div>

          <div v-if="historyCollapsed" class="history-head-actions">
            <button class="panel-action icon-only" type="button" @click="createConversation" aria-label="新增对话">新建</button>
            <button class="panel-action icon-only" type="button" aria-label="搜索对话">搜索</button>
          </div>
        </div>

        <div v-if="!historyCollapsed" class="history-toolbar">
          <button class="history-primary-btn" type="button" @click="createConversation">+ 新增对话</button>
          <input v-model="historyKeyword" class="history-search" placeholder="搜索历史对话" />
        </div>

        <div v-if="!historyCollapsed" class="history-list">
          <button
            v-for="item in filteredHistoryList"
            :key="`${item.title}-${item.time}`"
            class="history-item"
            :class="{ active: item.active }"
            type="button"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.time }}</span>
          </button>
        </div>
      </aside>

      <div class="query-main">
        <section class="query-workbench">
          <article class="panel-card execution-panel">
            <div class="panel-head">
              <div>
                <p class="panel-eyebrow">执行内容</p>
                <h2>AI 执行轨迹</h2>
              </div>
              <span class="panel-status">实时</span>
            </div>

            <div class="execution-panel-body">
              <div class="execution-list">
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

              <div class="stage-strip">
                <div class="stage-strip-head">
                  <p class="panel-eyebrow">阶段任务</p>
                  <span class="panel-status stage-summary">{{ stageSummary }}</span>
                </div>

                <div class="stage-mini-list">
                  <article
                    v-for="(stage, index) in stages"
                    :key="stage.name"
                    class="stage-mini"
                    :class="`status-${stage.status}`"
                    :title="`${stage.name}：${stage.desc}`"
                  >
                    <span class="stage-mini-index">{{ index + 1 }}</span>
                    <span class="stage-mini-text">{{ stage.name }}：{{ stage.desc }}</span>
                    <span class="stage-mini-dot" :class="`state-${stage.status}`" :aria-label="stage.status"></span>
                  </article>
                </div>
              </div>
            </div>
          </article>

          <article class="panel-card preview-panel">
            <div class="panel-head">
              <div>
                <p class="panel-eyebrow">结果预览</p>
                <h2>AI 执行结果预览</h2>
              </div>
              <button class="panel-action" type="button" @click="previewFullscreen = true">全屏</button>
            </div>

            <div class="preview-stack">
              <section class="preview-block">
                <div class="preview-block-head">
                  <h3>结果列表</h3>
                  <span class="preview-hint">默认预览</span>
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
              </section>

              <section class="charts-grid">
                <article class="preview-block chart-card">
                  <div class="preview-block-head">
                    <h3>饼图</h3>
                  </div>
                  <div class="pie-chart" :style="{ background: pieBackground }"></div>
                  <div class="chart-legend">
                    <div v-for="item in pieSegments" :key="item.label" class="legend-item">
                      <span class="legend-dot" :style="{ background: item.color }"></span>
                      <span>{{ item.label }} {{ item.value }}%</span>
                    </div>
                  </div>
                </article>

                <article class="preview-block chart-card">
                  <div class="preview-block-head">
                    <h3>柱状图</h3>
                  </div>
                  <div class="bar-chart">
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
          </article>
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
