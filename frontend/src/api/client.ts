import type { ApiEnvelope } from './types';

/**
 * Talks to the api-gateway. In development the Vite server forwards /api to it, so paths stay relative.
 * Success bodies are { success, message, data }; errors are { status, message, validationErrors? }.
 */

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

type TokenProvider = () => string | null;
type UnauthorizedHandler = () => void;

let tokenProvider: TokenProvider = () => null;
let onUnauthorized: UnauthorizedHandler = () => {};

/** Wired up by the auth context: where the token comes from, and what to do when the gateway rejects it. */
export function configureApi(provider: TokenProvider, unauthorized: UnauthorizedHandler) {
  tokenProvider = provider;
  onUnauthorized = unauthorized;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** Query parameters; arrays repeat the key (brand=a&brand=b); undefined / '' are left out. */
  query?: Record<string, string | number | boolean | string[] | undefined | null>;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

export function buildQuery(query: RequestOptions['query']): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) value.forEach((v) => params.append(key, v));
    else params.append(key, String(value));
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

/** Returns `data` from the envelope (or null for 204). Throws ApiError for anything that isn't 2xx. */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = tokenProvider();
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(path + buildQuery(options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    if ((error as Error).name === 'AbortError') throw error;
    throw new ApiError(0, 'Can’t reach the shop right now. Check that the services are running and try again.');
  }

  if (response.status === 204) return null as T;
  const body = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiError(response.status, errorMessage(response.status, body), body?.validationErrors ?? {});
  }
  return (body as ApiEnvelope<T>).data;
}

function errorMessage(status: number, body: { message?: string } | null): string {
  if (body?.message && body.message !== 'Validation failed') return body.message;
  if (body?.message === 'Validation failed') return 'Please check the highlighted fields.';
  switch (status) {
    case 401:
      return 'Please sign in to continue.';
    case 403:
      return 'You don’t have access to this.';
    case 404:
      return 'We couldn’t find that.';
    case 503:
    case 504:
      return 'This part of the shop isn’t responding right now. Try again in a moment.';
    default:
      return 'Something went wrong. Try again.';
  }
}

export function isApiError(error: unknown, status?: number): error is ApiError {
  return error instanceof ApiError && (status === undefined || error.status === status);
}

/** A fresh key for Idempotency-Key headers (checkout, payment attempts). */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `k${Date.now()}${Math.random().toString(36).slice(2, 12)}`;
}
