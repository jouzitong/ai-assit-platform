<script setup>
import { computed } from 'vue'

const props = defineProps({
  totalItems: {
    type: Number,
    default: 0
  },
  page: {
    type: Number,
    default: 1
  },
  pageSize: {
    type: Number,
    default: 10
  },
  showPageSize: {
    type: Boolean,
    default: false
  },
  pageSizeOptions: {
    type: Array,
    default: () => [10, 20, 50, 100]
  }
})

const emit = defineEmits(['change', 'update:page', 'update:pageSize'])

const totalPages = computed(() => Math.max(1, Math.ceil((props.totalItems || 0) / (props.pageSize || 1))))
const canPrev = computed(() => props.page > 1)
const canNext = computed(() => props.page < totalPages.value)

function emitChange(page, pageSize = props.pageSize) {
  emit('update:page', page)
  emit('update:pageSize', pageSize)
  emit('change', { page, pageSize })
}

function goPrev() {
  if (!canPrev.value) return
  emitChange(props.page - 1)
}

function goNext() {
  if (!canNext.value) return
  emitChange(props.page + 1)
}

function onPageSizeChange(event) {
  const pageSize = Number(event.target.value) || props.pageSize
  emitChange(1, pageSize)
}
</script>

<template>
  <div class="pagination">
    <span class="pagination__meta">第 {{ page }} / {{ totalPages }} 页</span>
    <select v-if="showPageSize" class="pagination__select" :value="pageSize" @change="onPageSizeChange">
      <option v-for="size in pageSizeOptions" :key="size" :value="size">
        {{ size }} / 页
      </option>
    </select>
    <button type="button" :disabled="!canPrev" @click="goPrev">
      上一页
    </button>
    <button type="button" :disabled="!canNext" @click="goNext">
      下一页
    </button>
  </div>
</template>

<style scoped>
.pagination {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.pagination__meta {
  color: var(--text-dim);
  font-size: 12px;
}

.pagination__select,
button {
  height: 32px;
  border-radius: 10px;
  border: 1px solid var(--stroke);
  background: var(--control-bg);
  color: var(--text);
  font: inherit;
}

.pagination__select {
  padding: 0 10px;
}

button {
  padding: 0 12px;
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
