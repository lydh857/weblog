import { sanitizeHtml } from '~/utils/security/xss'

let mdEditorConfigPromise: Promise<void> | null = null

interface MarkdownToken {
  type: string
  content: string
  children?: MarkdownToken[]
}

interface MarkdownState {
  tokens: MarkdownToken[]
}

interface MarkdownCoreRuler {
  after(anchorName: string, ruleName: string, handler: (state: MarkdownState) => void): void
}

interface MarkdownCore {
  ruler: MarkdownCoreRuler
}

interface MarkdownItLike {
  core: MarkdownCore
}

function createMdEditorXssPlugin(markdown: MarkdownItLike): void {
  markdown.core.ruler.after('linkify', 'xss', (state) => {
    for (const token of state.tokens) {
      if (token.type === 'html_block') {
        token.content = sanitizeHtml(token.content)
      }

      if (token.type === 'inline' && token.children) {
        token.children.forEach((childToken) => {
          if (childToken.type === 'html_inline') {
            childToken.content = sanitizeHtml(childToken.content)
          }
        })
      }
    }
  })
}

export function ensureMdEditorConfigured(): Promise<void> {
  if (!mdEditorConfigPromise) {
    mdEditorConfigPromise = import('md-editor-v3/lib/es/config.mjs').then(({ config }) => {
      config({
        editorExtensions: {
          highlight: { instance: {} },
          katex: { instance: {} },
        },
        markdownItPlugins(plugins) {
          return [
            ...plugins,
            {
              type: 'xss',
              plugin: createMdEditorXssPlugin,
            },
          ]
        },
      })
    })
  }

  return mdEditorConfigPromise
}
