export class SchemaLoader {
  constructor(providers = []) {
    this.providers = Array.isArray(providers) ? providers : []
  }

  async load(pageCode) {
    if (!pageCode) {
      throw new Error('缺少 pageCode，无法加载页面 schema')
    }

    for (const provider of this.providers) {
      const schema = await provider.load(pageCode)
      if (schema) {
        return schema
      }
    }

    throw new Error(`未找到 pageCode=${pageCode} 对应的页面 schema`)
  }
}
