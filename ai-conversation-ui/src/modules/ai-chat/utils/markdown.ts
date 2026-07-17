import DOMPurify, { type Config } from 'dompurify'
import { marked, Renderer, type Tokens } from 'marked'

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

const renderer = new Renderer()

renderer.html = ({ text }) => escapeHtml(text)
renderer.link = function ({ href, title, tokens }: Tokens.Link) {
  const label = this.parser.parseInline(tokens)
  const titleAttribute = title ? ` title="${escapeHtml(title)}"` : ''
  return `<a href="${escapeHtml(href)}"${titleAttribute} target="_blank" rel="noopener noreferrer">${label}</a>`
}

const MARKDOWN_OPTIONS = {
  async: false,
  breaks: true,
  gfm: true,
  renderer,
} as const

const SANITIZE_OPTIONS: Config = {
  ALLOWED_TAGS: [
    'a',
    'blockquote',
    'br',
    'code',
    'del',
    'em',
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    'hr',
    'input',
    'li',
    'ol',
    'p',
    'pre',
    'strong',
    'table',
    'tbody',
    'td',
    'th',
    'thead',
    'tr',
    'ul',
  ],
  ALLOWED_ATTR: ['align', 'checked', 'class', 'disabled', 'href', 'rel', 'start', 'target', 'title', 'type'],
  ALLOW_ARIA_ATTR: false,
  ALLOW_DATA_ATTR: false,
  ALLOW_UNKNOWN_PROTOCOLS: false,
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i,
}

export function renderMarkdown(markdown: string) {
  if (!markdown?.trim()) {
    return ''
  }

  const source = markdown.replace(/^[\u200B-\u200F\uFEFF]+/, '')
  const rendered = marked.parse(source, MARKDOWN_OPTIONS)
  return DOMPurify.sanitize(rendered, SANITIZE_OPTIONS)
}
