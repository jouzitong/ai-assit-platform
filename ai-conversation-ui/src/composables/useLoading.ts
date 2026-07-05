import { computed, ref } from 'vue'

export function useLoading(initialValue = false) {
  const loading = ref(initialValue)

  const isIdle = computed(() => !loading.value)
  const startLoading = () => {
    loading.value = true
  }
  const stopLoading = () => {
    loading.value = false
  }

  return {
    loading,
    isIdle,
    startLoading,
    stopLoading,
  }
}
