import { defineConfig } from 'vite'
import { splitVendorChunkPlugin } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue(), splitVendorChunkPlugin()],
  build: {
    chunkSizeWarningLimit: 800
  },
  server: {
    host: '127.0.0.1',
    port: 17890
  }
})
