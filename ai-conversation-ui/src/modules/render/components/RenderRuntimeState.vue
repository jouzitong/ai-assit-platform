<script setup lang="ts">
defineProps<{
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <section v-if="loading" class="render-runtime-state" aria-live="polite" aria-busy="true">
    <el-skeleton :rows="8" animated />
  </section>
  <section v-else-if="error" class="render-runtime-state" role="alert">
    <el-result icon="error" title="页面加载失败" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="emit('retry')">重新加载</el-button>
      </template>
    </el-result>
  </section>
</template>

<style scoped>
.render-runtime-state {
  display: grid;
  width: 100%;
  min-height: min(60dvh, 560px);
  padding: var(--app-space-6);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-raised);
  place-items: center;
}

.render-runtime-state :deep(.el-skeleton) {
  width: min(100%, 960px);
}
</style>
