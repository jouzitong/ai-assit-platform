import type { ArtifactWorkflowDefinition, CatalogQuery } from '../types'
import {
  createCatalog,
  createDefinitionVersion,
  deleteCatalog,
  getCatalog,
  listCatalog,
  publishDefinition,
  testDefinition,
  updateCatalog,
  validateDefinition,
} from './client'

export const listWorkflows = (query: CatalogQuery = {}) => listCatalog<ArtifactWorkflowDefinition>('workflows', query)
export const getWorkflow = (code: string) => getCatalog<ArtifactWorkflowDefinition>('workflows', code)
export const createWorkflow = (payload: ArtifactWorkflowDefinition) => createCatalog<ArtifactWorkflowDefinition, ArtifactWorkflowDefinition>('workflows', payload)
export const updateWorkflow = (code: string, payload: ArtifactWorkflowDefinition) => updateCatalog<ArtifactWorkflowDefinition, ArtifactWorkflowDefinition>('workflows', code, payload)
export const deleteWorkflow = (code: string) => deleteCatalog('workflows', code)
export const createWorkflowVersion = (code: string, payload: ArtifactWorkflowDefinition) => createDefinitionVersion<ArtifactWorkflowDefinition, ArtifactWorkflowDefinition>('workflows', code, payload)
export const validateWorkflow = (code: string, version: number) => validateDefinition('workflows', code, version)
export const publishWorkflow = (code: string, version: number) => publishDefinition('workflows', code, version)
export const testWorkflow = (code: string, version: number, payload: Record<string, unknown>) => testDefinition('workflows', code, version, payload)
