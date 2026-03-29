import { advertisementApi, type AdApplyPitOption, type AdPriceRuleVO } from '~/api/marketing/advertisement'

export function useAdApplyConfig() {
  const applyEnabled = useState<boolean>('ad-apply-enabled', () => false)
  const priceRules = useState<AdPriceRuleVO[]>('ad-apply-price-rules', () => [])
  const pitOptions = useState<AdApplyPitOption[]>('ad-apply-pit-options', () => [])
  const loaded = useState<boolean>('ad-apply-loaded', () => false)
  const loading = ref(false)

  async function loadAdApplyConfig(force = false) {
    if (loading.value) return
    if (loaded.value && !force) return

    loading.value = true
    try {
      const res = await advertisementApi.getApplyStatus()
      applyEnabled.value = Boolean(res.data?.enabled)
      priceRules.value = Array.isArray(res.data?.rules) ? res.data.rules : []
      pitOptions.value = Array.isArray(res.data?.pitOptions) ? res.data.pitOptions : []
      loaded.value = true
    } catch {
      applyEnabled.value = false
      priceRules.value = []
      pitOptions.value = []
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  return {
    applyEnabled,
    priceRules,
    pitOptions,
    loaded,
    loading,
    loadAdApplyConfig,
  }
}
