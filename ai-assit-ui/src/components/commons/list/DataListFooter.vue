<script setup>
import Pagination from '../../comm/Pagination.vue'

defineProps({
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
  }
})

const emit = defineEmits(['change', 'update:page', 'update:pageSize'])
</script>

<template>
  <div class="footer data-list-footer">
    <span class="data-list-footer__total">共 {{ totalItems }} 条</span>
    <Pagination
      :total-items="totalItems"
      :page="page"
      :page-size="pageSize"
      :show-page-size="showPageSize"
      @change="emit('change', $event)"
      @update:page="emit('update:page', $event)"
      @update:pageSize="emit('update:pageSize', $event)"
    />
  </div>
</template>

<style scoped>
.footer {
  height: 50px;
  padding: 10px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--text-dim);
}

.data-list-footer {
  gap: 12px;
  flex-wrap: wrap;
}

.data-list-footer__total {
  color: var(--text-dim);
  white-space: nowrap;
}
</style>
