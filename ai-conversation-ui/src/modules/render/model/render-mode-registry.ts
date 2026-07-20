import type { Component } from 'vue'
import DashboardModeHost from '../components/DashboardModeHost.vue'
import EmbeddedModeHost from '../components/EmbeddedModeHost.vue'
import ReportModeHost from '../components/ReportModeHost.vue'
import StandardModeHost from '../components/StandardModeHost.vue'
import type { RenderAppMode } from './render-app'

export interface RenderModeDefinition {
  key: RenderAppMode
  name: string
  component: Component
  supportsPrint: boolean
  usesResponsiveViewport: boolean
}

const RENDER_MODE_DEFINITIONS = [
  {
    key: 'standard',
    name: '标准页面',
    component: StandardModeHost,
    supportsPrint: false,
    usesResponsiveViewport: false,
  },
  {
    key: 'dashboard',
    name: '看板',
    component: DashboardModeHost,
    supportsPrint: false,
    usesResponsiveViewport: true,
  },
  {
    key: 'report',
    name: '报表',
    component: ReportModeHost,
    supportsPrint: true,
    usesResponsiveViewport: false,
  },
  {
    key: 'embedded',
    name: '嵌入页面',
    component: EmbeddedModeHost,
    supportsPrint: false,
    usesResponsiveViewport: false,
  },
] as const satisfies readonly RenderModeDefinition[]

const RENDER_MODE_MAP = new Map<RenderAppMode, RenderModeDefinition>(
  RENDER_MODE_DEFINITIONS.map(definition => [definition.key, definition]),
)

export function listRenderModes() {
  return [...RENDER_MODE_DEFINITIONS]
}

export function findRenderMode(mode: RenderAppMode) {
  return RENDER_MODE_MAP.get(mode)
}
