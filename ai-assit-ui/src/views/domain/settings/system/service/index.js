import { ref } from 'vue'
import { sections } from '../data'

export function useSystemPage() {
  const activeSection = ref('overview')
  const sidebarCollapsed = ref(false)

  function switchSection(key) {
    activeSection.value = key
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return {
    sections,
    activeSection,
    sidebarCollapsed,
    switchSection,
    toggleSidebar
  }
}
