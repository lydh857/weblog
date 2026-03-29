import withNuxt from './.nuxt/eslint.config.mjs'
import tsEslintPlugin from '@typescript-eslint/eslint-plugin'

export default withNuxt({
  plugins: {
    '@typescript-eslint': tsEslintPlugin,
  },
  rules: {
    '@typescript-eslint/no-explicit-any': 'error',
    '@typescript-eslint/no-invalid-void-type': 'off',
    '@typescript-eslint/no-unused-vars': 'warn',
    '@typescript-eslint/unified-signatures': 'off',
    '@typescript-eslint/no-dynamic-delete': 'off',
    'import/first': 'off',
    'no-empty': 'off',
    'no-extra-boolean-cast': 'off',
    'no-unsafe-finally': 'off',
    'vue/multi-word-component-names': 'off',
    'vue/no-v-text-v-html-on-component': 'off',
  },
})
