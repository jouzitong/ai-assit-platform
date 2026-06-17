<script setup>
const props = defineProps({
  text: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'primary'
  },
  loading: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click'])

function handleClick(event) {
  if (props.disabled || props.loading) {
    return
  }
  emit('click', event)
}
</script>

<template>
  <button
    class="app-button"
    :class="`type-${type}`"
    :disabled="disabled || loading"
    type="button"
    @click="handleClick"
  >
    <span v-if="loading" class="loading-dot" />
    <span>{{ text }}</span>
  </button>
</template>

<style scoped>
.app-button {
  height: 32px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  font: inherit;
  white-space: nowrap;
  transition: transform 0.2s ease, background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.app-button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.app-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.type-primary {
  background: var(--app-accent);
  color: #fff;
}

.type-ghost {
  background: var(--control-bg);
  border-color: var(--stroke);
  color: var(--text);
}

.type-danger {
  background: rgba(220, 38, 38, 0.12);
  border-color: rgba(248, 113, 113, 0.35);
  color: var(--danger);
}

.type-link {
  background: transparent;
  color: var(--accent);
  padding-inline: 4px;
}

.loading-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  animation: rotate 0.8s linear infinite;
}

@keyframes rotate {
  to {
    transform: rotate(360deg);
  }
}
</style>
