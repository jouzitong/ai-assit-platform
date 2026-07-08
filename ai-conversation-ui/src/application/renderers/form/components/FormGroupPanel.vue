<script setup lang="ts">
import { computed } from 'vue'
import FormFieldRenderer from './FormFieldRenderer.vue'
import type { FormRendererField, NormalizedFormRendererGroup } from '../types'

const props = defineProps<{
  group: NormalizedFormRendererGroup
  fields: Record<string, FormRendererField>
  modelValue: Record<string, unknown>
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
  gap: 16px;
  padding: 20px 22px;
  border: 1px solid var(--app-border-subtle);
  border-radius: 24px;
  background: var(--app-surface-gradient);
  box-shadow: var(--app-shadow-md);
}

.form-group-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.form-group-panel__title {
  margin: 0;
  color: var(--app-title);
  font-size: 18px;
  font-weight: 700;
}

.form-group-panel__description {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.form-group-panel__grid {
  display: grid;
  gap: 16px;
}

@media (max-width: 960px) {
  .form-group-panel__grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
