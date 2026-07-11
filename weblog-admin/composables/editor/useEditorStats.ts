/**
 * 编辑器统计信息相关的纯函数和类型
 */

export interface MdStats {
  chars: number
  lines: number
}

export interface HtmlStats {
  chars: number
  paragraphs: number
}

/**
 * 统计文本的非空白字符数和行数
 */
export function countCharsAndLines(text: string): { chars: number; lines: number } {
  let chars = 0
  let lines = 1
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i)
    if (c === 10) lines++
    else if (c !== 32 && c !== 9 && c !== 13 && c !== 11 && c !== 12) chars++
  }
  return { chars, lines }
}

/**
 * 计算 Markdown 源码统计
 */
export function calculateMdStats(markdown: string): MdStats {
  if (!markdown) return { chars: 0, lines: 0 }
  return countCharsAndLines(markdown)
}

/**
 * 计算 HTML 预览统计
 * 使用 DOMParser 替代 innerHTML，避免潜在 XSS 风险
 */
export function calculateHtmlStats(html: string): HtmlStats {
  if (!html) return { chars: 0, paragraphs: 0 }
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  const paragraphs = doc.querySelectorAll(
    'p, h1, h2, h3, h4, h5, h6, li, tr, blockquote'
  ).length
  doc.querySelectorAll('pre, code, svg, style, .mermaid, [data-mermaid]')
    .forEach(el => el.remove())
  const text = doc.body.textContent || ''
  let chars = 0
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i)
    if (c !== 32 && c !== 9 && c !== 10 && c !== 13 && c !== 11 && c !== 12) chars++
  }
  return { chars, paragraphs }
}
