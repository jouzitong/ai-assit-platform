import { request } from '../../../../api/request'
import type {
  CatalogQuery,
  SkillDefinition,
  SkillPackageInspection,
} from '../types'
import {
  agentManagementPath,
  deleteCatalog,
  getCatalog,
  listCatalog,
  publishDefinition,
  updateCatalog,
  validateDefinition,
} from './client'

export const listSkills = (query: CatalogQuery = {}) => listCatalog<SkillDefinition>('skills', query)
export const getSkill = (code: string) => getCatalog<SkillDefinition>('skills', code)
export const updateSkill = (code: string, payload: SkillDefinition) => updateCatalog<SkillDefinition, SkillDefinition>('skills', code, payload)
export const deleteSkill = (code: string) => deleteCatalog('skills', code)
export const validateSkill = (code: string, version: number) => validateDefinition('skills', code, version)
export const publishSkill = (code: string, version: number) => publishDefinition('skills', code, version)

export function createFormSkill(payload: SkillDefinition) {
  return request<SkillDefinition>(agentManagementPath('/skills/form'), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function inspectSkillPackage(file: File) {
  const body = new FormData()
  body.append('file', file)
  return request<SkillPackageInspection>(agentManagementPath('/skills/packages/inspect'), {
    method: 'POST',
    body,
  })
}

export function importSkillPackage(draftId: string, payload: Partial<SkillDefinition> = {}) {
  return request<SkillDefinition>(agentManagementPath(`/skills/packages/${encodeURIComponent(draftId)}/import`), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
