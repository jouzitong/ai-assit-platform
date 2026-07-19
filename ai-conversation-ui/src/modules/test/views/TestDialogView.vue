<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  AppDialog,
  AppDrawer,
  LayoutFormGrid,
  LayoutFormGridItem,
  LayoutLabelValue,
  useAppConfirm,
  type AppOverlaySize,
} from '../../../components'

type DialogType = 'form' | 'list' | 'confirm' | 'drawer'

const dialogTypes: Array<{
  value: DialogType
  label: string
  component: string
  description: string
}> = [
  { value: 'form', label: '表单 / 详情', component: 'AppDialog', description: '通过动作模式支持编辑和只读详情' },
  { value: 'list', label: '数据列表', component: 'AppDialog', description: '在统一弹窗内容区组合表格或列表' },
  { value: 'confirm', label: '确认操作', component: 'AppDialog / appConfirm', description: '通过函数 API 快速发起二次确认' },
  { value: 'drawer', label: '抽屉', component: 'AppDrawer', description: '保留页面上下文的辅助任务' },
]

const activeType = ref<DialogType>('form')
const size = ref<AppOverlaySize>('medium')
const draggable = ref(false)
const fullscreen = ref(false)
const closeOnClickModal = ref(false)
const customDimensions = ref(false)
const widthPercent = ref(70)
const heightPercent = ref(70)
const visible = reactive<Record<Exclude<DialogType, 'confirm'>, boolean>>({
  form: false,
  list: false,
  drawer: false,
})
const formMode = ref<'edit' | 'view'>('edit')
const saving = ref(false)
const lastEvent = ref('等待操作')
const drawerTypes = ref(['markdown', 'json'])
const drawerDateRange = ref<[Date, Date] | null>(null)
const appConfirm = useAppConfirm()

const sampleForm = reactive({
  name: '知识库文档同步',
  owner: '平台研发组',
  description: '每天同步知识库中的已发布文档。',
  enabled: true,
})

const rows = [
  { id: 1, name: '产品使用手册.md', type: 'Markdown', status: '已发布', updatedAt: '2026-07-18 15:32' },
  { id: 2, name: '同步任务.py', type: 'Python', status: '草稿', updatedAt: '2026-07-17 11:08' },
  { id: 3, name: '知识库配置.json', type: 'JSON', status: '已发布', updatedAt: '2026-07-16 09:45' },
]

const currentDefinition = computed(() => dialogTypes.find(item => item.value === activeType.value)!)
const dialogWidth = computed(() => customDimensions.value ? `${widthPercent.value}%` : undefined)
const dialogHeight = computed(() => customDimensions.value ? `${heightPercent.value}%` : undefined)

async function openCurrent() {
  if (activeType.value === 'confirm') {
    const confirmed = await appConfirm('删除后数据无法恢复，是否继续？', {
      title: '删除文档',
      danger: true,
      confirmButtonText: '确认删除',
    })
    lastEvent.value = confirmed ? '已确认删除' : '已取消删除'
    return
  }

  visible[activeType.value] = true
  lastEvent.value = `已打开${currentDefinition.value.label}`
}

async function saveForm() {
  saving.value = true
  await new Promise(resolve => setTimeout(resolve, 500))
  saving.value = false
  visible.form = false
  lastEvent.value = '表单已提交'
  ElMessage.success('保存成功')
}

function confirmDrawer() {
  visible.drawer = false
  lastEvent.value = '抽屉操作已提交'
  ElMessage.success('抽屉配置已保存')
}

function saveDraft() {
  lastEvent.value = '表单草稿已保存'
  ElMessage.success('草稿已保存')
}

function resetDrawerFilters() {
  drawerTypes.value = ['markdown', 'json']
  drawerDateRange.value = null
  lastEvent.value = '抽屉筛选条件已重置'
}
</script>

