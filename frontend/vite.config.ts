import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Backend Spring Boot berjalan di 8081; semua /api diteruskan agar satu origin (tanpa CORS).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8081' },
  },
  build: { sourcemap: true, chunkSizeWarningLimit: 800 },
});
