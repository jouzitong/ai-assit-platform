import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const DB_ENGINE_API_PREFIX = getBackendService(SERVICE_NAMES.DB_ENGINE).gatewayPrefix
const VIRTUAL_DATA_API_PREFIX = `${DB_ENGINE_API_PREFIX}/api/v1/virtual-data`

export type VirtualDataId = string | number
export type CatalogStatus = 0 | 1 | 2
export type LogicalType = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8
export type BindingRole = 0 | 1
export type TransformMode = 0 | 1 | 2
export type FieldSide = 0 | 1

export interface SearchResult<T> {
  list?: T[]
  pageInfo?: {
    total?: number
  }
}

export interface SearchPayload {
  page?: number
  size?: number
  keyword?: string
  enabled?: boolean
}

export interface VirtualEntityItem {
  id: VirtualDataId
  entityCode?: string
  entityName?: string
  description?: string
  status?: CatalogStatus
  catalogVersion?: number
  enabled?: boolean
  createTime?: string
  updateTime?: string
}

export interface VirtualEntityPayload {
  entityCode: string
  entityName: string
  description?: string
  status: CatalogStatus
  catalogVersion?: number
  enabled: boolean
}

export interface VirtualFieldItem {
  id: VirtualDataId
  entityId?: VirtualDataId
  fieldCode?: string
  fieldName?: string
  logicalType?: LogicalType
  nullable?: boolean
  primaryKey?: boolean
  ordinalPosition?: number
  defaultValue?: string
  enabled?: boolean
  remark?: string
}

export interface VirtualFieldPayload {
  entityId: VirtualDataId
  fieldCode: string
  fieldName: string
  logicalType: LogicalType
  nullable: boolean
  primaryKey: boolean
  ordinalPosition: number
  defaultValue?: string
  enabled: boolean
  remark?: string
}

export interface VirtualBindingItem {
  id: VirtualDataId
  entityId?: VirtualDataId
  bindingCode?: string
  bindingGroup?: string
  bindingRole?: BindingRole
  physicalTableMetaId?: VirtualDataId
  sourceKey?: string
  physicalTableName?: string
  readable?: boolean
  writable?: boolean
  readWeight?: number
  writePriority?: number
  routingConfig?: Record<string, unknown>
  enabled?: boolean
  remark?: string
}

export interface VirtualBindingPayload {
  entityId: VirtualDataId
  bindingCode: string
  bindingGroup: string
  bindingRole: BindingRole
  physicalTableMetaId?: VirtualDataId
  sourceKey: string
  physicalTableName: string
  readable: boolean
  writable: boolean
  readWeight: number
  writePriority: number
  routingConfig: Record<string, unknown>
  enabled: boolean
  remark?: string
}

export interface FieldTransformRuleItem {
  id: VirtualDataId
  bindingId?: VirtualDataId
  ruleCode?: string
  ruleName?: string
  transformMode?: TransformMode
  readTransformerCode?: string
  readTransformerVersion?: number
  writeTransformerCode?: string
  writeTransformerVersion?: number
  readConfig?: Record<string, unknown>
  writeConfig?: Record<string, unknown>
  enabled?: boolean
  remark?: string
}

export interface FieldTransformRulePayload {
  bindingId: VirtualDataId
  ruleCode: string
  ruleName: string
  transformMode: TransformMode
  readTransformerCode?: string
  readTransformerVersion?: number
  writeTransformerCode?: string
  writeTransformerVersion?: number
  readConfig: Record<string, unknown>
  writeConfig: Record<string, unknown>
  enabled: boolean
  remark?: string
}

export interface FieldTransformPortItem {
  id: VirtualDataId
  ruleId?: VirtualDataId
  fieldSide?: FieldSide
  portCode?: string
  virtualFieldId?: VirtualDataId
  physicalFieldMetaId?: VirtualDataId
  physicalColumnName?: string
  ordinalPosition?: number
  requiredOnWrite?: boolean
  remark?: string
}

export interface FieldTransformPortPayload {
  ruleId: VirtualDataId
  fieldSide: FieldSide
  portCode: string
  virtualFieldId?: VirtualDataId
  physicalFieldMetaId?: VirtualDataId
  physicalColumnName?: string
  ordinalPosition: number
  requiredOnWrite: boolean
  remark?: string
}

