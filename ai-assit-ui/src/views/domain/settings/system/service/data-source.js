import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dataSources } from '../data/data-source'

export function useDataSourcePage() {
  const router = useRouter()
  const keyword = ref('')
  const selectedSourceKey = ref('ods_trade_mysql')

  const filteredSources = computed(() => {
    const normalized = keyword.value.trim().toLowerCase()
    if (!normalized) {
      return dataSources
    }

    return dataSources.filter(item =>
      [item.name, item.type, item.owner, item.host, item.database].some(value =>
        String(value).toLowerCase().includes(normalized)
      )
    )
  })

  function openSource(key) {
    selectedSourceKey.value = key
    router.push(`/settings/system/data-source/${key}`)
  }

  function statusClass(status) {
    return `is-${status}`
  }

  return {
    keyword,
    selectedSourceKey,
    filteredSources,
    openSource,
    statusClass
  }
}
