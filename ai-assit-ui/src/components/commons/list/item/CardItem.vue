<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  meta: {
    type: Array,
    default: () => []
  },
  actions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['action'])
</script>

<template>
  <article class="card-shell">
    <div class="card-head">
      <div>
        <h4>{{ title }}</h4>
        <p v-if="description">
          {{ description }}
        </p>
      </div>
      <div v-if="actions.length" class="card-actions">
        <button v-for="action in actions" :key="action.key" type="button" @click="emit('action', action)">
          {{ action.label }}
        </button>
      </div>
    </div>
    <div class="card-meta">
      <div v-for="item in meta" :key="item.key" class="meta-item">
        <span>{{ item.key }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>
    <slot />
  </article>
</template>

<style scoped>
.card-shell {
  display: grid;
  gap: 14px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--stroke);
  background: var(--surface-bg-1);
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.card-head h4,
.card-head p {
  margin: 0;
}

.card-head p,
.meta-item span {
  color: var(--text-dim);
}

.card-actions {
  display: inline-flex;
  gap: 8px;
}

.card-actions button {
  height: 30px;
  border-radius: 10px;
  border: 1px solid var(--stroke);
  background: var(--control-bg);
  color: var(--text);
}

.card-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
}

.meta-item {
  display: grid;
  gap: 4px;
}
</style>
