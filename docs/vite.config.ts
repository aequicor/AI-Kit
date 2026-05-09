import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  base: '/AI-Kit/',
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        flows: resolve(__dirname, 'flows.html'),
        scenarios: resolve(__dirname, 'scenarios.html'),
      },
    },
  },
});
