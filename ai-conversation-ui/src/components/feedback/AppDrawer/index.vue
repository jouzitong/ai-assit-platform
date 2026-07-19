<script setup lang="ts">
import { computed, useSlots } from 'vue'
import { useResponsiveOverlayTarget } from '../../../composables/useResponsiveViewport'
import LayoutDialogFooter from '../../layout/LayoutDialogFooter/index.vue'
import type { AppDrawerProps, AppOverlaySize } from '../types'

defineOptions({ inheritAttrs: false })

const props = withDefaults(defineProps<AppDrawerProps>(), {
  modelValue: false,
  title: '',
  description: '',
  size: 'medium',
  direction: 'rtl',
  modal: true,
  lockScroll: true,
  destroyOnClose: true,
  closeOnClickModal: false,
  closeOnPressEscape: true,
  showClose: true,
  showFooter: true,
  confirmText: '确定',
  cancelText: '取消',
  confirming: false,
  confirmDisabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()

const slots = useSlots()
const responsiveOverlayTarget = useResponsiveOverlayTarget()

const widthTokens: Record<AppOverlaySize, string> = {
  small: 'var(--app-drawer-width-sm)',
  medium: 'var(--app-drawer-width-md)',
  large: 'var(--app-drawer-width-lg)',
  'extra-large': 'var(--app-drawer-width-xl)',
}

const resolvedWidth = computed(() => props.width || widthTokens[props.size])
const overlayTarget = computed(() => responsiveOverlayTarget.value || 'body')

function cancel() {
  emit('cancel')
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer
    class="app-drawer-shell"
    modal-class="app-overlay-mask"
    :model-value="modelValue"
    :title="title"
    :size="resolvedWidth"
    :direction="direction"
    :modal="modal"
    :lock-scroll="lockScroll"
    :destroy-on-close="destroyOnClose"
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :show-close="showClose"
    :append-to="overlayTarget"
    :append-to-body="overlayTarget === 'body'"
    body-class="app-drawer-shell__body"
    header-class="app-drawer-shell__header"
    footer-class="app-drawer-shell__footer"
    v-bind="$attrs"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header="slotProps">
      <slot name="header" v-bind="slotProps">
        <div class="app-drawer-shell__heading">
          <span class="app-drawer-shell__title">{{ title }}</span>
          <p v-if="description" class="app-drawer-shell__description">{{ description }}</p>
        </div>
      </slot>
    </template>

    <div class="app-drawer-shell__content">
      <slot />
    </div>

    <template v-if="showFooter || slots.footer || slots['footer-extra']" #footer>
      <slot name="footer" :cancel="cancel" :confirm="() => emit('confirm')">
        <LayoutDialogFooter align="between" class="app-drawer-shell__footer-layout">
          <div v-if="slots['footer-extra']" class="app-drawer-shell__footer-extra">
            <slot name="footer-extra" />
          </div>
          <div v-if="showFooter" class="app-drawer-shell__footer-actions">
            <el-button :disabled="confirming" @click="cancel">{{ cancelText }}</el-button>
            <el-button
              type="primary"
              :loading="confirming"
              :disabled="confirmDisabled"
              @click="emit('confirm')"
            >
              {{ confirmText }}
            </el-button>
          </div>
        </LayoutDialogFooter>
      </slot>
    </template>
  </el-drawer>
</template>

<style>
.app-drawer-shell.el-drawer {
  display: flex;
  flex-direction: column;
  max-width: calc(100cqw - var(--app-space-6));
  max-height: 100cqh;
  overflow: hidden;
  border-left: 1px solid var(--app-border-subtle);
  background: var(--app-surface-solid);
  box-shadow: var(--app-dialog-shadow);
}

.app-drawer-shell__header.el-drawer__header {
  flex: 0 0 auto;
  margin: 0;
  padding: var(--app-dialog-header-padding);
  border-bottom: 1px solid var(--app-border-subtle);
}

.app-drawer-shell__heading {
  min-width: 0;
}

.app-drawer-shell__title {
  color: var(--app-title);
  font-size: var(--app-font-size-title-sm);
  font-weight: 600;
  line-height: var(--app-line-height-tight);
}

.app-drawer-shell__description {
  margin: var(--app-space-1) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-body);
}

.app-drawer-shell__body.el-drawer__body {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  padding: 0;
  overflow: auto;
}

.app-drawer-shell__content {
  min-width: 0;
  padding: var(--app-dialog-body-padding);
}

.app-drawer-shell__footer.el-drawer__footer {
  flex: 0 0 auto;
  padding: var(--app-dialog-footer-padding);
  border-top: 1px solid var(--app-border-subtle);
  background: var(--app-surface-muted);
}

.app-drawer-shell__footer-layout {
  width: 100%;
}

.app-drawer-shell__footer-extra,
.app-drawer-shell__footer-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--app-space-2);
}

.app-drawer-shell__footer-actions {
  margin-left: auto;
}
</style>
