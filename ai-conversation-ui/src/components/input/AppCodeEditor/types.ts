export type AppCodeEditorFormat =
  | 'json'
  | 'python'
  | 'javascript'
  | 'markdown'
  | 'asciidoc'
  | 'text'

export interface AppCodeEditorFormatOption {
  label: string
  value: AppCodeEditorFormat
}
