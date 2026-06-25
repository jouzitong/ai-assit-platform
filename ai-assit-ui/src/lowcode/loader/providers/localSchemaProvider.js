import { getRenderPageContentByCode } from '../../../api/renderPage'

export function createLocalSchemaProvider() {
  function parseSchemaContent(pageCode, content) {
    if (typeof content !== 'string' || !content.trim()) {
      throw new Error(`pageCode=${pageCode} 的页面内容为空`)
    }

    try {
      return JSON.parse(content)
    } catch (error) {
      throw new Error(`pageCode=${pageCode} 的页面 JSON 解析失败: ${error.message}`)
    }
  }

  return {
    name: 'local-schema-provider',
    async load(pageCode) {
      if (!pageCode) {
        return null
      }

      const response = await getRenderPageContentByCode(pageCode)
      return parseSchemaContent(pageCode, response?.content)
    }
  }
}
