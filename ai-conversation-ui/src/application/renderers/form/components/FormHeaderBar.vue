<script setup lang="ts">
import { resolveRendererActionIcon } from '../../action'
import type { FormRendererAction } from '../types'

defineProps<{
  title: string
  description?: string
  actions?: FormRendererAction[]
  actionsAlign?: 'left' | 'center' | 'right'
  submitting?: boolean
}>()

const emit = defineEmits<{
  action: [action: FormRendererAction]
}>()

const mutationActionKeys = new Set(['SUBMIT', 'SAVE', 'CREATE', 'UPDATE'])

function isMutationAction(action: FormRendererAction) {
  return mutationActionKeys.has(action.action)
}

</script>

<template>
  <div class="form-header-bar">
    <div class="form-header-bar__main">
      <div>
        <h2 class="form-header-bar__title">{{ title }}</h2>
        <p v-if="description" class="form-header-bar__description">{{ description }}</p>
      </div>

      <div
        v-if="actions?.length"
        class="form-header-bar__actions"
        :class="`form-header-bar__actions--${actionsAlign || 'right'}`"
      >
        <el-button
          v-for="action in actions"
          :key="action.key"
          :type="action.options?.type"
          :icon="resolveRendererActionIcon(action)"
          :style="action.options?.style"
          :class="action.options?.class"
          :loading="submitting && isMutationAction(action)"
          :disabled="submitting"
          @click="emit('action', action)"
        >
          {{ action.name }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-header-bar {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-3);
}

.form-header-bar__main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-4);
}

.form-header-bar__title {
  margin: 0;
  font-size: var(--app-font-size-title-lg);
  font-weight: 700;
  color: var(--app-title);
}

.form-header-bar__description {
  margin: 8px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-body);
  line-height: 1.55;
}

.form-header-bar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-3);
}

.form-header-bar__actions--left {
  justify-content: flex-start;
}

.form-header-bar__actions--center {
  justify-content: center;
}

.form-header-bar__actions--right {
  justify-content: flex-end;
}

@container application-form-layout (max-width: 768px) {
  .form-header-bar__main {
    flex-direction: column;
  }
}
</style>