export interface VirtualRelationItem {
  id: VirtualDataId
  relationCode?: string
  relationName?: string
  sourceEntityId?: VirtualDataId
  sourceFieldId?: VirtualDataId
  targetEntityId?: VirtualDataId
  targetFieldId?: VirtualDataId
  enabled?: boolean
  remark?: string
}

export interface VirtualRelationPayload {
  relationCode: string
  relationName: string
  sourceEntityId: VirtualDataId
  sourceFieldId: VirtualDataId
  targetEntityId: VirtualDataId
  targetFieldId: VirtualDataId
  enabled: boolean
  remark?: string
}

export interface CatalogSnapshot {
  entityId?: VirtualDataId
  entityCode?: string
  entityName?: string
  status?: CatalogStatus
  catalogVersion?: number
  enabled?: boolean
}

export interface VirtualKnowledgePreviewResult {
  type?: string
  content?: string
}

export interface VirtualKnowledgeStatusItem {
  entityId: VirtualDataId
  kbCodes?: string[]
}

export interface VirtualKnowledgeSyncPayload {
  kbCode: string
  entityIds: VirtualDataId[]
}

export interface VirtualKnowledgeSyncResult {
  kbCode?: string
  totalCount?: number
  createdCount?: number
  updatedCount?: number
  unchangedCount?: number
}

export interface VirtualUnpublishResult {
  unpublishedCount?: number
  deletedDocumentCount?: number
}

export interface TransformerDescriptor {
  code?: string
  version?: number
  capabilities?: {
    readable?: boolean
    writable?: boolean
    deterministic?: boolean
  }
}

