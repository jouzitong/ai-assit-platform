import type {
  AgentDomFormField,
  AgentDomFormSnapshot,
  AgentDomPageSnapshot,
  AgentDomTableSnapshot,
  AgentFormPatchChange,
  AgentFormPatchResult,
  AgentJsonPrimitive,
  AgentJsonValue,
} from '../types'

const ASSISTANT_SELECTOR = '[data-ai-assistant-root], [data-ai-assistant-panel]'
const SENSITIVE_FIELD_PATTERN = /(password|passwd|pwd|secret|token|api.?key|access.?key|密钥|密码|令牌)/i
const MAX_VISIBLE_TEXT_LENGTH = 8_000
const MAX_PAGE_BACKGROUND_TEXT_LENGTH = 2_000
const MAX_FORM_COUNT = 12
const MAX_FIELD_COUNT = 80
const MAX_TABLE_COUNT = 6
const MAX_TABLE_ROWS = 30
const MAX_TABLE_COLUMNS = 20

const fieldIds = new WeakMap<Element, string>()
const formIds = new WeakMap<Element, string>()
const capturedFields = new Map<string, Element>()
let fieldSequence = 0
let formSequence = 0

function nextFieldId(element: Element) {
  const existing = fieldIds.get(element)
  if (existing) return existing
  fieldSequence += 1
  const id = `dom-field-${fieldSequence}`
  fieldIds.set(element, id)
  return id
}

function nextFormId(element: Element) {
  const existing = formIds.get(element)
  if (existing) return existing
  formSequence += 1
  const id = `dom-form-${formSequence}`
  formIds.set(element, id)
  return id
}

function normalizeText(value: string | null | undefined, maxLength = 500) {
  return (value || '').replace(/\s+/g, ' ').trim().slice(0, maxLength)
}

function isAssistantElement(element: Element) {
  return Boolean(element.closest(ASSISTANT_SELECTOR))
}

function isVisible(element: Element) {
  if (isAssistantElement(element)) return false
  const target = element.closest('.el-switch, .el-checkbox, .el-radio') || element
  const style = window.getComputedStyle(target)
  return style.display !== 'none'
    && style.visibility !== 'hidden'
    && style.opacity !== '0'
    && target.getClientRects().length > 0
}

function activeDialogRoot() {
  let activeDialog: HTMLElement | null = null
  let activeZIndex = Number.NEGATIVE_INFINITY

  document.querySelectorAll<HTMLElement>('.el-dialog').forEach((dialog) => {
    if (!isVisible(dialog)) return
    const overlayDialog = dialog.closest<HTMLElement>('.el-overlay-dialog')
    if (overlayDialog?.classList.contains('is-closing')) return
    const overlay = dialog.closest<HTMLElement>('.el-overlay')
    const parsedZIndex = Number.parseInt(window.getComputedStyle(overlay || dialog).zIndex, 10)
    const zIndex = Number.isFinite(parsedZIndex) ? parsedZIndex : 0
    if (zIndex >= activeZIndex) {
      activeDialog = dialog
      activeZIndex = zIndex
    }
  })

  return activeDialog
}

function resolveDialogTitle(dialog: Element) {
  return normalizeText(dialog.querySelector('.el-dialog__title')?.textContent)
    || normalizeText(dialog.closest('.el-overlay-dialog')?.getAttribute('aria-label'))
    || normalizeText(dialog.querySelector('[role="heading"]')?.textContent)
}

function prioritizeActiveDialogElements<T extends Element>(elements: T[], activeDialog: HTMLElement | null) {
  if (!activeDialog) return elements
  return [
    ...elements.filter(element => activeDialog.contains(element)),
    ...elements.filter(element => !activeDialog.contains(element)),
  ]
}

