export type AppCodeEditorFormat =
  | 'json'
  | 'python'
  | 'javascript'
  | 'markdown'
  | 'asciidoc'
  | 'text'

export type AppCodeEditorMarkdownMode = 'edit' | 'split' | 'preview'

export type AppCodeEditorStatus = {
  checking: boolean
  diagnostics: number
}

export interface AppCodeEditorFormatOption {
  label: string
  value: AppCodeEditorFormat
}
