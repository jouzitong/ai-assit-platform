import {
  listDbAccessTables,
  searchDbTableFields,
  searchDbTables,
  syncDbAccessTableMeta,
  type DbTableFieldMetaItem,
  type DbTableMetaItem,
} from '../../api/dataSources'
import {
  createFieldTransformPort,
  createFieldTransformRule,
  createVirtualEntityFromPhysicalTable,
  deleteFieldTransformPort,
  searchFieldTransformPorts,
  searchFieldTransformRules,
  searchVirtualBindings,
  searchVirtualEntities,
  searchVirtualFields,
  searchVirtualRelations,
  updateFieldTransformPort,
  updateFieldTransformRule,
  type FieldTransformPortItem,
  type FieldTransformPortPayload,
  type FieldTransformRuleItem,
  type FieldTransformRulePayload,
  type VirtualBindingItem,
  type VirtualDataId,
  type VirtualEntityItem,
  type VirtualFieldItem,
  type VirtualRelationItem,
} from '../../api/virtualData'

const CATALOG_PAGE_SIZE = 2000

export interface VirtualCatalogOverview {
  entities: VirtualEntityItem[]
  fields: VirtualFieldItem[]
  bindings: VirtualBindingItem[]
  relations: VirtualRelationItem[]
}

export interface VirtualTableWorkspace {
  fields: VirtualFieldItem[]
  bindings: VirtualBindingItem[]
  rules: FieldTransformRuleItem[]
  ports: FieldTransformPortItem[]
  physicalFields: DbTableFieldMetaItem[]
}

export interface InitializeResult {
  created: string[]
  failed: Array<{ tableName: string; message: string }>
}

export function buildVirtualEntityCode(sourceKey?: string, tableName?: string) {
  const raw = `${sourceKey || ''}_${tableName || ''}`
  let code = raw
    .replace(/[^A-Za-z0-9_]/g, '_')
    .replace(/_+/g, '_')
  if (!code || !/^[A-Za-z]/.test(code)) {
    code = `v_${code || 'virtual_table'}`
  }
  return code.slice(0, 64).replace(/_+$/g, '') || 'virtual_table'
}

export async function loadVirtualCatalogOverview(): Promise<VirtualCatalogOverview> {
  const [entityResult, fieldResult, bindingResult, relationResult] = await Promise.all([
    searchVirtualEntities({ page: 1, size: CATALOG_PAGE_SIZE }),
    searchVirtualFields({ page: 1, size: CATALOG_PAGE_SIZE }),
    searchVirtualBindings({ page: 1, size: CATALOG_PAGE_SIZE }),
    searchVirtualRelations({ page: 1, size: CATALOG_PAGE_SIZE }),
  ])
  return {
    entities: entityResult?.list || [],
    fields: fieldResult?.list || [],
    bindings: bindingResult?.list || [],
    relations: relationResult?.list || [],
  }
}

export async function loadPhysicalTables(sourceKey: string): Promise<DbTableMetaItem[]> {
  const result = await searchDbTables({ page: 1, size: CATALOG_PAGE_SIZE, sourceKey, enabled: true })
  return result?.list || []
}

export async function syncPhysicalTableMetadata(sourceKey: string) {
  const candidates = await listDbAccessTables({ sourceKey })
  const tableNames = (candidates?.tables || [])
    .map(item => item.tableName?.trim() || '')
    .filter(Boolean)
  if (!tableNames.length) {
    return { tableCount: 0 }
  }
  await syncDbAccessTableMeta({ sourceKey, tables: tableNames })
  return { tableCount: tableNames.length }
}

export async function initializeVirtualTables(sourceKey: string, tables: DbTableMetaItem[]): Promise<InitializeResult> {
  const results = await Promise.allSettled(tables.map(table => createVirtualEntityFromPhysicalTable({
    physicalTableMetaId: table.id,
    entityCode: buildVirtualEntityCode(sourceKey, table.tableName),
    entityName: buildVirtualEntityCode(sourceKey, table.tableName),
  })))

  const summary: InitializeResult = { created: [], failed: [] }
  results.forEach((result, index) => {
    const tableName = tables[index]?.tableName || String(tables[index]?.id || '-')
    if (result.status === 'fulfilled') {
      summary.created.push(tableName)
      return
    }
    summary.failed.push({
      tableName,
      message: result.reason instanceof Error ? result.reason.message : '初始化失败',
    })
  })
  return summary
}

export async function loadVirtualTableWorkspace(entityId: VirtualDataId): Promise<VirtualTableWorkspace> {
  const [fieldResult, bindingResult] = await Promise.all([
    searchVirtualFields({ page: 1, size: CATALOG_PAGE_SIZE, entityId }),
    searchVirtualBindings({ page: 1, size: CATALOG_PAGE_SIZE, entityId }),
  ])
  const fields = fieldResult?.list || []
  const bindings = bindingResult?.list || []
  const ruleGroups = await Promise.all(bindings.map(binding => searchFieldTransformRules({
    page: 1,
    size: CATALOG_PAGE_SIZE,
    bindingId: binding.id,
  })))
  const rules = ruleGroups.flatMap(result => result?.list || [])
  const portGroups = await Promise.all(rules.map(rule => searchFieldTransformPorts({
    page: 1,
    size: CATALOG_PAGE_SIZE,
    ruleId: rule.id,
  })))
  const physicalFieldGroups = await Promise.all(bindings.map(binding => searchDbTableFields({
    page: 1,
    size: CATALOG_PAGE_SIZE,
    sourceKey: binding.sourceKey,
    tableName: binding.physicalTableName,
    enabled: true,
  })))
  return {
    fields,
    bindings,
    rules,
    ports: portGroups.flatMap(result => result?.list || []),
    physicalFields: physicalFieldGroups.flatMap(result => result?.list || []),
  }
}

export async function saveTransformRuleWithPorts(
  ruleId: VirtualDataId | null,
  payload: FieldTransformRulePayload,
  ports: Array<FieldTransformPortPayload & { id?: VirtualDataId }>,
  existingPorts: FieldTransformPortItem[],
) {
  const savedRule = ruleId === null
    ? await createFieldTransformRule(payload)
    : await updateFieldTransformRule(ruleId, payload)
  let resolvedRuleId: VirtualDataId | undefined = ruleId ?? savedRule?.id
  if (!resolvedRuleId) {
    const result = await searchFieldTransformRules({
      page: 1,
      size: CATALOG_PAGE_SIZE,
      bindingId: payload.bindingId,
      ruleCode: payload.ruleCode,
    })
    resolvedRuleId = result?.list?.find(item => item.ruleCode === payload.ruleCode)?.id
  }
  if (!resolvedRuleId) {
    throw new Error('规则已保存，但未能解析规则 ID，请刷新后继续配置端口')
  }
  const finalRuleId = resolvedRuleId

  const retainedIds = new Set(ports.map(port => port.id).filter((id): id is VirtualDataId => id !== undefined))
  await Promise.all(existingPorts
    .filter(port => !retainedIds.has(port.id))
    .map(port => deleteFieldTransformPort(port.id)))
  await Promise.all(ports.map((port) => {
    const { id, ...portPayload } = port
    const finalPayload: FieldTransformPortPayload = { ...portPayload, ruleId: finalRuleId }
    return id === undefined
      ? createFieldTransformPort(finalPayload)
      : updateFieldTransformPort(id, finalPayload)
  }))
  return resolvedRuleId
}
