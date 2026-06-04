import { ref } from 'vue'
import { sections } from '../data'

export function useSystemPage() {
  const sidebarCollapsed = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return {
    sections,
    sidebarCollapsed,
    toggleSidebar
  }
}
