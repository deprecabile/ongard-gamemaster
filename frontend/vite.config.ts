import react from '@vitejs/plugin-react';
import fs from 'node:fs';
import path from 'node:path';
import { defineConfig } from 'vite';
import sassDts from 'vite-plugin-sass-dts';

const onDemandLangs = fs
  .readdirSync(path.resolve(__dirname, 'public/locales'), { withFileTypes: true })
  .filter((d) => d.isDirectory())
  .map((d) => d.name);

const supportedLngs = ['en', ...onDemandLangs];

export default defineConfig({
  define: {
    __SUPPORTED_LANGS__: JSON.stringify(supportedLngs),
    __GOOGLE_CLIENT_ID__: JSON.stringify(
      '650458536920-7v7m10gdktkah341b3n5kegtgd5obu33.apps.googleusercontent.com',
    ),
  },
  plugins: [react(), sassDts({ enabledMode: ['development', 'production'], esmExport: true })],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/styles/variables" as *;\n@use "@/styles/mixins" as *;\n`,
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
