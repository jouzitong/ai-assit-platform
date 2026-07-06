import MarkdownIt from 'markdown-it'

const markdownRenderer = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true
})

const defaultLinkOpen = markdownRenderer.renderer.rules.link_open || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))

markdownRenderer.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  token.attrSet('target', '_blank')
  token.attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

export function renderMarkdown(content) {
  const source = typeof content === 'string' ? content.trim() : ''
  if (!source) {
    return ''
  }
  return markdownRenderer.render(source)
}
