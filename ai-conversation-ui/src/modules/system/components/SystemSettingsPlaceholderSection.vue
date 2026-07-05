<script setup lang="ts">
defineProps<{
  title: string
  description: string
  bullets: string[]
}>()
</script>

<template>
  <section class="system-settings-panel">
    <div class="system-settings-panel__card">
      <h3>当前模块</h3>
      <ul>
        <li v-for="item in bullets" :key="item">{{ item }}</li>
      </ul>
    </div>

    <div class="system-settings-panel__card">
      <h3>实施说明</h3>
      <p>{{ description }}</p>
    </div>
  </section>
</template>

<style scoped>
.system-settings-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.system-settings-panel__card {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.system-settings-panel__card h3 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 16px;
}

.system-settings-panel__card p,
.system-settings-panel__card li {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.55;
}

.system-settings-panel__card ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
}

@media (max-width: 960px) {
  .system-settings-panel {
    grid-template-columns: 1fr;
  }
}
</style>
