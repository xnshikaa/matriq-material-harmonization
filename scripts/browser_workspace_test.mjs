import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { join, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const base = process.env.MATRIQ_BROWSER_URL || 'http://127.0.0.1:5173';
const edge = process.env.MATRIQ_EDGE || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const profile = await mkdtemp(resolve('scratch', 'edge-'));
const browser = spawn(edge, [
  '--headless=new', '--remote-debugging-port=0', '--user-data-dir=' + profile,
  '--no-first-run', '--no-default-browser-check', '--disable-background-networking',
  '--enable-unsafe-swiftshader', '--use-angle=swiftshader', '--window-size=1440,960', 'about:blank',
], { windowsHide: true, stdio: 'ignore' });
let socket;
let sequence = 0;
const callbacks = new Map();

async function waitFor(test, label, limit = 20_000) {
  const end = performance.now() + limit;
  while (performance.now() < end) {
    const result = await test();
    if (result) return result;
    await delay(125);
  }
  throw new Error('Timed out: ' + label);
}

function send(method, params = {}) {
  const id = ++sequence;
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      callbacks.delete(id);
      reject(new Error('Browser command timed out: ' + method));
    }, 75_000);
    callbacks.set(id, { resolve, reject, timeout });
    socket.send(JSON.stringify({ id, method, params }));
  });
}

async function evaluate(fn, ...args) {
  const expression = '(' + fn.toString() + ')(...' + JSON.stringify(args) + ')';
  const response = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (response.exceptionDetails) {
    if (response.exceptionDetails.text === 'Execution context was destroyed.') return undefined;
    throw new Error(response.exceptionDetails.exception?.description ?? response.exceptionDetails.text);
  }
  return response.result.value;
}

function text(value) {
  return waitFor(() => evaluate(needle => document.body?.innerText.includes(needle), value), value);
}

function click(label, within = 'body') {
  return waitFor(() => evaluate((needle, section) => {
    const container = document.querySelector(section);
    const button = [...(container?.querySelectorAll('button,summary') ?? [])].find(node =>
      !node.disabled && (node.textContent.trim().replace(/\s+/g, ' ') === needle || node.getAttribute('aria-label') === needle));
    if (!button || !button.getClientRects().length) return false;
    button.click();
    return true;
  }, label, within), 'button ' + label);
}

async function fill(selector, value) {
  await evaluate(query => {
    const field = document.querySelector(query);
    if (!field) throw new Error('Missing input: ' + query);
    field.focus();
    field.select();
  }, selector);
  await send('Input.insertText', { text: value });
}

async function changeDocument(method, params = {}) {
  const previous = await evaluate(() => performance.timeOrigin);
  const result = await send(method, params);
  if (result.errorText) throw new Error(result.errorText);
  await waitFor(() => evaluate(old => performance.timeOrigin !== old && document.readyState !== 'loading', previous),
    'new browser document', 60_000);
}

function open(path) {
  return changeDocument('Page.navigate', { url: base + path });
}

function reload() {
  return changeDocument('Page.reload');
}

async function selectCsv(path) {
  const { root } = await send('DOM.getDocument');
  const { nodeId } = await send('DOM.querySelector', { nodeId: root.nodeId, selector: 'input[type=file]' });
  assert.ok(nodeId);
  await send('DOM.setFileInputFiles', { nodeId, files: [resolve(path)] });
  await text('Map Catalogue Columns');
}

async function uploadBrowser(filename) {
  await selectCsv(join('data/catalogues', filename));
  await click('Validate Ingestion Records');
  await text('Validation Results');
  await click('Preview Import Records');
  await click('Confirm Catalogue Import');
}

async function workspaceRequest(path = '', method = 'GET', payload = null) {
  return evaluate(async (path, method, payload) => {
    const csrf = method !== 'GET' ? (await (await fetch('/api/auth/session')).json()).csrfToken : '';
    const headers = csrf ? { 'X-CSRF-Token': csrf } : {};
    let body;
    if (payload?.file) {
      body = new FormData();
      body.append('file', new Blob([payload.csv], { type: 'text/csv' }), payload.file);
      body.append('options', new Blob([JSON.stringify(payload.options)], { type: 'application/json' }));
    } else if (payload) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(payload);
    }
    const response = await fetch('/api/workspace' + path, { method, headers, body });
    return { status: response.status, data: await response.json() };
  }, path, method, payload);
}

