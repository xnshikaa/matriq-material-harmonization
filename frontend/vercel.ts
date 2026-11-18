const target = process.env.MATRIQ_API_ORIGIN;

if (!target) {
  throw new Error('Set MATRIQ_API_ORIGIN to the hosted MATRIQ Spring Boot HTTPS origin.');
}

const api = new URL(target);
if (api.protocol !== 'https:' || api.username || api.password || api.pathname !== '/' || api.search || api.hash
  || ['localhost', '127.0.0.1', '[::1]'].includes(api.hostname)) {
  throw new Error('MATRIQ_API_ORIGIN must be an external HTTPS origin with no path or credentials.');
}

export const config = {
  framework: 'vite',
  buildCommand: 'npm run build',
  outputDirectory: 'dist',
  rewrites: [
    { source: '/api/:path*', destination: api.origin + '/api/:path*' },
    { source: '/:path((?!api(?:/|$)|assets(?:/|$)).*)', destination: '/index.html' },
  ],
};
