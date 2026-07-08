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
  gap: 14px;
}

.list-summary-bar__card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 110px;
  padding: 18px 20px;
  border: 1px solid var(--workbench-summary-border);
  border-radius: 22px;
  background: var(--workbench-summary-bg);
  box-shadow: var(--app-inset-highlight);
}

.list-summary-bar__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--workbench-text-soft);
}

.list-summary-bar__value {
  font-size: 32px;
  line-height: 1;
  color: var(--summary-accent);
}

.list-summary-bar__hint {
  font-size: 12px;
  color: var(--workbench-text-muted);
}
</style>
