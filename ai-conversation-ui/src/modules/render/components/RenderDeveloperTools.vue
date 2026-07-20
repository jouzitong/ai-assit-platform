<script setup lang="ts">
import { EditPen, Operation } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { AppCodeEditor, AppDialog, AppJsonTree } from '../../../components'

const props = defineProps<{
  code: string
  metadataVisible: boolean
  scopeVisible: boolean
  metadataDraft: string
  metadataSaving?: boolean
  scope: unknown
  showTriggers?: boolean
}>()

const emit = defineEmits<{
  'update:metadataVisible': [value: boolean]
  'update:scopeVisible': [value: boolean]
  'update:metadataDraft': [value: string]
  'open-metadata': []
  save: [content: Record<string, unknown>]
}>()

function formatMetadata() {
  try {
    const content = parseMetadata()
    emit('update:metadataDraft', JSON.stringify(content, null, 2))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Render JSON 格式不合法')
  }
}

function saveMetadata() {
  try {
    emit('save', parseMetadata())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Render JSON 格式不合法')
  }
}

function parseMetadata() {
  const value = JSON.parse(props.metadataDraft)
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Render JSON 根节点必须是对象')
  }
  return value as Record<string, unknown>
}
</script>

<template>
  <aside v-if="showTriggers" class="render-developer-tools" aria-label="Render Runtime 开发者工具">
    <el-tag type="warning" effect="dark">开发模式</el-tag>
    <el-button-group>
      <el-button :icon="EditPen" @click="emit('open-metadata')">元数据配置</el-button>
      <el-button
        :icon="Operation"
        title="SCOPE 上下文"
        aria-label="SCOPE 上下文"
        @click="emit('update:scopeVisible', true)"
      />
    </el-button-group>
  </aside>

  <AppDialog
    :model-value="metadataVisible"
    title="Render JSON 元数据"
    :description="`编辑并保存 ${code} 的原始 Render Meta 内容。`"
    size="extra-large"
    action-mode="confirm"
    confirm-text="保存并重新渲染"
    :confirming="metadataSaving"
    :show-close="!metadataSaving"
    :close-on-press-escape="!metadataSaving"
    @update:model-value="emit('update:metadataVisible', $event)"
    @confirm="saveMetadata"
  >
    <div class="render-developer-tools__metadata-toolbar">
      <el-button @click="formatMetadata">格式化 JSON</el-button>
    </div>
    <AppCodeEditor
      :model-value="metadataDraft"
      format="json"
      height="var(--app-layout-dialog-body-max-height)"
      min-height="var(--app-layout-dialog-body-max-height)"
      toolbar-label="Render JSON"
      :show-format-switcher="false"
      @update:model-value="emit('update:metadataDraft', $event)"
    />
  </AppDialog>

  <teleport to="body">
    <div
      v-if="scopeVisible"
      class="render-developer-tools__context-layer"
      @click.self="emit('update:scopeVisible', false)"
    >
      <aside
        class="render-developer-tools__context-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="render-runtime-context-title"
      >
        <header class="render-developer-tools__context-header">
          <h2 id="render-runtime-context-title">Runtime Context</h2>
          <el-button text @click="emit('update:scopeVisible', false)">关闭</el-button>
        </header>
        <div class="render-developer-tools__context-body">
          <div class="render-developer-tools__context-meta">
            <span class="render-developer-tools__context-meta-label">Render Code</span>
            <span class="render-developer-tools__context-meta-value">{{ code }}</span>
          </div>
          <AppJsonTree :value="scope" label="SCOPE" />
        </div>
      </aside>
    </div>
  </teleport>
</template>

<style scoped>
.render-developer-tools {
  position: fixed;
  right: var(--app-space-5);
  bottom: var(--app-space-5);
  z-index: 120;
  display: flex;
  align-items: center;
  gap: var(--app-space-2);
  padding: var(--app-space-2);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-raised);
  box-shadow: var(--app-shadow-sm);
}

.render-developer-tools__metadata-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--app-space-3);
}

.render-developer-tools__context-layer {
  position: fixed;
  inset: 0;
  z-index: 2100;
  pointer-events: auto;
}

.render-developer-tools__context-panel {
  position: fixed;
  top: calc(var(--app-control-height-lg) + var(--app-space-6));
  right: var(--app-space-5);
  width: var(--app-drawer-width-sm);
  max-width: calc(100% - var(--app-space-5) - var(--app-space-5));
  max-height: calc(100% - var(--app-control-height-lg) - var(--app-space-8));
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-solid);
  box-shadow: var(--app-dialog-shadow);
}

.render-developer-tools__context-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-3);
  padding: var(--app-space-3) var(--app-space-4);
  border-bottom: 1px solid var(--app-border-subtle);
}

.render-developer-tools__context-header h2 {
  margin: 0;
  font-size: var(--app-font-size-subtitle);
  color: var(--app-title);
}

.render-developer-tools__context-body {
  min-height: 0;
  overflow: auto;
  padding: var(--app-space-3) var(--app-space-4) var(--app-space-4);
}

.render-developer-tools__context-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-3);
  margin-bottom: var(--app-space-3);
  padding: var(--app-space-2) var(--app-space-3);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-muted);
}

.render-developer-tools__context-meta-label {
  flex: 0 0 auto;
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  color: var(--app-text-muted);
}

.render-developer-tools__context-meta-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--app-font-family);
  font-size: var(--app-font-size-caption);
  color: var(--app-text);
}

@media (max-width: 560px) {
  .render-developer-tools__context-panel {
    top: var(--app-space-4);
    right: var(--app-space-3);
    width: calc(100% - var(--app-space-6));
    max-width: none;
    max-height: calc(100% - var(--app-space-8));
  }
}
</style>
