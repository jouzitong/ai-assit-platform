<script setup lang="ts">
import { computed } from 'vue'
import FormFieldRenderer from './FormFieldRenderer.vue'
import type { FormRendererField, NormalizedFormRendererGroup } from '../types'

const props = defineProps<{
  group: NormalizedFormRendererGroup
  fields: Record<string, FormRendererField>
  modelValue: Record<string, unknown>
  errors?: Record<string, string>
  schemaComponent?: string
  readonly?: boolean
}>()

const emit = defineEmits<{
  change: [payload: { key: string; value: unknown }]
}>()

const resolvedFields = computed(() =>
  props.group.fields
    .map((fieldKey) => props.fields[fieldKey])
    .filter((field): field is FormRendererField => Boolean(field)),
)

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${Math.max(props.group.columns || 1, 1)}, minmax(0, 1fr))`,
}))
</script>

<template>
  <section class="form-group-panel">
    <header class="form-group-panel__header">
      <div>
        <h3 class="form-group-panel__title">{{ group.title }}</h3>
        <p v-if="group.description" class="form-group-panel__description">{{ group.description }}</p>
      </div>
    </header>

    <div class="form-group-panel__grid" :style="gridStyle">
      <FormFieldRenderer
        v-for="field in resolvedFields"
        :key="field.key"
        :field="field"
        :model-value="modelValue"
        :error="errors?.[field.key]"
        :schema-component="schemaComponent"
        :readonly="readonly"
        @change="emit('change', $event)"
      />
    </div>
  </section>
</template>

<style scoped>
.form-group-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
}

.form-group-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-4);
  padding: var(--app-space-4) var(--app-space-5);
  border-bottom: 1px solid var(--app-border-subtle);
  background: var(--app-surface-solid);
}

.form-group-panel__title {
  margin: 0;
  color: var(--app-title);
  font-size: var(--app-font-size-title-md);
  font-weight: 700;
}

.form-group-panel__description {
  margin: var(--app-space-1) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  line-height: 1.5;
}

.form-group-panel__grid {
  display: grid;
  column-gap: var(--app-space-6);
  row-gap: var(--app-space-5);
  padding: var(--app-space-5);
  background: var(--app-surface);
}

@container application-form-layout (max-width: 680px) {
  .form-group-panel__grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
