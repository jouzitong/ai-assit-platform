import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { aiFlowSectionTabs, getAiFlowSectionMeta } from '../data/ai-flow'

export function useAiFlowPage() {
  const route = useRoute()
  const router = useRouter()

  const activeSection = computed(() => {
    const section = route.query.section
    return typeof section === 'string' && aiFlowSectionTabs.some(item => item.key === section) ? section : 'workflow'
  })

  const sectionMeta = computed(() => getAiFlowSectionMeta(activeSection.value))

  function switchSection(sectionKey) {
    router.replace({
      path: route.path,
      query: {
        ...route.query,
        section: sectionKey
      }
    })
  }

  return {
    sectionTabs: aiFlowSectionTabs,
    activeSection,
    sectionMeta,
    switchSection
  }
}