function associatedLabel(element: HTMLElement) {
  const ariaLabel = normalizeText(element.getAttribute('aria-label'))
  if (ariaLabel) return ariaLabel

  const formItemLabel = element.closest('.el-form-item')?.querySelector('.el-form-item__label')
  const formItemText = normalizeText(formItemLabel?.textContent)
  if (formItemText) return formItemText

  const id = element.id
  if (id) {
    const label = [...document.querySelectorAll<HTMLLabelElement>('label[for]')]
      .find(item => item.htmlFor === id)
    const labelText = normalizeText(label?.textContent)
    if (labelText) return labelText
  }

  const wrappingLabel = normalizeText(element.closest('label')?.textContent)
  if (wrappingLabel) return wrappingLabel
  return normalizeText(
    element.getAttribute('placeholder')
      || element.getAttribute('name')
      || element.id
      || '未命名字段',
  )
}

function isSensitiveField(element: HTMLElement) {
  const label = associatedLabel(element)
  const identity = `${label} ${element.getAttribute('name') || ''} ${element.id} ${element.getAttribute('autocomplete') || ''}`
  return element instanceof HTMLInputElement && element.type === 'password'
    || SENSITIVE_FIELD_PATTERN.test(identity)
}

function fieldControl(element: Element) {
  if (element instanceof HTMLTextAreaElement) return 'textarea'
  if (element instanceof HTMLSelectElement) return 'select'
  if (element instanceof HTMLInputElement) return element.type || 'text'
  return 'contenteditable'
}

function fieldValue(element: Element, sensitive: boolean): AgentJsonValue {
  if (sensitive) return '[已隐藏]'
  if (element instanceof HTMLInputElement) {
    if (element.type === 'checkbox') return element.checked
    if (element.type === 'radio') return element.checked ? element.value : ''
    return element.value
  }
  if (element instanceof HTMLTextAreaElement || element instanceof HTMLSelectElement) return element.value
  return normalizeText(element.textContent, 2_000)
}

function isWritable(element: Element) {
  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
    if (element.disabled) return false
    if (element.readOnly && !['checkbox', 'radio'].includes(element.type)) return false
    if (element.closest('.el-select, .el-date-editor, .el-cascader')) return false
    return true
  }
  if (element instanceof HTMLSelectElement) return !element.disabled
  return element instanceof HTMLElement && element.isContentEditable
}

function resolveFormRoot(element: Element) {
  return element.closest('form, .el-form, [role="form"], .el-dialog')
    || document.querySelector('main')
    || document.body
}

function resolveFormTitle(root: Element) {
  const dialog = root.matches('.el-dialog') ? root : root.closest('.el-dialog')
  const dialogTitle = dialog ? resolveDialogTitle(dialog) : ''
  if (dialogTitle) return dialogTitle
  const heading = normalizeText(root.querySelector('h1, h2, h3, legend')?.textContent)
  return heading || '当前页面表单'
}

function captureForms(activeDialog = activeDialogRoot()) {
  capturedFields.clear()
  const groups = new Map<Element, AgentDomFormField[]>()
  const controls = prioritizeActiveDialogElements(
    [...document.querySelectorAll<HTMLElement>('input, textarea, select, [contenteditable="true"]')],
    activeDialog,
  )
    .filter((element) => {
      if (!isVisible(element)) return false
      if (element instanceof HTMLInputElement && element.type === 'hidden') return false
      return true
    })
    .slice(0, MAX_FIELD_COUNT)

  controls.forEach((element) => {
    const root = resolveFormRoot(element)
    const formId = nextFormId(root)
    const fieldId = nextFieldId(element)
    const label = associatedLabel(element)
    const sensitive = isSensitiveField(element)
    const options = element instanceof HTMLSelectElement
      ? [...element.options].slice(0, 50).map(option => normalizeText(option.label || option.text || option.value))
      : undefined

    capturedFields.set(fieldId, element)
    groups.set(root, [...(groups.get(root) || []), {
      fieldId,
      formId,
      label,
      control: fieldControl(element),
      value: fieldValue(element, sensitive),
      writable: isWritable(element) && !sensitive,
      required: element.hasAttribute('required') || element.getAttribute('aria-required') === 'true',
      sensitive,
      ...(options?.length ? { options } : {}),
    }])
  })

  return [...groups.entries()].slice(0, MAX_FORM_COUNT).map(([root, fields]): AgentDomFormSnapshot => ({
    formId: nextFormId(root),
    title: resolveFormTitle(root),
    fields,
  }))
}

