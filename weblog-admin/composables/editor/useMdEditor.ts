import xss from 'xss'

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

interface XssPluginOptions {
  extendedWhiteList?: Record<string, string[]>
}

function createMdEditorXssPlugin(markdown: MarkdownItLike, options: XssPluginOptions): void {
  const extendedWhiteList = options.extendedWhiteList ?? {}
  const defaultWhiteList = xss.getDefaultWhiteList()
  const builtInWhiteList: Record<string, string[]> = {
    img: ['class'],
    input: ['class', 'disabled', 'type', 'checked'],
    iframe: [
      'class',
      'width',
      'height',
      'src',
      'title',
      'border',
      'frameborder',
      'framespacing',
      'allow',
      'allowfullscreen',
    ],
  }

  const mergedWhiteList: Record<string, string[]> = { ...defaultWhiteList }
  for (const key of new Set([...Object.keys(builtInWhiteList), ...Object.keys(extendedWhiteList)])) {
    const baseAttrs = mergedWhiteList[key] ?? []
    const builtInAttrs = builtInWhiteList[key] ?? []
    const customAttrs = extendedWhiteList[key] ?? []
    mergedWhiteList[key] = Array.from(new Set([...baseAttrs, ...builtInAttrs, ...customAttrs]))
  }

  const sanitizer = new xss.FilterXSS({ whiteList: mergedWhiteList })
  markdown.core.ruler.after('linkify', 'xss', (state) => {
    for (const token of state.tokens) {
      if (token.type === 'html_block') {
        token.content = sanitizer.process(token.content)
      }

      if (token.type === 'inline' && token.children) {
        token.children.forEach((childToken) => {
          if (childToken.type === 'html_inline') {
            childToken.content = sanitizer.process(childToken.content)
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
        markdownItPlugins(plugins) {
          return [
            ...plugins,
            {
              type: 'xss',
              plugin: createMdEditorXssPlugin,
              options: {
                extendedWhiteList: {
                  img: ['style', 'class', 'alt', 'src', 'width', 'height'],
                },
              },
            },
          ]
        },
      })
    })
  }

  return mdEditorConfigPromise
}
