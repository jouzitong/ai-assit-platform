export type AppOverlaySize = 'small' | 'medium' | 'large' | 'extra-large'
export type AppDialogActionMode = 'confirm' | 'close' | 'none'

export interface AppDialogProps {
  modelValue?: boolean
  title?: string
  description?: string
  size?: AppOverlaySize
  width?: string | number
  height?: string | number
  fullscreen?: boolean
  modal?: boolean
  lockScroll?: boolean
  draggable?: boolean
  overflow?: boolean
  destroyOnClose?: boolean
  closeOnClickModal?: boolean
  closeOnPressEscape?: boolean
  showClose?: boolean
  alignCenter?: boolean
  scrollable?: boolean
  actionMode?: AppDialogActionMode
  confirmText?: string
  cancelText?: string
  closeText?: string
  confirming?: boolean
  confirmDisabled?: boolean
  showCancel?: boolean
}

export interface AppDrawerProps {
  modelValue?: boolean
  title?: string
  description?: string
  size?: AppOverlaySize
  width?: string | number
  direction?: 'rtl' | 'ltr'
  modal?: boolean
  lockScroll?: boolean
  destroyOnClose?: boolean
  closeOnClickModal?: boolean
  closeOnPressEscape?: boolean
  showClose?: boolean
  showFooter?: boolean
  confirmText?: string
  cancelText?: string
  confirming?: boolean
  confirmDisabled?: boolean
}