function tableFromElementPlus(root: Element): AgentDomTableSnapshot | null {
  const columns = [...root.querySelectorAll('.el-table__header-wrapper th')]
    .slice(0, MAX_TABLE_COLUMNS)
    .map(cell => normalizeText(cell.textContent))
    .filter(Boolean)
  const rows = [...root.querySelectorAll('.el-table__body-wrapper tbody tr')]
    .slice(0, MAX_TABLE_ROWS)
    .map(row => [...row.querySelectorAll('td')]
      .slice(0, MAX_TABLE_COLUMNS)
      .map(cell => normalizeText(cell.textContent)))
  if (!columns.length && !rows.length) return null
  return {
    title: normalizeText(root.getAttribute('aria-label')) || '页面数据表',
    columns,
    rows,
  }
}

function tableFromNative(root: HTMLTableElement): AgentDomTableSnapshot | null {
  const columns = [...root.querySelectorAll('thead th')]
    .slice(0, MAX_TABLE_COLUMNS)
    .map(cell => normalizeText(cell.textContent))
    .filter(Boolean)
  const rows = [...root.querySelectorAll('tbody tr')]
    .slice(0, MAX_TABLE_ROWS)
    .map(row => [...row.querySelectorAll('th, td')]
      .slice(0, MAX_TABLE_COLUMNS)
      .map(cell => normalizeText(cell.textContent)))
  if (!columns.length && !rows.length) return null
  return {
    title: normalizeText(root.caption?.textContent) || normalizeText(root.getAttribute('aria-label')) || '页面数据表',
    columns,
    rows,
  }
}

function captureTables(activeDialog = activeDialogRoot()) {
  const tables: AgentDomTableSnapshot[] = []
  const tableRoots = prioritizeActiveDialogElements(
    [...document.querySelectorAll<Element>('.el-table, table')]
      .filter(root => !(root instanceof HTMLTableElement && root.closest('.el-table'))),
    activeDialog,
  )

  tableRoots.forEach((root) => {
    if (tables.length >= MAX_TABLE_COUNT || !isVisible(root)) return
    const table = root instanceof HTMLTableElement
      ? tableFromNative(root)
      : tableFromElementPlus(root)
    if (table) tables.push(table)
  })
  return tables
}

function captureTextFromRoot(root: Element, maxLength: number, excludedRoot?: Element | null) {
  const chunks: string[] = []
  let length = 0
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      const parent = node.parentElement
      if (
        !parent
        || (excludedRoot && excludedRoot.contains(parent))
        || !isVisible(parent)
        || isAssistantElement(parent)
      ) {
        return NodeFilter.FILTER_REJECT
      }
      if (['SCRIPT', 'STYLE', 'NOSCRIPT'].includes(parent.tagName)) return NodeFilter.FILTER_REJECT
      return normalizeText(node.textContent) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT
    }
  })

  while (length < maxLength) {
    const node = walker.nextNode()
    if (!node) break
    const text = normalizeText(node.textContent)
    if (!text) continue
    chunks.push(text)
    length += text.length + 1
  }

  return chunks.join('\n').slice(0, maxLength)
}

function captureVisibleText(activeDialog = activeDialogRoot()) {
  const pageRoot = document.querySelector('main') || document.body

  if (!activeDialog) return captureTextFromRoot(pageRoot, MAX_VISIBLE_TEXT_LENGTH)

  const dialogText = captureTextFromRoot(
    activeDialog,
    MAX_VISIBLE_TEXT_LENGTH - MAX_PAGE_BACKGROUND_TEXT_LENGTH,
  )
  const backgroundText = captureTextFromRoot(
    pageRoot,
    MAX_PAGE_BACKGROUND_TEXT_LENGTH,
    activeDialog,
  )

  return [
    dialogText ? `【当前弹窗】\n${dialogText}` : '',
    backgroundText ? `【页面背景】\n${backgroundText}` : '',
  ].filter(Boolean).join('\n')
}

