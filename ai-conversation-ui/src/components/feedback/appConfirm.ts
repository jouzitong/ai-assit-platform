import { ElMessageBox, type ElMessageBoxOptions } from 'element-plus'
import { useResponsiveOverlayTarget } from '../../composables/useResponsiveViewport'

export interface AppConfirmOptions extends ElMessageBoxOptions {
  title?: string
  confirmButtonText?: string
  cancelButtonText?: string
  danger?: boolean
}

export async function appConfirm(message: string, options: AppConfirmOptions = {}) {
  const {
    title = '操作确认',
    confirmButtonText = '确定',
    cancelButtonText = '取消',
    danger = false,
    ...messageBoxOptions
  } = options

  try {
    await ElMessageBox.confirm(message, title, {
      type: danger ? 'warning' : 'info',
      confirmButtonText,
      cancelButtonText,
      closeOnClickModal: false,
      closeOnPressEscape: true,
      distinguishCancelAndClose: true,
      confirmButtonClass: danger ? 'el-button--danger' : '',
      ...messageBoxOptions,
    })
    return true
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') {
      return false
    }
    throw reason
  }
}

export function useAppConfirm() {
  const responsiveOverlayTarget = useResponsiveOverlayTarget()

  return (message: string, options: AppConfirmOptions = {}) => appConfirm(message, {
    appendTo: responsiveOverlayTarget.value || 'body',
    ...options,
  })
}
