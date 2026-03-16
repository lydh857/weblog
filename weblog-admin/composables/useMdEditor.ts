let mdEditorConfigPromise: Promise<void> | null = null

export function ensureMdEditorConfigured(): Promise<void> {
  if (!mdEditorConfigPromise) {
    mdEditorConfigPromise = import('md-editor-v3').then(({ config, XSSPlugin }) => {
      config({
        markdownItPlugins(plugins) {
          return [
            ...plugins,
            {
              type: 'xss',
              plugin: XSSPlugin,
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
