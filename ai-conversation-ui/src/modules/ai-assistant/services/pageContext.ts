import { activeAgentPageCapability } from './pageCapabilityRegistry'
import { captureDomPageSnapshot } from './domPageCapability'
import type { AgentPageContext } from '../types'

export async function captureAgentPageContext(): Promise<AgentPageContext> {
  const capability = activeAgentPageCapability.value
  return {
    capturedAt: new Date().toISOString(),
    page: captureDomPageSnapshot(),
    ...(capability ? { registeredCapability: await capability.getSnapshot() } : {}),
    availablePageActions: capability?.actions || [],
  }
}
