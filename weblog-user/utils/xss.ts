import DOMPurify from 'dompurify'

interface DomPurifyLike {
  sanitize: (dirty: string, config?: Record<string, unknown>) => string
}

function resolvePurify(): DomPurifyLike | null {
  const mod = DOMPurify as unknown as {
    sanitize?: DomPurifyLike['sanitize']
    default?: {
      sanitize?: DomPurifyLike['sanitize']
    }
  } & ((win: Window) => DomPurifyLike)

  if (typeof mod?.sanitize === 'function') {
    return { sanitize: mod.sanitize }
  }

  if (typeof mod?.default?.sanitize === 'function') {
    return { sanitize: mod.default.sanitize }
  }

  if (import.meta.client && typeof mod === 'function' && typeof window !== 'undefined') {
    const instance = mod(window)
    if (instance && typeof instance.sanitize === 'function') {
      return instance
    }
  }

  return null
}

function safeSanitize(dirty: string, config?: Record<string, unknown>): string {
  const purifier = resolvePurify()
  if (!purifier) {
    return escapeHtml(dirty)
  }
  return purifier.sanitize(dirty, config)
}

/**
 * XSS 清理 - 用于用户输入的文本内容
 * 移除所有 HTML 标签，只保留纯文本
 */
export function sanitizeText(dirty: string): string {
  return safeSanitize(dirty, { ALLOWED_TAGS: [] })
}

/**
 * XSS 清理 - 用于富文本内容（如文章正文）
 * 保留安全的 HTML 标签，移除危险标签和属性
 */
export function sanitizeHtml(dirty: string): string {
  return safeSanitize(dirty, {
    ALLOWED_TAGS: [
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'p', 'br', 'hr', 'blockquote', 'pre', 'code',
      'ul', 'ol', 'li', 'dl', 'dt', 'dd',
      'a', 'img', 'strong', 'em', 'b', 'i', 'u', 's', 'del', 'ins',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'span', 'div', 'sup', 'sub', 'mark',
      'details', 'summary',
    ],
    ALLOWED_ATTR: [
      'href', 'src', 'alt', 'title', 'class', 'id',
      'target', 'rel', 'width', 'height',
      'colspan', 'rowspan', 'align',
    ],
    // 链接必须是安全协议
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
  })
}

/**
 * 转义 HTML 特殊字符（用于模板插值场景）
 */
export function escapeHtml(str: string): string {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#x27;',
  }
  return str.replace(/[&<>"']/g, (char) => map[char] ?? char)
}