<template>
  <section class="test-dialog-view">
    <header class="test-dialog-view__header">
      <div>
        <p class="test-dialog-view__eyebrow">COMPONENT PLAYGROUND</p>
        <h1>统一弹窗交互测试</h1>
        <p>验证常见业务弹窗的布局、尺寸、滚动、按钮与关闭行为。</p>
      </div>
      <el-button type="primary" @click="openCurrent">打开当前示例</el-button>
    </header>

    <div class="test-dialog-view__workspace">
      <aside class="test-dialog-view__controls">
        <section class="test-dialog-view__section">
          <h2>业务类型</h2>
          <el-radio-group v-model="activeType" class="test-dialog-view__type-list">
            <el-radio-button v-for="item in dialogTypes" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </section>

        <section class="test-dialog-view__section">
          <h2>公共配置</h2>
          <div class="test-dialog-view__settings">
            <label>
              <span>尺寸预设</span>
              <el-select v-model="size" :disabled="customDimensions || activeType === 'confirm'">
                <el-option label="小（Small）" value="small" />
                <el-option label="中（Medium）" value="medium" />
                <el-option label="大（Large）" value="large" />
                <el-option label="超大（Extra Large）" value="extra-large" />
              </el-select>
            </label>
            <label v-if="activeType !== 'confirm'">
              <span>{{ activeType === 'drawer' ? '自定义宽度' : '自定义宽高' }}</span>
              <el-switch v-model="customDimensions" />
            </label>
            <label v-if="activeType !== 'confirm' && customDimensions">
              <span>宽度（%）</span>
              <span class="test-dialog-view__percent-input">
                <el-input-number
                  v-model="widthPercent"
                  :min="30"
                  :max="96"
                  :step="5"
                  controls-position="right"
                />
                <em>%</em>
              </span>
            </label>
            <label v-if="activeType === 'form'">
              <span>表单模式</span>
              <el-radio-group v-model="formMode" size="small">
                <el-radio-button value="edit">编辑</el-radio-button>
                <el-radio-button value="view">查看</el-radio-button>
              </el-radio-group>
            </label>
            <label v-if="activeType !== 'drawer' && activeType !== 'confirm' && customDimensions">
              <span>高度（%）</span>
              <span class="test-dialog-view__percent-input">
                <el-input-number
                  v-model="heightPercent"
                  :min="30"
                  :max="96"
                  :step="5"
                  controls-position="right"
                />
                <em>%</em>
              </span>
            </label>
            <label v-if="activeType !== 'drawer' && activeType !== 'confirm'">
              <span>允许拖拽</span>
              <el-switch v-model="draggable" />
            </label>
            <label v-if="activeType !== 'drawer' && activeType !== 'confirm'">
              <span>全屏展示</span>
              <el-switch v-model="fullscreen" />
            </label>
            <label v-if="activeType !== 'confirm'">
              <span>点击遮罩关闭</span>
              <el-switch v-model="closeOnClickModal" />
            </label>
          </div>
        </section>

        <section class="test-dialog-view__section test-dialog-view__status">
          <div><span>当前组件</span><strong>{{ currentDefinition.component }}</strong></div>
          <div><span>推荐场景</span><strong>{{ currentDefinition.description }}</strong></div>
          <div><span>最近事件</span><strong>{{ lastEvent }}</strong></div>
        </section>
      </aside>

      <main class="test-dialog-view__preview">
        <div class="test-dialog-view__preview-heading">
          <div>
            <span>{{ currentDefinition.component }}</span>
            <h2>{{ currentDefinition.label }}</h2>
            <p>{{ currentDefinition.description }}</p>
          </div>
          <el-tag effect="plain">Element Plus</el-tag>
        </div>

        <div class="test-dialog-view__catalog">
          <article v-for="item in dialogTypes" :key="item.value" :class="{ 'is-active': item.value === activeType }">
            <div>
              <h3>{{ item.label }}</h3>
              <code>{{ item.component }}</code>
            </div>
            <p>{{ item.description }}</p>
            <el-button text type="primary" @click="activeType = item.value; openCurrent()">测试</el-button>
          </article>
        </div>
      </main>
    </div>

    <AppDialog
      v-model="visible.form"
      :action-mode="formMode === 'edit' ? 'confirm' : 'close'"
      :title="formMode === 'edit' ? '编辑同步任务' : '同步任务详情'"
      :description="formMode === 'edit' ? '修改任务基础信息，提交期间会锁定操作按钮。' : '查看模式只展示数据，底部仅保留关闭按钮。'"
      :size="size"
      :width="dialogWidth"
      :height="dialogHeight"
      :fullscreen="fullscreen"
      :draggable="draggable"
      :close-on-click-modal="closeOnClickModal"
      :confirming="saving"
      @confirm="saveForm"
      @cancel="lastEvent = '已取消表单编辑'"
      @close="lastEvent = '已关闭表单详情'"
    >
      <template v-if="formMode === 'edit'" #footer-extra>
        <el-button @click="saveDraft">保存草稿</el-button>
      </template>
      <el-form v-if="formMode === 'edit'" label-position="top">
        <LayoutFormGrid :columns="2">
          <LayoutFormGridItem>
            <el-form-item label="任务名称"><el-input v-model="sampleForm.name" /></el-form-item>
          </LayoutFormGridItem>
          <LayoutFormGridItem>
            <el-form-item label="负责人"><el-input v-model="sampleForm.owner" /></el-form-item>
          </LayoutFormGridItem>
          <LayoutFormGridItem span="full">
            <el-form-item label="任务说明"><el-input v-model="sampleForm.description" type="textarea" :rows="4" /></el-form-item>
          </LayoutFormGridItem>
          <LayoutFormGridItem>
            <el-form-item label="启用任务"><el-switch v-model="sampleForm.enabled" /></el-form-item>
          </LayoutFormGridItem>
        </LayoutFormGrid>
      </el-form>
      <div v-else class="test-dialog-view__detail-grid">
        <LayoutLabelValue label="任务名称" :value="sampleForm.name" />
        <LayoutLabelValue label="负责人" :value="sampleForm.owner" />
        <LayoutLabelValue label="运行状态" :value="sampleForm.enabled ? '已启用' : '已停用'" />
        <LayoutLabelValue label="任务说明" :value="sampleForm.description" />
      </div>
    </AppDialog>

    <AppDialog
      v-model="visible.list"
      action-mode="close"
      title="选择知识文档"
      description="列表区域超出高度后独立滚动。"
      :size="size"
      :width="dialogWidth"
      :height="dialogHeight"
      :fullscreen="fullscreen"
      :draggable="draggable"
      :close-on-click-modal="closeOnClickModal"
      @close="lastEvent = '已关闭数据列表'"
    >
      <el-table :data="rows" stripe>
        <el-table-column prop="name" label="文件名" min-width="190" />
        <el-table-column prop="type" label="类型" width="110" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
      </el-table>
    </AppDialog>

    <AppDrawer
      v-model="visible.drawer"
      title="文档筛选条件"
      description="适合在不离开主页面的前提下完成辅助配置。"
      :size="size"
      :width="dialogWidth"
      :close-on-click-modal="closeOnClickModal"
      @confirm="confirmDrawer"
      @cancel="lastEvent = '已取消抽屉操作'"
    >
      <template #footer-extra>
        <el-button @click="resetDrawerFilters">重置筛选</el-button>
      </template>
      <el-form label-position="top">
        <el-form-item label="关键词"><el-input placeholder="输入文档名称或内容" /></el-form-item>
        <el-form-item label="文档类型">
          <el-checkbox-group v-model="drawerTypes">
            <el-checkbox value="markdown">Markdown</el-checkbox>
            <el-checkbox value="python">Python</el-checkbox>
            <el-checkbox value="json">JSON</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="更新时间">
          <el-date-picker
            v-model="drawerDateRange"
            type="daterange"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
      </el-form>
    </AppDrawer>
  </section>
