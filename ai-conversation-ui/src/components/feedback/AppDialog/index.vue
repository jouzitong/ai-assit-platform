<script setup lang="ts">
import { computed, useSlots } from 'vue'
import { useResponsiveOverlayTarget } from '../../../composables/useResponsiveViewport'
import LayoutDialogFooter from '../../layout/LayoutDialogFooter/index.vue'
import type { AppDialogProps, AppOverlaySize } from '../types'

defineOptions({
  inheritAttrs: false,
})

const props = withDefaults(defineProps<AppDialogProps>(), {
  modelValue: false,
  title: '',
  description: '',
  size: 'medium',
  fullscreen: false,
  modal: true,
  lockScroll: true,
  draggable: false,
  overflow: false,
  destroyOnClose: true,
  closeOnClickModal: false,
  closeOnPressEscape: true,
  showClose: true,
  alignCenter: true,
  scrollable: true,
  actionMode: 'none',
  confirmText: '确定',
  cancelText: '取消',
  closeText: '关闭',
  confirming: false,
  confirmDisabled: false,
  showCancel: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
  close: []
}>()

const slots = useSlots()
const responsiveOverlayTarget = useResponsiveOverlayTarget()

const widthTokens: Record<AppOverlaySize, string> = {
  small: 'var(--app-dialog-width-sm)',
  medium: 'var(--app-dialog-width-md)',
  large: 'var(--app-dialog-width-lg)',
  'extra-large': 'var(--app-dialog-width-xl)',
}

const resolvedWidth = computed(() => props.width || widthTokens[props.size])
const overlayTarget = computed(() => responsiveOverlayTarget.value || 'body')
const shouldRenderFooter = computed(() => (
  props.actionMode !== 'none'
  || Boolean(slots.footer)
  || Boolean(slots['footer-extra'])
))
const resolvedHeight = computed(() => {
  if (props.fullscreen || props.height === undefined || props.height === '') {
    return undefined
  }
  return typeof props.height === 'number' ? `${props.height}px` : props.height
})

function cancel() {
  emit('cancel')
  emit('update:modelValue', false)
}

function close() {
  emit('close')
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    class="app-dialog-shell"
    :class="{ 'app-dialog-shell--fixed-height': resolvedHeight }"
    :style="{ height: resolvedHeight }"
    modal-class="app-overlay-mask"
    :model-value="modelValue"
    :width="resolvedWidth"
    :fullscreen="fullscreen"
    :modal="modal"
    :lock-scroll="lockScroll"
    :draggable="draggable"
    :overflow="overflow"
    :destroy-on-close="destroyOnClose"
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :show-close="showClose"
    :align-center="alignCenter"
    :append-to="overlayTarget"
    :append-to-body="overlayTarget === 'body'"
    body-class="app-dialog-shell__body"
    header-class="app-dialog-shell__header"
    footer-class="app-dialog-shell__footer"
    v-bind="$attrs"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header="slotProps">
      <slot name="header" v-bind="slotProps">
        <div class="app-dialog-shell__heading">
          <span :id="slotProps.titleId" :class="slotProps.titleClass">{{ title }}</span>
          <p v-if="description" class="app-dialog-shell__description">{{ description }}</p>
        </div>
      </slot>
    </template>

    <div
      class="app-dialog-shell__content"
      :class="{ 'app-dialog-shell__content--scrollable': scrollable }"
    >
      <slot />
    </div>

    <template v-if="shouldRenderFooter" #footer>
      <slot
        name="footer"
        :action-mode="actionMode"
        :cancel="cancel"
        :close="close"
        :confirm="() => emit('confirm')"
      >
        <LayoutDialogFooter align="between" class="app-dialog-shell__footer-layout">
          <div v-if="slots['footer-extra']" class="app-dialog-shell__footer-extra">
            <slot name="footer-extra" />
          </div>
          <div v-if="actionMode !== 'none'" class="app-dialog-shell__footer-actions">
            <template v-if="actionMode === 'confirm'">
              <el-button v-if="showCancel" :disabled="confirming" @click="cancel">
                {{ cancelText }}
              </el-button>
              <el-button
                type="primary"
                :loading="confirming"
                :disabled="confirmDisabled"
                @click="emit('confirm')"
              >
                {{ confirmText }}
              </el-button>
            </template>
            <el-button v-else type="primary" @click="close">{{ closeText }}</el-button>
          </div>
        </LayoutDialogFooter>
      </slot>
    </template>
  </el-dialog>
</template>

<style>
.app-dialog-shell.el-dialog {
  --el-dialog-padding-primary: var(--app-dialog-padding);
  display: flex;
  flex-direction: column;
  max-width: calc(100cqw - var(--app-space-6) * 2);
  max-height: calc(100cqh - var(--app-space-6) * 2);
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-dialog-radius);
  background: var(--app-surface-solid);
  box-shadow: var(--app-dialog-shadow);
}

.app-dialog-shell.el-dialog.is-fullscreen {
  max-width: none;
  max-height: none;
  border: 0;
  border-radius: 0;
}

.app-dialog-shell__header.el-dialog__header {
  flex: 0 0 auto;
  margin: 0;
  padding: var(--app-dialog-header-padding);
  border-bottom: 1px solid var(--app-border-subtle);
}

.app-dialog-shell__heading {
  min-width: 0;
  padding-right: var(--app-space-7);
}

.app-dialog-shell__heading .el-dialog__title {
  color: var(--app-title);
  font-size: var(--app-font-size-title-sm);
  font-weight: 600;
  line-height: var(--app-line-height-tight);
}

.app-dialog-shell__description {
  margin: var(--app-space-1) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-body);
}

.app-dialog-shell__body.el-dialog__body {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  padding: 0;
  color: var(--app-text);
  overflow: hidden;
}

.app-dialog-shell__content {
  box-sizing: border-box;
  min-width: 0;
  padding: var(--app-dialog-body-padding);
}

.app-dialog-shell__content--scrollable {
  max-height: var(--app-dialog-body-max-height);
  overflow: auto;
  overscroll-behavior: contain;
}

.app-dialog-shell--fixed-height .app-dialog-shell__content--scrollable,
.app-dialog-shell.is-fullscreen .app-dialog-shell__content--scrollable {
  height: 100%;
  max-height: none;
}

.app-dialog-shell__footer.el-dialog__footer {
  flex: 0 0 auto;
  padding: var(--app-dialog-footer-padding);
  border-top: 1px solid var(--app-border-subtle);
  background: var(--app-surface-muted);
}

.app-dialog-shell__footer-layout {
  width: 100%;
}

.app-dialog-shell__footer-extra,
.app-dialog-shell__footer-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--app-space-2);
}

.app-dialog-shell__footer-actions {
  margin-left: auto;
}

.app-dialog-shell .el-dialog__headerbtn {
  top: var(--app-space-3);
  right: var(--app-space-3);
  width: var(--app-control-height-md);
  height: var(--app-control-height-md);
  border-radius: var(--app-radius-md);
}

.app-dialog-shell .el-dialog__headerbtn:hover {
  background: var(--app-accent-bg);
}

.app-overlay-mask {
  background: var(--app-overlay-mask);
  backdrop-filter: blur(2px);
}

@container (max-width: 720px) {
  .app-dialog-shell.el-dialog:not(.is-fullscreen) {
    max-width: calc(100cqw - var(--app-space-4) * 2);
    max-height: calc(100cqh - var(--app-space-4) * 2);
  }
}
</style>
