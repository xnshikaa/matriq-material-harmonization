import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const proxy = { '/api': { target: process.env.MATRIQ_API_TARGET ?? 'http://127.0.0.1:8081' } };

export default defineConfig({
  publicDir: false,
  plugins: [react(), tailwindcss()],
  server: { port: 5173, proxy },
  preview: { proxy },
});
