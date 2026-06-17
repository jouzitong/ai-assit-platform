<script setup>
defineProps({
  title: {
    type: String,
    default: '任务中心'
  },
  meta: {
    type: String,
    default: ''
  },
  showTitle: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <div class="header">
    <div v-if="showTitle" class="header-title">
      <div class="title">
        {{ title }}
      </div>
      <div v-if="meta" class="meta">
        {{ meta }}
      </div>
    </div>
    <div class="header-left">
      <slot name="left" />
    </div>
    <div class="header-right">
      <slot name="right" />
    </div>
  </div>
</template>

<style scoped>
.header {
  height: 64px;
  border-bottom: 1px solid var(--stroke);
  padding: 10px 20px;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-left {
  display: flex;
  flex: 1;
  padding-left: 5px;
  min-width: 0;
}

.header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
</style>
