<script setup lang="ts">
const props = defineProps<{
  label?: string
  hint?: string
  error?: string
  required?: boolean
  block?: boolean
  minWidth?: string
  maxWidth?: string
  labelPosition?: 'left' | 'inner'
  innerLabelWidth?: string
}>()
</script>

<template>
  <div
    class="app-field-shell"
    :class="{ 'app-field-shell--block': block, 'app-field-shell--error': error }"
    :style="{
      '--app-field-min-width': props.minWidth || 'var(--app-field-width-min)',
      '--app-field-max-width': props.maxWidth || 'var(--app-field-width-max)',
      '--app-field-inner-label-width':
        props.innerLabelWidth ||
        `${Math.max(String(props.label || '').length * 14 + 16, 44)}px`,
    }"
  >
    <div
      v-if="(label || $slots.label) && labelPosition !== 'inner'"
      class="app-field-shell__header"
    >
      <slot name="label">
        <span class="app-field-shell__label">
          <span v-if="required" class="app-field-shell__required">*</span>
          {{ label }}
        </span>
      </slot>
      <slot name="extra" />
    </div>

    <div
      class="app-field-shell__control"
      :class="{ 'app-field-shell__control--inner': labelPosition === 'inner' && (label || $slots.label) }"
    >
      <div
      v-if="labelPosition === 'inner' && (label || $slots.label)"
        class="app-field-shell__inner-label"
      >
        <slot name="label">
          <span class="app-field-shell__label">
            <span v-if="required" class="app-field-shell__required">*</span>
            {{ label }}
          </span>
        </slot>
      </div>
      <slot />
    </div>

    <div v-if="error || hint || $slots.hint" class="app-field-shell__footer">
      <span v-if="error" class="app-field-shell__error">{{ error }}</span>
      <slot v-else name="hint">
        <span v-if="hint" class="app-field-shell__hint">{{ hint }}</span>
      </slot>
    </div>
  </div>
</template>

<style scoped>
.app-field-shell {
  display: inline-flex;
  flex-direction: column;
  gap: var(--app-space-2);
  width: 100%;
  min-width: min(var(--app-field-min-width), 100%);
  max-width: var(--app-field-max-width);
}

.app-field-shell--block {
  display: flex;
  width: 100%;
  max-width: none;
}

.app-field-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-3);
  min-width: 0;
}

.app-field-shell__label {
  font-size: var(--app-font-size-body);
  font-weight: 600;
  color: var(--app-text);
}

.app-field-shell__required {
  margin-right: 4px;
  color: var(--app-danger);
}

.app-field-shell__control {
  min-width: 0;
}

.app-field-shell__control--inner {
  position: relative;
}

.app-field-shell__inner-label {
  position: absolute;
  top: 0;
  left: 12px;
  z-index: 2;
  padding: 0 var(--app-space-tight);
  transform: translateY(-50%);
  white-space: nowrap;
  pointer-events: none;
  background: var(--app-surface-solid);
  line-height: 1;
  border-radius: var(--app-radius-round);
}

.app-field-shell__control--inner :deep(.el-input__wrapper),
.app-field-shell__control--inner :deep(.el-select__wrapper),
.app-field-shell__control--inner :deep(.el-textarea__inner) {
  padding-left: 12px;
}

.app-field-shell__control--inner :deep(.el-range-editor.el-input__wrapper) {
  padding-left: 12px;
}

.app-field-shell__control--inner :deep(.el-select__placeholder),
.app-field-shell__control--inner :deep(.el-input__inner::placeholder) {
  color: var(--app-placeholder);
}

.app-field-shell__control :deep(.el-input),
.app-field-shell__control :deep(.el-select),
.app-field-shell__control :deep(.el-date-editor),
.app-field-shell__control :deep(.el-tree-select) {
  width: 100%;
}

.app-field-shell__footer {
  font-size: var(--app-font-size-caption);
  line-height: 1.5;
}

.app-field-shell__hint {
  color: var(--app-text-muted);
}

.app-field-shell__error {
  color: var(--app-danger);
}
</style>
