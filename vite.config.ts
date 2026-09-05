import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: false,
    hmr: {
      // Fixes Vite HMR connection in proxied/AI Studio environments
      protocol: 'ws',
    }
  },
  build: {
    outDir: 'dist',
    target: 'esnext'
  }
});
