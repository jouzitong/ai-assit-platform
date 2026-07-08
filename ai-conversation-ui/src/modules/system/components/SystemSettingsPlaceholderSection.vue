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
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface);
  box-shadow: var(--system-shadow);
}

.system-settings-panel__card h3 {
  margin: 0 0 8px;
  color: var(--system-title);
  font-size: 16px;
}

.system-settings-panel__card p,
.system-settings-panel__card li {
  margin: 0;
  color: var(--system-text-muted);
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
