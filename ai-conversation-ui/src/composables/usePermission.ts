import { computed, ref } from 'vue'

const grantedPermissions = ref<string[]>([])

export function usePermission() {
  const hasPermission = (permission: string) => grantedPermissions.value.includes(permission)

  return {
    permissions: computed(() => grantedPermissions.value),
    hasPermission,
  }
}
