import { ElMessage } from 'element-plus'

/** AI 错误码与提示信息映射 */
const AI_ERROR_MAP: Record<number, string> = {
  50001: 'AI 功能已关闭，请在配置中开启',
  50002: '该 AI 功能已关闭',
  50003: '本月 AI 用量已达上限',
  50004: 'AI 响应超时，请稍后重试',
  50005: 'AI 服务暂时不可用，请稍后重试',
  50006: '文章内容过短，请先完善内容',
  50007: '今日问答次数已用完',
  50008: '单次对话轮次已达上限',
}

/**
 * 处理 AI 相关错误，显示对应提示
 * @returns true 表示已处理，false 表示非 AI 错误
 */
export function handleAiError(error: unknown): boolean {
  const err = error as { code?: number; message?: string }
  const code = err?.code
  if (code && AI_ERROR_MAP[code]) {
    const message = err?.message?.trim()
    ElMessage.warning(message && message !== AI_ERROR_MAP[code] ? message : AI_ERROR_MAP[code])
    return true
  }
  // 非 AI 特定错误，显示通用提示
  ElMessage.error(err?.message || 'AI 请求失败')
  return false
}
