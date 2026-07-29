import {
  Download,
  FullScreen,
  Operation,
  Printer,
  RefreshRight,
} from '@element-plus/icons-vue'
import type { RendererAction } from '../schema/action'

const ACTION_ICONS = {
  download: Download,
  fullscreen: FullScreen,
  operation: Operation,
  print: Printer,
  refresh: RefreshRight,
} as const

export function resolveRendererActionIcon(action: RendererAction) {
  const icon = action.options?.icon?.toLowerCase()
  return icon && icon in ACTION_ICONS
    ? ACTION_ICONS[icon as keyof typeof ACTION_ICONS]
    : undefined
}
