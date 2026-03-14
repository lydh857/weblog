import { ref } from 'vue'

const visible = ref(false)
const preferredPosition = ref('home_left')
const preferredStep = ref<1 | 2 | 3>(2)
const preferredPitAdId = ref<number | null>(null)
const applicationVersion = ref(0)

export function useAdApplyModal() {
  function open(position?: string, options?: { step?: 1 | 2 | 3; pitAdId?: number }) {
    if (typeof position === 'string' && position.trim()) {
      preferredPosition.value = position.trim()
    }
    if (options?.step) {
      preferredStep.value = options.step
    } else {
      preferredStep.value = 2
    }

    if (typeof options?.pitAdId === 'number' && Number.isFinite(options.pitAdId) && options.pitAdId > 0) {
      preferredPitAdId.value = options.pitAdId
    } else {
      preferredPitAdId.value = null
    }

    visible.value = true
  }

  function close() {
    visible.value = false
  }

  function notifyApplicationChanged() {
    applicationVersion.value += 1
  }

  return {
    visible,
    preferredPosition,
    preferredStep,
    preferredPitAdId,
    applicationVersion,
    open,
    close,
    notifyApplicationChanged,
  }
}
