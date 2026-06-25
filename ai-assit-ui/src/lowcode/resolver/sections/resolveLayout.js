export function inferLayout(component, layout) {
  if (layout && typeof layout === 'object' && layout.type) {
    return {
      type: layout.type,
      variant: layout.variant || '',
      title: layout.title || '',
      description: layout.description || '',
      meta: layout.meta || ''
    }
  }

  const source = String(component || '')
  if (source.includes('tree-list')) {
    return { type: 'list', variant: 'tree-list', title: '', description: '', meta: '' }
  }
  if (source.includes('list')) {
    return { type: 'list', variant: '', title: '', description: '', meta: '' }
  }
  if (source.includes('form')) {
    return { type: 'form', variant: '', title: '', description: '', meta: '' }
  }
  if (source.includes('report')) {
    return { type: 'report', variant: '', title: '', description: '', meta: '' }
  }
  return { type: 'info', variant: '', title: '', description: '', meta: '' }
}

export function resolveLayout(rawSchema = {}, pageCode = '') {
  const layout = inferLayout(rawSchema.component, rawSchema.layout)
  if (!layout.title) {
    layout.title = rawSchema.title || rawSchema.name || pageCode
  }
  if (!layout.description) {
    layout.description = rawSchema.description || ''
  }
  return layout
}
