import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/': {
        target: backendUrl,
        changeOrigin: true,
        bypass(req: { url?: string }) {
          if (req.url?.startsWith('/src') || req.url?.startsWith('/@') || req.url?.startsWith('/node_modules')) {
            return req.url
          }
        },
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    rolldownOptions: {
      input: 'src/app.ts',
      output: {
        entryFileNames: 'assets/app.js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name][extname]',
      },
    },
  },
})
