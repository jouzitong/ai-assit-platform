import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { CatalogItem, ValidationReport } from '../types'

export function useDefinitionEditor<T extends CatalogItem>(options: {
  routeBase: string
  label: string
  get: (code: string) => Promise<T>
  create: (payload: T) => Promise<T>
  update: (code: string, payload: T) => Promise<T>
  validate: (code: string, version: number) => Promise<ValidationReport>
  publish: (code: string, version: number) => Promise<unknown>
  getPayload: () => T | null
  apply: (value: T) => void
  reset: () => void
}) {
  const route = useRoute()
  const router = useRouter()
  const routeCode = computed(() => typeof route.params.sourceKey === 'string' ? route.params.sourceKey : '')
  const isCreate = computed(() => !routeCode.value || routeCode.value === 'new')
  const loading = ref(false)
  const saving = ref(false)
  const validating = ref(false)
  const publishing = ref(false)
  const report = ref<ValidationReport | null>(null)
  const saved = ref<T | null>(null)

  const currentCode = computed(() => saved.value?.code || (!isCreate.value ? routeCode.value : ''))
  const currentVersion = computed(() => Number(
    saved.value?.draftVersion || saved.value?.version || saved.value?.currentPublishedVersion || 1,
  ))
  const status = computed(() => saved.value?.status || 'DRAFT')

  async function load() {
    report.value = null
    if (isCreate.value) {
      options.reset()
      saved.value = null
      return
    }
    loading.value = true
    try {
      const value = await options.get(routeCode.value)
      saved.value = value
      options.apply(value)
    }
    catch (error) {
      ElMessage.error(error instanceof Error ? error.message : `${options.label}加载失败`)
    }
    finally {
      loading.value = false
    }
  }

  async function save() {
    const payload = options.getPayload()
    if (!payload) return null
    saving.value = true
    report.value = null
    try {
      const value = isCreate.value
        ? await options.create(payload)
        : await options.update(routeCode.value, payload)
      saved.value = value ? { ...payload, ...value } : payload
      options.apply(saved.value)
      ElMessage.success('草稿已保存')
      if (isCreate.value) {
        await router.replace(`${options.routeBase}/${encodeURIComponent(saved.value.code)}`)
      }
      return saved.value
    }
    catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '保存失败')
      return null
    }
    finally {
      saving.value = false
    }
  }

  async function validate() {
    const definition = await save()
    if (!definition) return null
    validating.value = true
    try {
      report.value = await options.validate(definition.code, currentVersion.value)
      if (report.value.valid === false) {
        ElMessage.error(report.value.message || report.value.issues?.[0]?.message || '校验未通过')
      }
      else {
        ElMessage.success('校验通过')
      }
      return report.value
    }
    catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '校验失败')
      return null
    }
    finally {
      validating.value = false
    }
  }

  async function publish() {
    const validation = await validate()
    if (!validation || validation.valid === false) return
    const definition = saved.value
    if (!definition) return
    try {
      await ElMessageBox.confirm(
        `确认发布${options.label}“${definition.name || definition.code}”版本 ${currentVersion.value}？`,
        `发布${options.label}`,
        { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' },
      )
      publishing.value = true
      await options.publish(definition.code, currentVersion.value)
      ElMessage.success('发布成功')
      await load()
    }
    catch (error) {
      if (error === 'cancel' || error === 'close') return
      ElMessage.error(error instanceof Error ? error.message : '发布失败')
    }
    finally {
      publishing.value = false
    }
  }

  const back = () => router.push(options.routeBase)

  return {
    isCreate,
    loading,
    saving,
    validating,
    publishing,
    report,
    saved,
    currentCode,
    currentVersion,
    status,
    load,
    save,
    validate,
    publish,
    back,
  }
}
