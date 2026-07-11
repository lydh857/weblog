export function useAdminTableHeight(offset = 162, minHeight = 360) {
  const tableHeight = ref(minHeight)

  function updateTableHeight() {
    if (!import.meta.client) return
    tableHeight.value = Math.max(minHeight, window.innerHeight - offset)
  }

  onMounted(() => {
    updateTableHeight()
    window.addEventListener('resize', updateTableHeight)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', updateTableHeight)
  })

  return tableHeight
}
