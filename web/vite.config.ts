import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [{ find: /^@\//, replacement: '/src/' }],
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          mui: ['@mui/material', '@emotion/react', '@emotion/styled'],
          charts: ['@mui/x-charts'],
        },
      },
    },
  },
  // 로컬 개발: /api 를 로컬 Portal API(api/ — ./gradlew bootRun, 8082)로 넘긴다. 운영은 같은 출처라 필요 없음
  server: { port: 5174, proxy: { '/api': 'http://localhost:8082' } },
});
