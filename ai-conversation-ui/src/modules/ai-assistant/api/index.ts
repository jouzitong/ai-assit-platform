import type { BrowserAgentModel } from '../types'

/** Browser-direct Agent execution is intentionally disabled; use a server-side SETTINGS_ASSISTANT entry. */
export async function fetchBrowserAgentModels(): Promise<BrowserAgentModel[]> {
  return []
}
