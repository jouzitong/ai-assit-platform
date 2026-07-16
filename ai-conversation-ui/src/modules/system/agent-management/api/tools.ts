import type { CatalogQuery, ToolDefinition } from '../types'
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

export const listTools = (query: CatalogQuery = {}) => listCatalog<ToolDefinition>('tools', query)
export const getTool = (code: string) => getCatalog<ToolDefinition>('tools', code)
export const createTool = (payload: ToolDefinition) => createCatalog<ToolDefinition, ToolDefinition>('tools', payload)
export const updateTool = (code: string, payload: ToolDefinition) => updateCatalog<ToolDefinition, ToolDefinition>('tools', code, payload)
export const deleteTool = (code: string) => deleteCatalog('tools', code)
export const createToolVersion = (code: string, payload: ToolDefinition) => createDefinitionVersion<ToolDefinition, ToolDefinition>('tools', code, payload)
export const validateTool = (code: string, version: number) => validateDefinition('tools', code, version)
export const publishTool = (code: string, version: number) => publishDefinition('tools', code, version)
export const testTool = (code: string, version: number, payload: Record<string, unknown>) => testDefinition('tools', code, version, payload)