function search<T, P extends object = object>(path: string, payload: P) {
  return request<SearchResult<T>>(`${VIRTUAL_DATA_API_PREFIX}/${path}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

function create<T>(path: string, payload: unknown) {
  return request<T>(`${VIRTUAL_DATA_API_PREFIX}/${path}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

function update<T>(path: string, id: VirtualDataId, payload: unknown) {
  return request<T>(`${VIRTUAL_DATA_API_PREFIX}/${path}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

function remove(path: string, id: VirtualDataId) {
  return request(`${VIRTUAL_DATA_API_PREFIX}/${path}/${id}`, { method: 'DELETE' })
}

export function searchVirtualEntities(payload: SearchPayload & { entityCode?: string; status?: CatalogStatus } = {}) {
  return search<VirtualEntityItem>('entities', payload)
}

export function updateVirtualEntity(id: VirtualDataId, payload: VirtualEntityPayload) {
  return update<VirtualEntityItem>('entities', id, payload)
}

export function deleteVirtualEntity(id: VirtualDataId) {
  return remove('entities', id)
}

export function searchVirtualFields(payload: SearchPayload & { entityId?: VirtualDataId; fieldCode?: string } = {}) {
  return search<VirtualFieldItem>('fields', payload)
}

export function createVirtualField(payload: VirtualFieldPayload) {
  return create<VirtualFieldItem>('fields', payload)
}

export function updateVirtualField(id: VirtualDataId, payload: VirtualFieldPayload) {
  return update<VirtualFieldItem>('fields', id, payload)
}

export function deleteVirtualField(id: VirtualDataId) {
  return remove('fields', id)
}

export function searchVirtualBindings(payload: SearchPayload & { entityId?: VirtualDataId; bindingCode?: string; sourceKey?: string } = {}) {
  return search<VirtualBindingItem>('bindings', payload)
}

export function createVirtualBinding(payload: VirtualBindingPayload) {
  return create<VirtualBindingItem>('bindings', payload)
}

export function updateVirtualBinding(id: VirtualDataId, payload: VirtualBindingPayload) {
  return update<VirtualBindingItem>('bindings', id, payload)
}

export function deleteVirtualBinding(id: VirtualDataId) {
  return remove('bindings', id)
}

export function searchFieldTransformRules(payload: SearchPayload & { bindingId?: VirtualDataId; ruleCode?: string } = {}) {
  return search<FieldTransformRuleItem>('field-transform-rules', payload)
}

export function createFieldTransformRule(payload: FieldTransformRulePayload) {
  return create<FieldTransformRuleItem>('field-transform-rules', payload)
}

export function updateFieldTransformRule(id: VirtualDataId, payload: FieldTransformRulePayload) {
  return update<FieldTransformRuleItem>('field-transform-rules', id, payload)
}

export function deleteFieldTransformRule(id: VirtualDataId) {
  return remove('field-transform-rules', id)
}

export function searchFieldTransformPorts(payload: SearchPayload & { ruleId?: VirtualDataId; fieldSide?: FieldSide } = {}) {
  return search<FieldTransformPortItem>('field-transform-ports', payload)
}

export function createFieldTransformPort(payload: FieldTransformPortPayload) {
  return create<FieldTransformPortItem>('field-transform-ports', payload)
}

export function updateFieldTransformPort(id: VirtualDataId, payload: FieldTransformPortPayload) {
  return update<FieldTransformPortItem>('field-transform-ports', id, payload)
}

export function deleteFieldTransformPort(id: VirtualDataId) {
  return remove('field-transform-ports', id)
}

export function searchVirtualRelations(payload: SearchPayload & { sourceEntityId?: VirtualDataId; targetEntityId?: VirtualDataId } = {}) {
  return search<VirtualRelationItem>('relations', payload)
}

export function createVirtualRelation(payload: VirtualRelationPayload) {
  return create<VirtualRelationItem>('relations', payload)
}

export function updateVirtualRelation(id: VirtualDataId, payload: VirtualRelationPayload) {
  return update<VirtualRelationItem>('relations', id, payload)
}

export function deleteVirtualRelation(id: VirtualDataId) {
  return remove('relations', id)
}

export function createVirtualEntityFromPhysicalTable(payload: { physicalTableMetaId: VirtualDataId; entityCode?: string; entityName?: string }) {
  return request<CatalogSnapshot>(`${VIRTUAL_DATA_API_PREFIX}/entities/from-physical-table`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function validateVirtualCatalog(entityId: VirtualDataId) {
  return request<void>(`${VIRTUAL_DATA_API_PREFIX}/validate`, {
    method: 'POST',
    query: { entityId },
  })
}

export function publishVirtualCatalog(entityId: VirtualDataId) {
  return request<CatalogSnapshot>(`${VIRTUAL_DATA_API_PREFIX}/publish`, {
    method: 'POST',
    query: { entityId },
  })
}

export function publishVirtualCatalogBatch(entityIds: VirtualDataId[]) {
  return request<CatalogSnapshot[]>(`${VIRTUAL_DATA_API_PREFIX}/publish-batch`, {
    method: 'POST',
    body: JSON.stringify({ entityIds }),
  })
}

export function previewVirtualKnowledge(entityId: VirtualDataId) {
  return request<VirtualKnowledgePreviewResult>(`${VIRTUAL_DATA_API_PREFIX}/knowledge-preview`, {
    method: 'GET',
    query: { entityId },
  })
}

export function getVirtualKnowledgeStatus(entityIds: VirtualDataId[]) {
  return request<VirtualKnowledgeStatusItem[]>(`${VIRTUAL_DATA_API_PREFIX}/knowledge-status`, {
    method: 'POST',
    body: JSON.stringify({ entityIds }),
  })
}

export function syncVirtualKnowledge(payload: VirtualKnowledgeSyncPayload) {
  return request<VirtualKnowledgeSyncResult>(`${VIRTUAL_DATA_API_PREFIX}/knowledge-sync`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function checkVirtualUnpublish(entityIds: VirtualDataId[]) {
  return request<VirtualKnowledgeStatusItem[]>(`${VIRTUAL_DATA_API_PREFIX}/unpublish-check`, {
    method: 'POST',
    body: JSON.stringify({ entityIds }),
  })
}

export function unpublishVirtualCatalog(entityIds: VirtualDataId[]) {
  return request<VirtualUnpublishResult>(`${VIRTUAL_DATA_API_PREFIX}/unpublish`, {
    method: 'POST',
    body: JSON.stringify({ entityIds }),
  })
}

export function listFieldTransformers() {
  return request<TransformerDescriptor[]>(`${VIRTUAL_DATA_API_PREFIX}/field-transformers`, { method: 'GET' })
}

export function validateFieldTransformRule(ruleId: VirtualDataId) {
  return request<void>(`${VIRTUAL_DATA_API_PREFIX}/field-transform-rules/validate`, {
    method: 'POST',
    query: { ruleId },
  })
}
