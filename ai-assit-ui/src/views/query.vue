<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Connection, Microphone, Picture, Promotion, Setting } from '@element-plus/icons-vue'

const models = [
  { value: 'gpt-4.1', label: 'GPT-4.1' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'deepseek-r1', label: 'DeepSeek-R1' }
]

const selectedModel = ref(models[0].value)
const prompt = ref('')
const executions = ref([
  {
    title: '系统启动',
    detail: '等待用户输入问题，准备检索元数据、指标定义和可用数据集。',
    tone: 'neutral',
    active: false
  },
  {
    title: '上下文装载',
    detail: '已加载员工主题域、考勤指标、绩效口径和成本分析规则。',
    tone: 'info',
    active: true
  }
])

const stages = ref([
  { name: '需求理解', desc: '解析用户问题，识别维度、指标和时间范围。', status: 'done' },
  { name: '数据源选择', desc: '定位可用表、主题域和权限范围。', status: 'running' },
  { name: 'SQL / DSL 生成', desc: '生成查询语句和聚合逻辑。', status: 'pending' },
  { name: '结果校验', desc: '检查口径一致性、异常值和结果完整性。', status: 'pending' },
  { name: '结论输出', desc: '返回摘要、图表建议和下一步追问方向。', status: 'pending' }
])
const historyCollapsed = ref(false)
const previewFullscreen = ref(false)
const historyKeyword = ref('')
const historyList = ref([
  { title: '最近30天人力成本波动分析', time: '今天 14:32', active: true },
  { title: '客服团队夜班补贴异常排查', time: '今天 11:08', active: false },
  { title: '研发中心编制与预算偏差对比', time: '昨天 18:45', active: false },
  { title: '销售部门提成成本趋势复盘', time: '昨天 16:20', active: false },
  { title: '月度绩效分布与离职率联动', time: '06-01 09:14', active: false }
])
const resultRows = ref([
  { dept: '研发中心', cost: '¥2.48M', change: '+12.4%', risk: '扩招带动上涨' },
  { dept: '销售一部', cost: '¥1.96M', change: '+8.1%', risk: '提成支出增加' },
  { dept: '客服团队', cost: '¥1.18M', change: '+15.7%', risk: '夜班补贴异常' },
  { dept: '职能支持', cost: '¥0.92M', change: '+3.2%', risk: '基本稳定' }
])
const pieSegments = [
  { label: '研发', value: 38, color: '#2563eb' },
  { label: '销售', value: 30, color: '#0ea5e9' },
  { label: '客服', value: 18, color: '#10b981' },
  { label: '职能', value: 14, color: '#f59e0b' }
]
const barSeries = [
  { label: '研发', value: 82 },
  { label: '销售', value: 64 },
  { label: '客服', value: 71 },
  { label: '职能', value: 48 }
]

const placeholder = '例如：帮我分析最近 30 天各部门人力成本变化，并找出异常波动原因'
const composerInput = ref(null)
const minInputHeight = 48
const maxInputHeight = 320

const stageSummary = computed(() => {
  const total = stages.value.length
  const done = stages.value.filter((item) => item.status === 'done').length
  return `${done}/${total} 阶段已完成`
})

const filteredHistoryList = computed(() => {
  const keyword = historyKeyword.value.trim().toLowerCase()
  if (!keyword) return historyList.value
  return historyList.value.filter((item) => item.title.toLowerCase().includes(keyword))
})

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
          </article>

          <article class="panel-card stages-panel">
            <div class="panel-head">
              <div>
                <p class="panel-eyebrow">阶段任务</p>
                <h2>任务拆解</h2>
              </div>
              <span class="panel-status">{{ stageSummary }}</span>
            </div>

            <div class="stages-list">
              <article
                v-for="(stage, index) in stages"
                :key="stage.name"
                class="stage-item"
                :class="`status-${stage.status}`"
              >
                <div class="stage-index">{{ index + 1 }}</div>
                <div class="stage-body">
                  <div class="stage-title-row">
                    <h3>{{ stage.name }}</h3>
                    <span class="stage-state" :class="`state-${stage.status}`" :aria-label="stage.status"></span>
                  </div>
                  <p>{{ stage.desc }}</p>
                </div>
              </article>
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
                  <div
                    class="pie-chart"
                    :style="{
                      background: `conic-gradient(${pieSegments.map((item, index) => `${item.color} ${pieSegments.slice(0, index).reduce((sum, current) => sum + current.value, 0)}% ${pieSegments.slice(0, index + 1).reduce((sum, current) => sum + current.value, 0)}%`).join(', ')})`
                    }"
                  ></div>
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
              <div
                class="pie-chart large"
                :style="{
                  background: `conic-gradient(${pieSegments.map((item, index) => `${item.color} ${pieSegments.slice(0, index).reduce((sum, current) => sum + current.value, 0)}% ${pieSegments.slice(0, index + 1).reduce((sum, current) => sum + current.value, 0)}%`).join(', ')})`
                }"
              ></div>
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
