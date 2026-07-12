<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="layout-page-header">
    <div class="layout-page-header__main">
      <slot name="leading" />
      <div>
        <h2 class="layout-page-header__title">{{ title }}</h2>
        <p v-if="description" class="layout-page-header__description">{{ description }}</p>
      </div>
    </div>

    <div v-if="$slots.actions" class="layout-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.layout-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
  width: 100%;
  min-width: 0;
  min-height: var(--app-layout-header-height);
  container-type: inline-size;
}

.layout-page-header__main,
.layout-page-header__actions {
  min-width: 0;
}

.layout-page-header__main {
  display: flex;
  align-items: flex-start;
  gap: var(--app-space-3);
}

.layout-page-header__title {
  margin: 0;
  color: var(--app-title);
  font-size: var(--app-font-size-title-sm);
  line-height: 1.4;
}

.layout-page-header__description {
  margin: var(--app-space-1) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  line-height: 1.5;
}

.layout-page-header__actions {
  flex: 0 1 auto;
}

@container (max-width: 720px) {
  .layout-page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .layout-page-header__actions {
    width: 100%;
  }
}
</style>
