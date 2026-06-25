<script setup>
defineProps({
  runtime: {
    type: Object,
    required: true
  },
  rendererKey: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <section class="placeholder-panel">
    <p class="eyebrow">Renderer</p>
    <h2>{{ runtime.title }}</h2>
    <p>{{ runtime.description || '当前 schema 已进入 PageRuntime，但对应渲染器还未实现。' }}</p>
    <div class="tag-row">
      <span class="tag">layout: {{ rendererKey }}</span>
      <span class="tag">viewId: {{ runtime.schema.viewId }}</span>
    </div>
  </section>
</template>

<style scoped>
.placeholder-panel {
  display: grid;
  gap: 10px;
  padding: 24px;
  border-radius: 20px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.92));
}

.placeholder-panel h2,
.placeholder-panel p {
  margin: 0;
}

.tag-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.9);
  color: #334155;
  font-size: 12px;
}
</style>
