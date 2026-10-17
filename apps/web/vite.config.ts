/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin, type ProxyOptions } from 'vite';
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

export default defineConfig({
  plugins: [react(), cspMetaTag()],
  server: { port: 5173, strictPort: true, proxy },
  preview: { port: 4173, strictPort: true, proxy },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
  },
});