export function captureDomPageSnapshot(): AgentDomPageSnapshot {
  const activeDialog = activeDialogRoot()
  const activeDialogTitle = activeDialog ? resolveDialogTitle(activeDialog) : ''
  return {
    route: `${window.location.pathname}${window.location.search}`,
    title: activeDialogTitle || normalizeText(document.querySelector('h1')?.textContent) || document.title || window.location.pathname,
    ...(activeDialog ? { activeDialog: { title: activeDialogTitle || '当前弹窗' } } : {}),
    visibleText: captureVisibleText(activeDialog),
    forms: captureForms(activeDialog),
    tables: captureTables(activeDialog),
  }
}

function setNativeProperty(element: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement, key: 'value' | 'checked', value: unknown) {
  const prototype = Object.getPrototypeOf(element) as object
  const descriptor = Object.getOwnPropertyDescriptor(prototype, key)
  if (descriptor?.set) descriptor.set.call(element, value)
  else Reflect.set(element, key, value)
}

function textValue(value: AgentJsonPrimitive) {
  if (typeof value === 'string') return value
  if (value === null) return ''
  return String(value)
}

function applyValue(element: Element, value: AgentJsonPrimitive) {
  if (!element.isConnected) throw new Error('字段已经离开当前页面')
  if (!isWritable(element)) throw new Error('字段只读或需要通过专用页面动作填写')
  if (element instanceof HTMLElement && isSensitiveField(element)) throw new Error('敏感字段禁止由 AI 自动回填')

  if (element instanceof HTMLInputElement) {
    if (element.type === 'checkbox') {
      const checked = typeof value === 'boolean' ? value : ['true', '1', 'yes', 'on'].includes(textValue(value).toLowerCase())
      setNativeProperty(element, 'checked', checked)
    }
    else if (element.type === 'radio') {
      setNativeProperty(element, 'checked', textValue(value) === element.value)
    }
    else {
      setNativeProperty(element, 'value', textValue(value))
    }
  }
  else if (element instanceof HTMLTextAreaElement) {
    setNativeProperty(element, 'value', textValue(value))
  }
  else if (element instanceof HTMLSelectElement) {
    const requested = textValue(value)
    const option = [...element.options].find(item => item.value === requested || item.label === requested || item.text === requested)
    if (!option) throw new Error(`下拉选项中不存在“${requested}”`)
    setNativeProperty(element, 'value', option.value)
  }
  else if (element instanceof HTMLElement && element.isContentEditable) {
    element.textContent = textValue(value)
  }
  else {
    throw new Error('暂不支持该字段控件')
  }

  element.dispatchEvent(new Event('input', { bubbles: true, composed: true }))
  element.dispatchEvent(new Event('change', { bubbles: true, composed: true }))
}

export function applyDomFormPatch(changes: AgentFormPatchChange[]): AgentFormPatchResult {
  const result: AgentFormPatchResult = { applied: [], rejected: [] }
  if (changes.some(change => !capturedFields.get(change.fieldId)?.isConnected)) captureForms()

  changes.slice(0, 30).forEach((change) => {
    const element = capturedFields.get(change.fieldId)
    if (!element) {
      result.rejected.push({ fieldId: change.fieldId, reason: '当前页面找不到该字段，请重新读取页面后再试' })
      return
    }
    try {
      applyValue(element, change.value)
      result.applied.push({ fieldId: change.fieldId, label: associatedLabel(element as HTMLElement) })
    }
    catch (error) {
      result.rejected.push({
        fieldId: change.fieldId,
        reason: error instanceof Error ? error.message : '字段回填失败',
      })
    }
  })
  return result
}
