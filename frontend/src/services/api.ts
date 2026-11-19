import type { Batch, CatalogueValidation, CatalogueOptions, ReviewAction, Session, Workspace } from '../types/workspace';
import type { Organization } from '../types';

let csrfToken = '';
let sessionInFlight: Promise<Session> | undefined;

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers = new Headers({ Accept: 'application/json' });
  if (method !== 'GET') headers.set('X-CSRF-Token', csrfToken);
  const multipart = body instanceof FormData;
  if (body !== undefined && !multipart) headers.set('Content-Type', 'application/json');
  const response = await fetch('/api' + path, {
    method, headers, credentials: 'same-origin',
    body: body === undefined ? undefined : multipart ? body : JSON.stringify(body),
  });
  if (!response.ok) {
    const failure = await response.json().catch(() => null);
    if (response.status === 401 && !path.startsWith('/auth/')) window.dispatchEvent(new Event('matriq:expired'));
    const details = Array.isArray(failure?.details)
      ? failure.details.filter((detail: unknown) => typeof detail === 'string').join(' ')
      : '';
    const error = typeof failure?.error === 'string' ? failure.error : '';
    const fallback = path.startsWith('/auth/') && (response.status === 404 || response.status >= 500)
      ? 'The MATRIQ sign-in service is unavailable. Please try again later.'
      : 'MATRIQ could not complete this request.';
    throw new ApiError(response.status, details || error || fallback);
  }
  return response.json() as Promise<T>;
}

async function sessionRequest(path: string, method = 'GET', body?: unknown) {
  const session = await request<Session>(path, method, body);
  csrfToken = session.csrfToken;
  return session;
}

function fileForm(file: File, options?: CatalogueOptions) {
  const form = new FormData();
  form.append('file', file);
  if (options) form.append('options', new Blob([JSON.stringify(options)], { type: 'application/json' }));
  return form;
}

export const api = {
  session: () => sessionInFlight ??= sessionRequest('/auth/session').finally(() => { sessionInFlight = undefined; }),
  authenticate: (mode: 'login' | 'signup', input: { name?: string; email: string; password: string }) =>
    sessionRequest('/auth/' + mode, 'POST', input),
  logout: () => sessionRequest('/auth/logout', 'POST'),
  workspace: () => request<Workspace>('/workspace'),
  addOrganization: ({ code, name, type, location, contactEmail }: Organization) =>
    request<Workspace>('/workspace/organizations', 'POST', { code, name, type, location, contactEmail }),
  inspectCatalogue: (file: File) => request<string[][]>('/workspace/catalogues/inspect', 'POST', fileForm(file)),
  validateCatalogue: (file: File, options: CatalogueOptions) =>
    request<CatalogueValidation>('/workspace/catalogues/validate', 'POST', fileForm(file, options)),
  importCatalogue: (file: File, options: CatalogueOptions) =>
    request<Batch>('/workspace/catalogues', 'POST', fileForm(file, options)),
  harmonize: () => request<Workspace>('/workspace/harmonize', 'POST'),
  review: (id: string, action: ReviewAction, rationale: string) =>
    request<Workspace>('/workspace/reviews/' + encodeURIComponent(id), 'POST', { action, rationale }),
};
