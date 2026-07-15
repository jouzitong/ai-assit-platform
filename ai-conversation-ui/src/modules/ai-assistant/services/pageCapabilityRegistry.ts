import { computed, shallowRef } from 'vue'
import type {
  AgentJsonPrimitive,
  AgentPageActionResult,
  AgentPageCapability,
} from '../types'

const capabilityStack = shallowRef<AgentPageCapability[]>([])

export const activeAgentPageCapability = computed<AgentPageCapability | null>(() => (
  capabilityStack.value[capabilityStack.value.length - 1] ?? null
))

export function registerAgentPageCapability(capability: AgentPageCapability) {
  capabilityStack.value = [...capabilityStack.value.filter(item => item !== capability), capability]

  return () => {
    capabilityStack.value = capabilityStack.value.filter(item => item !== capability)
  }
}

export async function executeRegisteredPageAction(
  action: string,
  payload: Record<string, AgentJsonPrimitive>,
): Promise<AgentPageActionResult> {
  const capability = activeAgentPageCapability.value
  if (!capability?.executeAction) {
    return { success: false, message: '当前页面没有提供可执行的画布或页面动作。' }
  }
  if (!capability.actions?.some(item => item.name === action)) {
    return { success: false, message: `当前页面不支持动作 ${action}。` }
  }
  return capability.executeAction(action, payload)
}
