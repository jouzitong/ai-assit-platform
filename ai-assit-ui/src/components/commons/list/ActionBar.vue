<script setup>
import { computed, ref } from 'vue'
import AppButton from '../app/AppButton.vue'

const props = defineProps({
  actions: {
    type: Array,
    default: () => []
  },
  maxVisible: {
    type: Number,
    default: 4
  }
})

const emit = defineEmits(['action'])

const showMenu = ref(false)
const pinned = ref(false)

const visibleActions = computed(() => props.actions.slice(0, props.maxVisible))
const overflowActions = computed(() => props.actions.slice(props.maxVisible))

function getButtonType(field) {
  const srcType = String(field?.type || '')
  if (['primary', 'ghost', 'danger', 'link'].includes(srcType)) return srcType
  const variant = String(field?.variant || 'default')
  if (variant === 'danger') return 'danger'
  if (variant === 'link') return 'link'
  if (['ghost', 'secondary', 'dark', 'light', 'default'].includes(variant)) return 'ghost'
  return 'primary'
}

function emitAction(field) {
  if (field?.disabled) return
  emit('action', {
    action: field.action,
    key: field.key,
    value: field.value,
    field
  })
}
</script>

<template>
  <div class="action-bar">
    <AppButton
      v-for="(field, index) in visibleActions"
      :key="`${field.key}-${index}`"
      :text="field.label"
      :type="getButtonType(field)"
      :loading="Boolean(field.loading)"
      :disabled="Boolean(field.disabled)"
      @click="emitAction(field)"
    />
    <div
      v-if="overflowActions.length"
      class="action-more"
      @mouseenter="!pinned && (showMenu = true)"
      @mouseleave="!pinned && (showMenu = false)"
    >
      <AppButton class="action-more__trigger" text="更多操作" type="ghost" @click="pinned = !pinned; showMenu = pinned || !showMenu" />
      <div v-if="showMenu" class="action-menu">
        <div v-for="(field, index) in overflowActions" :key="`${field.key}-more-${index}`" class="action-menu__item">
          <AppButton
            class="action-menu__btn"
            :text="field.label"
            :type="getButtonType(field)"
            :loading="Boolean(field.loading)"
            :disabled="Boolean(field.disabled)"
            @click="emitAction(field); showMenu = false; pinned = false"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.action-bar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  position: relative;
  flex-wrap: wrap;
}

.action-more {
  position: relative;
}

.action-menu {
  position: absolute;
  right: 0;
  top: 36px;
  min-width: 140px;
  background: var(--surface-bg-2);
  border: 1px solid var(--stroke);
  border-radius: 10px;
  padding: 6px;
  z-index: 10;
  box-shadow: var(--surface-shadow);
}

.action-menu__item + .action-menu__item {
  margin-top: 6px;
}

.action-menu__btn {
  width: 100%;
}
</style>