</template>

<style scoped>
.test-dialog-view {
  min-height: 100%;
  padding: var(--app-space-6);
  color: var(--app-text);
  background: var(--app-surface-muted);
  container: test-dialog-view / inline-size;
}

.test-dialog-view__header,
.test-dialog-view__preview-heading,
.test-dialog-view__settings label,
.test-dialog-view__catalog article > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
}

.test-dialog-view__header {
  align-items: flex-start;
  margin-bottom: var(--app-space-6);
}

.test-dialog-view__header h1,
.test-dialog-view__section h2,
.test-dialog-view__preview h2,
.test-dialog-view__catalog h3 {
  margin: 0;
  color: var(--app-title);
}

.test-dialog-view__header h1 {
  font-size: var(--app-font-size-title-lg);
}

.test-dialog-view__header p:last-child,
.test-dialog-view__preview-heading p,
.test-dialog-view__catalog p {
  margin: var(--app-space-2) 0 0;
  color: var(--app-text-muted);
  line-height: var(--app-line-height-body);
}

.test-dialog-view__eyebrow {
  margin: 0 0 var(--app-space-2);
  color: var(--app-accent);
  font-size: var(--app-font-size-caption);
  font-weight: 700;
  letter-spacing: 0.08em;
}

.test-dialog-view__workspace {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: var(--app-space-5);
}

