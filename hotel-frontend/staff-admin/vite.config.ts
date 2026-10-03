import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure(proxy) {
          // The browser talks same-origin to Vite. Do not forward the browser
          // Origin as if it were a direct cross-origin call to Spring Boot.
          proxy.on('proxyReq', (proxyRequest) => proxyRequest.removeHeader('origin'))
        },
      },
    },
  },
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] })
  ],
})
