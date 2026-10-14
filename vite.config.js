import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  base: '/public/ui/',
  build: {
    outDir: 'target/classes/META-INF/resources/public/ui',
    emptyOutDir: true,
    rollupOptions: {
      input: 'src/main/frontend/main.js',
      output: {
        entryFileNames: 'app.js',
        chunkFileNames: '[name]-[hash].js',
        assetFileNames: '[name]-[hash][extname]'
      }
    }
  },
  test: {
    environment: 'jsdom',
    include: ['src/test/frontend/**/*.test.js'],
    clearMocks: true,
    restoreMocks: true
  }
});
