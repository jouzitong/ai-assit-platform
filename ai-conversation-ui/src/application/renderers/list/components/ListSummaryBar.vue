<script setup lang="ts">
import type { RendererSummaryCard } from '../types'

defineProps<{
  cards: RendererSummaryCard[]
}>()
</script>

<template>
  <div v-if="cards.length" class="list-summary-bar">
    <article
      v-for="card in cards"
      :key="card.key"
      class="list-summary-bar__card"
      :style="{ '--summary-accent': card.accent || 'var(--app-accent)' }"
    >
      <span class="list-summary-bar__label">{{ card.label }}</span>
      <strong class="list-summary-bar__value">{{ card.value }}</strong>
      <span v-if="card.hint" class="list-summary-bar__hint">{{ card.hint }}</span>
    </article>
  </div>
</template>

<style scoped>
.list-summary-bar {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--app-space-comfortable);
}

.list-summary-bar__card {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-2);
  min-height: 110px;
  padding: var(--app-space-roomy) var(--app-space-5);
  border: 1px solid var(--workbench-summary-border);
  border-radius: var(--app-radius-2xl);
  background: var(--workbench-summary-bg);
  box-shadow: var(--app-inset-highlight);
}

.list-summary-bar__label {
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  color: var(--workbench-text-soft);
}

.list-summary-bar__value {
  font-size: var(--app-font-size-display);
  line-height: 1;
  color: var(--summary-accent);
}

.list-summary-bar__hint {
  font-size: var(--app-font-size-caption);
  color: var(--workbench-text-muted);
}
</style>
