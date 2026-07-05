<script setup lang="ts">
defineProps<{
  description?: string
}>()
</script>

<template>
  <div class="app-empty">
    <p>{{ description ?? 'No data available.' }}</p>
  </div>
</template>

<style scoped>
.app-empty {
  padding: 32px 20px;
  text-align: center;
  color: #64748b;
}
</style>