try {
  const { port } = await waitFor(async () => {
    const file = await readFile(join(profile, 'DevToolsActivePort'), 'utf8').catch(() => '');
    return file ? { port: file.split(/\r?\n/)[0] } : null;
  }, 'Edge DevTools port');
  const tabs = await (await fetch('http://127.0.0.1:' + port + '/json/list')).json();
  socket = new WebSocket(tabs.find(tab => tab.type === 'page').webSocketDebuggerUrl);
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.method === 'Runtime.exceptionThrown') {
      console.error('Uncaught browser error:', message.params.exceptionDetails.exception?.description);
    }
    const callback = callbacks.get(message.id);
    if (!callback) return;
    callbacks.delete(message.id);
    clearTimeout(callback.timeout);
    if (message.error) callback.reject(new Error(message.error.message));
    else callback.resolve(message.result);
  });
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Network.enable');
  await open('/');
  await click('Skip intro');
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  await text('Sign in to MATRIQ');
  assert.ok(await evaluate(() => {
    const stage = document.querySelector('.auth-stage').getBoundingClientRect();
    const login = document.querySelector('.auth-content').getBoundingClientRect();
    const scene = document.querySelector('.auth-hero').getBoundingClientRect();
    return Math.abs(stage.left - (innerWidth - stage.right)) < 2 && login.left < scene.left;
  }), 'The login card is centered with the form on the left.');
  assert.equal((await workspaceRequest()).status, 401);
  assert.equal(await evaluate(() => Boolean(document.querySelector('nav[aria-label="Workspace"]'))), false);
  await waitFor(() => evaluate(() => Boolean(document.querySelector('.material-scene canvas.is-live'))), 'active WebGL scene', 30_000);
  await writeFile('scratch/login-desktop.png', Buffer.from((await send('Page.captureScreenshot')).data, 'base64'));
  console.log('Intro → authentication, WebGL scene and protected API verified.');

  const email = 'browser-' + randomUUID() + '@matriq.invalid';
  const secret = 'browser-test-only-password';
  await click('Create Account');
  await text('Create your account');
  assert.ok(await evaluate(() => document.querySelector('.auth-stage').dataset.mode === 'signup'
    && document.querySelector('.auth-hero').getBoundingClientRect().left
      < document.querySelector('.auth-content').getBoundingClientRect().left), 'The scene and form switch sides for registration.');
  await fill('input[name=name]', 'Browser Test Analyst');
  await fill('input[name=email]', email);
  await fill('input[name=password]', secret);
  await fill('input[name=confirmation]', secret);
  await click('Show password');
  assert.equal(await evaluate(() => document.querySelector('#auth-password').type), 'text');
  await click('Hide password');
  await click('Create Account', 'form');
  await text('Material Intelligence Command Center');
  await text('Browser Test Analyst');
  const cookies = (await send('Network.getCookies', { urls: [base] })).cookies;
  assert.ok(cookies.some(cookie => cookie.name === 'MATRIQ_SESSION' && cookie.httpOnly && cookie.sameSite === 'Lax'));

  await click('Material Catalogues', 'nav');
  const existing = (await workspaceRequest()).data.records.length;
  if (existing === 0) {
    await click('Upload New Catalogue');
    await click('Continue to Metadata');
    await click('Continue to File Upload');
    await writeFile('scratch/browser-mixed-organisations.csv', 'ItemCode,Description,Source Organisation\r\nX-1,Pipe,IOCL\r\nX-2,Valve,NTPC\r\n');
    await selectCsv('scratch/browser-mixed-organisations.csv');
    await click('Validate Ingestion Records');
    await text('Split the CSV into one file per organisation');
    assert.equal((await workspaceRequest()).data.records.length, 0);
    await click('Back to Upload');
    const iocl = '01_IOCL_Procurement_Catalogue.csv';
    await uploadBrowser(iocl);
    await text('42 accepted of 42 submitted rows.');
    await click('Return to Batches & Ingestion');
  
    const mapping = { itemCode: 'ItemCode', description: 'Description', category: 'MaterialCategory',
      grade: 'MaterialGrade', dimensions: 'Dimensions', unit: 'Unit', specification: 'StandardSpecification', materialType: '' };
    for (const [file, org, count] of [
      ['02_NTPC_Engineering_Materials.csv', 'NTPC', 95],
      ['03_OIL_Industrial_Materials_A.csv', 'OIL', 150],
      ['04_OIL_Industrial_Materials_B.csv', 'OIL', 150],
    ]) {
      const payload = { file, csv: await readFile(join('data/catalogues', file), 'utf8'),
        options: { organization: org, name: file, mapping } };
      const response = await workspaceRequest('/catalogues', 'POST', payload);
      assert.equal(response.status, 200, JSON.stringify(response.data));
      assert.equal(response.data.validRows, count);
      assert.equal((await workspaceRequest('/catalogues', 'POST', payload)).status, 409);
    }
  } else {
    assert.equal(existing, 437, 'Use an empty isolated workspace for import testing.');
  }
  await reload();
  await text('4 batches');
  await click('Harmonize Materials');
  await text('Harmonization Workspace');
  const { data } = await workspaceRequest();
  assert.equal(data.records.length, 437);
  assert.equal(data.batches.length, 4);
  assert.equal(data.registry.length, 0);
  const activePair = data.proposals.find(item => item.status === 'POSSIBLE');
  assert.ok(activePair);
  assert.equal((await workspaceRequest('/reviews/' + activePair.id, 'POST',
    { action: 'APPROVE', rationale: 'Attempt from a standard account during browser verification.' })).status, 403);

  await click('Dashboard', 'nav');
  await text('437');
  await text('Recent System Activity');
  await text('Harmonization completed');
  const labels = await evaluate(() => [...document.querySelectorAll('nav[aria-label=Workspace] button')]
    .map(button => button.getAttribute('aria-label')));
  assert.deepEqual(labels, ['Dashboard', 'Organisations', 'Material Catalogues', 'Harmonization', 'Review Queue', 'Unified Registry']);
  assert.equal(await evaluate(() => /Held-Out Demo|Live Operational Log|Local Workspace|Algorithm Demonstration/i.test(document.body.innerText)), false);
  await evaluate(() => {
    const select = document.querySelector('select[aria-label="Organisation scope"]');
    select.value = 'NTPC';
    select.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await waitFor(() => evaluate(() => [...document.querySelectorAll('main button')].some(
    button => button.textContent.includes('Material Records') && button.querySelector('strong')?.innerText === '95')), 'NTPC accepted count 95');
  const expected = data.proposals.filter(item => [item.recordA, item.recordB].some(id =>
    data.records[id - 1].material.cpse === 'NTPC')).length;
  await click('Review Queue', 'nav');
  await text('Pending Reviews (' + expected + ')');
  await text('An authorized reviewer can record a decision.');
  await click('Unified Registry', 'nav');
  await text('MATRIQ Unified Material Registry');
  await text('No verified material identities');
  console.log(existing === 0 ? 'Browser CSV wizard, four imports, duplicates, harmonization, scope, role and registry verified.' : 'Existing browser imports, harmonization, scope, role and registry verified.');

  await click('Browser Test Analyst');
  await click('Sign Out');
  await text('Sign in to MATRIQ');
  assert.equal((await workspaceRequest()).status, 401);
  await send('Network.setBlockedURLs', { urls: ['*/api/auth/session'] });
  await open('/review-queue');
  await text('Sign in to MATRIQ');
  assert.ok(await evaluate(() => Boolean(document.querySelector('input[name=email]'))));
  await send('Network.setBlockedURLs', { urls: [] });
  await fill('input[name=email]', email);
  await fill('input[name=password]', 'wrong-password');
  await click('Sign In', 'form');
  await text('Email or password is incorrect.');
  await fill('input[name=password]', secret);
  await click('Sign In', 'form');
  await text('Review Queue');
  await reload();
  await text('Browser Test Analyst');
  await text('Pending Reviews');
  console.log('Sign-out, incorrect credentials, intended deep link and authenticated refresh verified.');

  await click('Browser Test Analyst');
  await click('Sign Out');
  await text('Sign in to MATRIQ');
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `
    const originalContext = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function(type, ...args) {
      return type.startsWith('webgl') ? null : originalContext.call(this, type, ...args);
    };
  ` });
  await reload();
  await text('Sign in to MATRIQ');
  assert.deepEqual(await evaluate(() => ({
    fallback: Boolean(document.querySelector('.material-fallback:not([hidden])')),
    animation: getComputedStyle(document.querySelector('.auth-card')).animationName,
    fits: document.documentElement.scrollWidth === innerWidth,
  })), { fallback: true, animation: 'none', fits: true });
  await writeFile('scratch/login-mobile-fallback.png', Buffer.from((await send('Page.captureScreenshot')).data, 'base64'));
  await fill('input[name=email]', email);
  await fill('input[name=password]', secret);
  await click('Sign In', 'form');
  await text('Browser Test Analyst');
  console.log('Mobile sign-in, WebGL fallback and reduced motion verified.');
  await click('Dashboard', 'nav');
  await text('Material Intelligence Command Center');
  assert.ok(await evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'The mobile dashboard fits the viewport.');
  const reviewerEmail = process.env.MATRIQ_BROWSER_REVIEWER_EMAIL;
  if (reviewerEmail) {
    await click('Browser Test Analyst');
    await click('Sign Out');
    await open('/review-queue');
    await text('Sign in to MATRIQ');
    await fill('input[name=email]', reviewerEmail);
    await fill('input[name=password]', process.env.MATRIQ_BROWSER_REVIEWER_PASSWORD);
    await click('Sign In', 'form');
    await text('Pending Reviews');
    const conflict = data.proposals.find(item => item.status === 'CONFLICT');
    await open('/review-queue/' + conflict.id);
    await text('Confirmation requires corrected technical evidence');
    assert.ok(await evaluate(() => [...document.querySelectorAll('main button')].some(
      button => button.textContent === 'Confirm Match' && button.disabled)));
    await fill('#review-note', 'The cited specifications need clarification in this isolated test workspace.');
    await click('Request Clarification');
    await click('Record Decision');
    await text('Clarification request recorded in MATRIQ.');
    await open('/review-queue/' + activePair.id);
    await text('The technical evidence supports reviewer confirmation.');
    await fill('#review-note', 'Compared dimensions and supporting technical evidence in the isolated browser test.');
    await click('Confirm Match');
    await click('Record Decision');
    await click('Unified Registry', 'nav');
    await text('1 verified identities');
    await reload();
    await text('1 verified identities');
    console.log('Reviewer confirmation, blocked conflict, clarification and verified registry refresh verified.');
    if (process.env.MATRIQ_BEFORE_BROWSER_URL) {
      await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1040, deviceScaleFactor: 1, mobile: false });
      const views = [
        ['dashboard', 'Material Intelligence Dashboard', 'Material Intelligence Command Center'],
        ['organizations', 'Participating Organisations', 'Participating Organisations'],
        ['catalogues', 'Material Catalogues & Ingestion', 'Material Catalogues & Ingestion'],
        ['harmonization', 'Harmonization Workspace', 'Harmonization Workspace'],
        ['review-queue', 'Review Queue', 'Review Queue'],
        ['registry', 'MATRIQ Unified Material Registry', 'MATRIQ Unified Material Registry'],
      ];
      for (const [version, origin, titleIndex] of [
        ['before', process.env.MATRIQ_BEFORE_BROWSER_URL, 1], ['after', base, 2],
      ]) {
        for (const view of views) {
          const [route] = view;
          await changeDocument('Page.navigate', { url: origin + '/' + route });
          await waitFor(() => evaluate(title => document.querySelector('main h2')?.textContent.trim() === title, view[titleIndex]), view[titleIndex]);
          if (route === 'registry') {
            await waitFor(() => evaluate(() => {
              const button = document.querySelector('main table tbody button');
              if (!button) return false;
              button.click();
              return true;
            }), 'Open verified identity');
            if (version === 'after') await waitFor(() => evaluate(() => [...document.querySelectorAll('main h4')].some(heading => heading.textContent === 'Decision provenance')), 'decision provenance');
          }
          if (route === 'organizations') await text('NTPC Limited');
          const png = await send('Page.captureScreenshot', { captureBeyondViewport: false });
          await writeFile('scratch/restoration/' + version + '-' + route + '.png', Buffer.from(png.data, 'base64'));
        }
      }
      console.log('Captured six before-and-after page pairs using the same live data and account.');
      await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
      for (const [route, , title] of views) {
        await open('/' + route);
        await waitFor(() => evaluate(expected => document.querySelector('main h2')?.textContent.trim() === expected, title), title);
        assert.ok(await evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), route + ' fits a mobile viewport.');
      }
      console.log('Verified the viewport fits on all six pages at 390px.');
    }
  }
} catch (error) {
  if (socket?.readyState === WebSocket.OPEN) {
    const status = await evaluate(() => ({
      path: location.pathname,
      alert: document.querySelector('[role=alert]')?.textContent ?? '',
      title: document.querySelector('h2')?.textContent ?? '',
      fieldErrors: [...document.querySelectorAll('input:invalid')].map(input => [input.name, input.validationMessage]),
    })).catch(() => null);
    console.error('Browser route and rendered status:', status);
  }
  throw error;
} finally {
  socket?.close();
  browser.kill();
}
