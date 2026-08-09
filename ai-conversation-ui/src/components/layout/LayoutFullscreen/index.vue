<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue?: boolean
  }>(),
  {
    modelValue: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

function handleKeydown(event: KeyboardEvent) {
  if (props.modelValue && event.key === 'Escape') {
    event.preventDefault()
    emit('update:modelValue', false)
  }
}
</script>

<template>
  <Teleport to="body" :disabled="!props.modelValue">
    <div
      class="layout-fullscreen"
      :class="{ 'layout-fullscreen--active': props.modelValue }"
      @keydown="handleKeydown"
    >
      <slot />
    </div>
  </Teleport>
</template>

<style scoped>
.layout-fullscreen {
  width: 100%;
  min-width: 0;
  min-height: 0;
}

.layout-fullscreen--active {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--app-body-bg);
}
</style>
