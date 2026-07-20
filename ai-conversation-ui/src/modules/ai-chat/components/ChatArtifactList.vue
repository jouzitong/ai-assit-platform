<script setup lang="ts">
import type { ChatArtifact } from '../types'
import { isRenderJsonArtifact } from '../utils/renderArtifact'

defineProps<{
  artifacts: ChatArtifact[]
}>()

const emit = defineEmits<{
  'open-render-artifact': [artifact: ChatArtifact]
}>()

function displayContent(content: unknown) {
  if (typeof content === 'string') return content
  if (content === undefined || content === null) return ''
  try {
    return JSON.stringify(content, null, 2)
  } catch {
    return String(content)
  }
}
</script>

<template>
  <div class="chat-artifact-list" aria-label="Agent 产物">
    <article
      v-for="(artifact, index) in artifacts"
      :key="artifact.artifactCode || artifact.codeRef || index"
      class="chat-artifact-list__item"
    >
      <header>
        <div>
          <span>{{ artifact.artifactType || 'ARTIFACT' }}</span>
          <strong>{{ artifact.title || artifact.artifactCode || artifact.codeRef || 'Agent 产物' }}</strong>
        </div>
        <small>{{ artifact.status || artifact.stage || '' }}</small>
      </header>
      <button
        v-if="isRenderJsonArtifact(artifact)"
        class="chat-artifact-list__open"
        type="button"
        @click="emit('open-render-artifact', artifact)"
      >
        <span>打开生成页面</span>
        <small>支持等比例缩放与全屏查看</small>
      </button>
      <pre v-else-if="displayContent(artifact.content)">{{ displayContent(artifact.content) }}</pre>
      <div v-else-if="artifact.codeRef || artifact.artifactCode" class="chat-artifact-list__reference">
        {{ artifact.codeRef || artifact.artifactCode }}
      </div>
    </article>
  </div>
</template>

<style scoped>
.chat-artifact-list {
  display: grid;
  gap: 0.5rem;
  margin-top: 0.625rem;
}

.chat-artifact-list__item {
  padding: 0.75rem;
  border: 0.0625rem solid var(--chat-panel-border);
  border-radius: 0.75rem;
  background: var(--chat-soft-bg);
}

.chat-artifact-list__item header,
.chat-artifact-list__item header > div {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.chat-artifact-list__item header {
  justify-content: space-between;
}

.chat-artifact-list__item header span,
.chat-artifact-list__item header small,
.chat-artifact-list__reference {
  color: var(--chat-text-muted);
  font-size: 0.6875rem;
}

.chat-artifact-list__item header strong {
  color: var(--chat-text-primary);
  font-size: 0.8125rem;
}

.chat-artifact-list__item pre {
  max-height: 17.5rem;
  margin: 0.625rem 0 0;
  padding: 0.625rem;
  overflow: auto;
  border-radius: 0.5rem;
  background: var(--chat-bubble-bg);
  color: var(--chat-text-primary);
  font: 0.75rem/1.55 ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.chat-artifact-list__reference { margin-top: 0.5rem; }

.chat-artifact-list__open {
  display: grid;
  width: 100%;
  gap: 0.25rem;
  margin-top: 0.625rem;
  padding: 0.75rem;
  border: 0.0625rem solid var(--el-color-primary-light-5);
  border-radius: 0.625rem;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  text-align: left;
  cursor: pointer;
}

.chat-artifact-list__open span {
  font-size: 0.8125rem;
  font-weight: 700;
}

.chat-artifact-list__open small {
  color: var(--chat-text-muted);
  font-size: 0.6875rem;
}

.chat-artifact-list__open:hover {
  border-color: var(--el-color-primary);
}

.chat-artifact-list__open:focus-visible {
  outline: 0.125rem solid var(--el-color-primary);
  outline-offset: 0.125rem;
}
</style>
