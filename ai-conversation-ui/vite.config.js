import { readdirSync } from 'node:fs'
import { dirname, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { APPLICATION_COMPONENT_MANIFEST } from './src/application/component-manifest.ts'
import { assertApplicationRendererAssetDefinitions } from './src/application/component-manifest-validation.ts'
import { APPLICATION_LAYOUT_CATALOG } from './src/application/layout/catalog.ts'
import { APPLICATION_RENDERER_CATALOG } from './src/application/registry/catalog.ts'
import { APPLICATION_STATIC_RENDER_NODE_CATALOG } from './src/application/runtime/node-catalog.ts'

const projectRoot = dirname(fileURLToPath(import.meta.url))
const rendererRoot = resolve(projectRoot, 'src/application/renderers')

function listRendererEntryFiles(directory = rendererRoot) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const absolutePath = resolve(directory, entry.name)
    if (entry.isDirectory()) {
      return entry.name === 'components' ? [] : listRendererEntryFiles(absolutePath)
    }
    if (!entry.isFile() || !entry.name.endsWith('.vue')) return []
    return [relative(projectRoot, absolutePath).split(sep).join('/')]
  })
}

function assertRendererSourceCoverage() {
  const sourceFiles = listRendererEntryFiles()
  const catalogSources = APPLICATION_RENDERER_CATALOG.map(item => item.sourcePath)
  const uncataloguedFiles = sourceFiles.filter(sourcePath => !catalogSources.includes(sourcePath))
  const missingFiles = catalogSources.filter(sourcePath => !sourceFiles.includes(sourcePath))
  if (uncataloguedFiles.length || missingFiles.length) {
    const errors = [
      uncataloguedFiles.length ? `未声明 exposure 的 Renderer：${uncataloguedFiles.join(', ')}` : '',
      missingFiles.length ? `Catalog 指向不存在的 Renderer：${missingFiles.join(', ')}` : '',
    ].filter(Boolean)
    throw new Error(`Application Renderer 文件目录校验失败：\n- ${errors.join('\n- ')}`)
  }
}

export default defineConfig(() => {
  assertRendererSourceCoverage()
  assertApplicationRendererAssetDefinitions(
    APPLICATION_RENDERER_CATALOG,
    APPLICATION_COMPONENT_MANIFEST,
    [...APPLICATION_LAYOUT_CATALOG, ...APPLICATION_STATIC_RENDER_NODE_CATALOG],
  )

  return {
    plugins: [vue()],
  }
})
