/**
 * md-editor-v3 的 insert 回调参数类型
 */
export interface InsertParam {
  targetValue: string
  select: boolean
  deviationStart: number
  deviationEnd: number
}

/**
 * 对选中文本应用 Markdown 格式化
 */
export function applyFormat(selectedText: string, action: string): InsertParam {
  switch (action) {
    case 'bold':
      return { targetValue: `**${selectedText}**`, select: true, deviationStart: 2, deviationEnd: -2 }
    case 'italic':
      return { targetValue: `*${selectedText}*`, select: true, deviationStart: 1, deviationEnd: -1 }
    case 'strike':
      return { targetValue: `~~${selectedText}~~`, select: true, deviationStart: 2, deviationEnd: -2 }
    case 'code':
      return { targetValue: `\`${selectedText}\``, select: true, deviationStart: 1, deviationEnd: -1 }
    case 'codeblock':
      return { targetValue: `\n\`\`\`\n${selectedText}\n\`\`\`\n`, select: true, deviationStart: 5, deviationEnd: -5 }
    case 'link':
      return { targetValue: `[${selectedText}](url)`, select: false, deviationStart: 0, deviationEnd: 0 }
    case 'h1':
      return { targetValue: `# ${selectedText}`, select: true, deviationStart: 2, deviationEnd: 0 }
    case 'h2':
      return { targetValue: `## ${selectedText}`, select: true, deviationStart: 3, deviationEnd: 0 }
    case 'h3':
      return { targetValue: `### ${selectedText}`, select: true, deviationStart: 4, deviationEnd: 0 }
    case 'quote':
      return { targetValue: `> ${selectedText}`, select: true, deviationStart: 2, deviationEnd: 0 }
    default:
      return { targetValue: selectedText, select: true, deviationStart: 0, deviationEnd: 0 }
  }
}
