import { onBeforeUnmount, onMounted } from 'vue'
import { registerAgentPageCapability } from '../services/pageCapabilityRegistry'
import type { AgentPageCapability } from '../types'

export function useAgentPageCapability(capability: AgentPageCapability) {
  let unregister: (() => void) | undefined

  onMounted(() => {
    unregister = registerAgentPageCapability(capability)
  })

  onBeforeUnmount(() => {
    unregister?.()
  })
}
