import { spawn, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('..', import.meta.url));
const frontend = fileURLToPath(new URL('../frontend/', import.meta.url));
const vite = fileURLToPath(new URL('../frontend/node_modules/vite/bin/vite.js', import.meta.url));
const backend = 'http://127.0.0.1:8081';
const app = 'http://127.0.0.1:5173';
const children = [];

function start(command, args, cwd, env = process.env) {
  const child = spawn(command, args, { cwd, env, stdio: 'inherit', windowsHide: true });
  const process = { child, failed: false };
  child.once('error', error => {
    process.failed = true;
    console.error(error.message);
  });
  children.push(process);
  return process;
}

function stop() {
  for (const { child } of children.slice().reverse()) {
    if (child.pid && child.exitCode === null) {
      spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true });
    }
  }
}
process.once('SIGINT', () => { stop(); process.exit(0); });
process.once('SIGTERM', () => { stop(); process.exit(0); });

async function ready(origin) {
  try {
    const response = await fetch(origin + '/api/auth/session', { signal: AbortSignal.timeout(2000) });
    if (!response.ok) return false;
    const session = await response.json();
    return typeof session.csrfToken === 'string' && session.csrfToken.length >= 32;
  } catch {
    return false;
  }
}

async function waitFor(origin, running, label) {
  const deadline = performance.now() + 120_000;
  while (performance.now() < deadline) {
    if (running.failed || running.child.exitCode !== null) throw new Error(label + ' stopped. See the console output above.');
    if (await ready(origin)) return;
    await delay(550);
  }
  throw new Error(label + ' did not become ready within two minutes.');
}

try {
  if (!existsSync(vite)) throw new Error('Run npm.cmd --prefix frontend ci once to install frontend dependencies.');
  if (!await ready(backend)) {
    console.log('Starting the MATRIQ backend...');
    const running = start('cmd.exe', ['/d', '/c', 'run-backend.bat'], root, { ...process.env, MATRIQ_SECURE_COOKIE: 'false' });
    await waitFor(backend, running, 'The MATRIQ backend');
  }
  if (!await ready(app)) {
    console.log('Starting the MATRIQ interface...');
    const running = start(process.execPath, [vite, '--host', '127.0.0.1', '--strictPort'], frontend);
    await waitFor(app, running, 'The MATRIQ interface');
  }
  console.log('MATRIQ is ready at ' + app);
  if (!process.argv.includes('--no-browser')) {
    const browser = spawn('rundll32.exe', ['url.dll,FileProtocolHandler', app], { detached: true, stdio: 'ignore', windowsHide: true });
    browser.on('error', () => console.log('Open ' + app + ' in your browser.'));
    browser.unref();
  }
  if (children.length) {
    await Promise.race(children.map(({ child }) => new Promise(resolve => child.once('exit', resolve))));
    throw new Error('A MATRIQ process stopped. See the console output above.');
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  stop();
}
