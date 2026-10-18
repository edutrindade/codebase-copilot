import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin, type ProxyOptions } from 'vite';
import { configDefaults } from 'vitest/config';
import { contentSecurityPolicy } from './csp.ts';

// 127.0.0.1, not localhost: Node may resolve localhost to ::1 and the backend listens on IPv4 only (D-072).
const backendUrl = process.env.BACKEND_URL ?? 'http://127.0.0.1:8080';

// changeOrigin rewrites Host for the backend's Host check; Origin is kept so CSRF through the proxy is still refused.
const proxy: Record<string, ProxyOptions> = {
  '/api': { target: backendUrl, changeOrigin: true },
};

// Build only: the dev server injects an inline React Refresh preamble that this policy would block.
function cspMetaTag(): Plugin {
  return {
    name: 'csp-meta-tag',
    apply: 'build',
    transformIndexHtml: () => [
      {
        tag: 'meta',
        attrs: { 'http-equiv': 'Content-Security-Policy', content: contentSecurityPolicy },
        injectTo: 'head-prepend',
      },
    ],
  };
}

// The UI font is needed for the first paint; the hashed name is only known after bundling.
function preloadUiFont(): Plugin {
  return {
    name: 'preload-ui-font',
    apply: 'build',
    transformIndexHtml(_html, context) {
      const font = Object.keys(context.bundle ?? {}).find((file) =>
        /inter-latin-wght-normal-[\w-]+\.woff2$/.test(file),
      );
      if (!font) {
        throw new Error('UI font missing from the bundle');
      }
      return [
        {
          tag: 'link',
          attrs: {
            rel: 'preload',
            href: `/${font}`,
            as: 'font',
            type: 'font/woff2',
            crossorigin: '',
          },
          injectTo: 'head',
        },
      ];
    },
  };
}

export default defineConfig({
  plugins: [react(), cspMetaTag(), preloadUiFont()],
  resolve: {
    alias: {
      // Its <style> element breaks the CSP; the replacement uses a constructed stylesheet.
      'react-style-singleton': fileURLToPath(
        new URL('./src/vendor/styleSingleton.ts', import.meta.url),
      ),
    },
  },
  // The highlight worker loads each language as its own chunk, which IIFE workers cannot do.
  worker: { format: 'es' },
  server: { port: 5173, strictPort: true, proxy },
  preview: { port: 4173, strictPort: true, proxy },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    // Playwright runs these against the real backend (npm run e2e).
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
});