.test-dialog-view__controls,
.test-dialog-view__preview {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
}

.test-dialog-view__section {
  padding: var(--app-space-5);
  border-bottom: 1px solid var(--app-border-subtle);
}

.test-dialog-view__section:last-child {
  border-bottom: 0;
}

.test-dialog-view__section h2 {
  margin-bottom: var(--app-space-3);
  font-size: var(--app-font-size-title-sm);
}

.test-dialog-view__type-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--app-space-2);
}

.test-dialog-view__type-list :deep(.el-radio-button__inner) {
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  text-align: left;
  box-shadow: none;
}

.test-dialog-view__settings {
  display: grid;
  gap: var(--app-space-3);
}

.test-dialog-view__settings label > span {
  color: var(--app-text-soft);
}

.test-dialog-view__settings .el-select {
  width: 150px;
}

.test-dialog-view__percent-input {
  display: inline-flex;
  align-items: center;
  gap: var(--app-space-2);
}

.test-dialog-view__percent-input .el-input-number {
  width: 132px;
}

.test-dialog-view__percent-input em {
  color: var(--app-text-muted);
  font-style: normal;
}

.test-dialog-view__status {
  display: grid;
  gap: var(--app-space-3);
}

.test-dialog-view__status div {
  display: grid;
  gap: var(--app-space-1);
}

.test-dialog-view__status span {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.test-dialog-view__status strong {
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-body);
}

.test-dialog-view__preview {
  min-width: 0;
  padding: var(--app-space-6);
}

.test-dialog-view__preview-heading > div > span {
  color: var(--app-accent);
  font-family: ui-monospace, monospace;
  font-size: var(--app-font-size-caption);
}

.test-dialog-view__preview-heading h2 {
  margin-top: var(--app-space-1);
}

.test-dialog-view__catalog {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--app-space-3);
  margin-top: var(--app-space-6);
}

.test-dialog-view__catalog article {
  display: grid;
  gap: var(--app-space-3);
  padding: var(--app-space-4);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-muted);
}

.test-dialog-view__catalog article.is-active {
  border-color: var(--app-accent-border);
  background: var(--app-accent-bg);
}

.test-dialog-view__catalog h3 {
  font-size: var(--app-font-size-subtitle);
}

.test-dialog-view__catalog code {
  color: var(--app-accent);
  font-size: var(--app-font-size-caption);
}

.test-dialog-view__catalog .el-button {
  justify-self: start;
  padding: 0;
}

.test-dialog-view__detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--app-space-4);
}

@container test-dialog-view (max-width: 820px) {
  .test-dialog-view__workspace,
  .test-dialog-view__catalog {
    grid-template-columns: 1fr;
  }
}
</style>
