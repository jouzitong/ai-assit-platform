import { buildUrl, request } from '../utils/request'

const AI_KB_API_PREFIX = '/aiEngine/internal/v1/ai/kb'

export function listKnowledgeDocuments(payload) {
  return request(`${AI_KB_API_PREFIX}/document/list`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function getKnowledgeDocumentDetail({ kbCode, documentCode }) {
  const query = new URLSearchParams({
    kbCode: String(kbCode ?? ''),
    documentCode: String(documentCode ?? '')
  })
  return request(buildUrl(`${AI_KB_API_PREFIX}/document/detail?${query.toString()}`), {
    method: 'GET'
  })
}
