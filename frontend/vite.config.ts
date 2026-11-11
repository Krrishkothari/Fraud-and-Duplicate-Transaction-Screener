import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // In development, /api calls go to the Spring Boot backend started by the root npm run dev.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
