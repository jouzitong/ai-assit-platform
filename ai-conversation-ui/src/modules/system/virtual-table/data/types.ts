import type {
  VirtualBindingItem,
  VirtualEntityItem,
  VirtualFieldItem,
  VirtualRelationItem,
} from '../../api/virtualData'

export type RelationLineStyle = 'curve' | 'polyline' | 'straight'

export interface VirtualEntitySummary extends VirtualEntityItem {
  sources: string[]
  physicalTables: string[]
  fieldCount: number
  relationCount: number
}

export interface VirtualTableNodeData {
  entity: VirtualEntityItem
  fields: VirtualFieldItem[]
  bindings: VirtualBindingItem[]
  sourceLabels: string[]
}

export interface RelationEditorContext {
  relation: VirtualRelationItem | null
  sourceEntityId: string | number
  sourceFieldId: string | number
  targetEntityId: string | number
  targetFieldId: string | number
}
