<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['action'])

const normalizedItems = computed(() => props.items.map((item) => {
  const key = item?.key || ''
  const value = item?.value || ''
  return {
    ...item,
    text: value ? `${key}：${value}` : key
  }
}))
</script>

<template>
  <div class="filter-summary">
    <span
      v-for="(item, index) in normalizedItems"
      :key="index"
      class="chip"
      :class="{ ghost: item.ghost }"
      @click="item.action && emit('action', item.action)"
    >
      {{ item.text }}
    </span>
  </div>
</template>

<style scoped>
.filter-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 2px 25px 0;
}

.chip {
  padding: 4px 10px;
  border: 1px solid var(--stroke);
  border-radius: 999px;
  font-size: 12px;
  background: var(--control-bg);
  color: var(--text);
  cursor: pointer;
}

.chip.ghost {
  color: var(--text-dim);
  background: transparent;
}
</style>
