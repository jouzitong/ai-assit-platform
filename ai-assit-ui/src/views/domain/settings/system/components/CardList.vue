<script setup>
defineProps({
  eyebrow: {
    type: String,
    default: ''
  },
  title: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  items: {
    type: Array,
    default: () => []
  },
  emptyText: {
    type: String,
    default: '当前没有可展示内容。'
  }
})
</script>

<template>
  <section class="card-list-panel">
    <header class="card-list-head">
      <div class="card-list-copy">
        <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description" class="card-list-desc">{{ description }}</p>
      </div>
      <slot name="head-action" />
    </header>

    <div v-if="items.length" class="card-list-body">
      <slot name="list" :items="items" />
    </div>

    <div v-else class="card-list-empty">
      {{ emptyText }}
    </div>
  </section>
</template>

<style scoped>
.card-list-panel {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 18px;
  background: #fff;
  padding: 14px;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.card-list-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
}

.card-list-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.card-list-copy h2,
.card-list-copy p {
  margin: 0;
}

.card-list-copy h2 {
  color: #0f172a;
  font-size: 18px;
  line-height: 1.2;
}

.card-list-desc {
  color: #475569;
  font-size: 12px;
  line-height: 1.45;
}

.card-list-body {
  min-height: 0;
  display: grid;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.card-list-empty {
  min-height: 180px;
  border: 1px dashed rgba(191, 219, 254, 0.95);
  border-radius: 14px;
  padding: 16px 14px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  background: rgba(248, 250, 252, 0.88);
  display: grid;
  place-items: center;
  text-align: center;
}

@media (max-width: 1100px) {
  .card-list-head {
    flex-direction: column;
  }
}
</style>
