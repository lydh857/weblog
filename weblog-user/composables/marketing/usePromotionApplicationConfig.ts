import { promotionApi, type AdApplyPitOption, type AdPriceRuleVO } from '~/api/marketing/promotion'

export function usePromotionApplicationConfig() {
  const applyEnabled = useState<boolean>('promotion-application-enabled', () => false)
  const priceRules = useState<AdPriceRuleVO[]>('promotion-application-price-rules', () => [])
  const pitOptions = useState<AdApplyPitOption[]>('promotion-application-pit-options', () => [])
  const loaded = useState<boolean>('promotion-application-loaded', () => false)
  const loading = ref(false)

  async function loadPromotionApplicationConfig(force = false) {
    if (loading.value) return
    if (loaded.value && !force) return

    loading.value = true
    try {
      const res = await promotionApi.getApplyStatus()
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
    loadPromotionApplicationConfig,
  }
}
