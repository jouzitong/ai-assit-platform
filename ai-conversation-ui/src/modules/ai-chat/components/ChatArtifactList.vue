<script setup lang="ts">
import type { ChatArtifact } from '../types'

defineProps<{
  artifacts: ChatArtifact[]
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
      <pre v-if="displayContent(artifact.content)">{{ displayContent(artifact.content) }}</pre>
      <div v-else-if="artifact.codeRef || artifact.artifactCode" class="chat-artifact-list__reference">
        {{ artifact.codeRef || artifact.artifactCode }}
      </div>
    </article>
  </div>
</template>

<style scoped>
.chat-artifact-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.chat-artifact-list__item {
  padding: 12px;
  border: 1px solid var(--chat-panel-border);
  border-radius: 12px;
  background: var(--chat-soft-bg);
}

.chat-artifact-list__item header,
.chat-artifact-list__item header > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-artifact-list__item header {
  justify-content: space-between;
}

.chat-artifact-list__item header span,
.chat-artifact-list__item header small,
.chat-artifact-list__reference {
  color: var(--chat-text-muted);
  font-size: 11px;
}

.chat-artifact-list__item header strong {
  color: var(--chat-text-primary);
  font-size: 13px;
}

.chat-artifact-list__item pre {
  max-height: 280px;
  margin: 10px 0 0;
  padding: 10px;
  overflow: auto;
  border-radius: 8px;
  background: var(--chat-bubble-bg);
  color: var(--chat-text-primary);
  font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.chat-artifact-list__reference { margin-top: 8px; }
</style>
