<script setup lang="ts">
import GeneratedArtifactWorkspace from './GeneratedArtifactWorkspace.vue'
import type { ChatArtifact } from '../types'
import { isRenderJsonArtifact } from '../utils/renderArtifact'

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
    <template
      v-for="(artifact, index) in artifacts"
      :key="artifact.artifactCode || artifact.codeRef || index"
    >
      <GeneratedArtifactWorkspace
        v-if="isRenderJsonArtifact(artifact)"
        :artifact="artifact"
      />
      <article v-else class="chat-artifact-list__item">
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
    </template>
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
</style>
