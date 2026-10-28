import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { createQueryClient } from '../queryClient';

/** A JWT the app can read (the tests' fake gateway doesn't check signatures). */
export function fakeToken(claims: Record<string, unknown> = {}) {
  const enc = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${enc({ alg: 'HS256' })}.${enc({ sub: 'u-1', email: 'omesh@example.com', role: 'CUSTOMER', exp: Math.floor(Date.now() / 1000) + 3600, ...claims })}.sig`;
}

export function signIn() {
  window.localStorage.setItem('cartwheel.token', fakeToken());
}

type Handler = (url: URL, init: RequestInit) => { status?: number; body?: unknown } | undefined;

/** Replaces fetch: each call goes to the first handler that answers it; unanswered calls fail the test loudly. */
export function mockApi(handler: Handler) {
  const calls: { method: string; url: URL; body: unknown; headers: Record<string, string> }[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost');
    const body = init.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ method: init.method ?? 'GET', url, body, headers: (init.headers ?? {}) as Record<string, string> });
    const answer = handler(url, init);
    if (!answer) throw new Error(`Unexpected ${init.method ?? 'GET'} ${url.pathname}${url.search}`);
    const status = answer.status ?? 200;
    const payload = status >= 400 ? answer.body : { success: true, message: 'OK', data: answer.body };
    return new Response(status === 204 ? null : JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fetchMock);
  return calls;
}

function Location() {
  const l = useLocation();
  return <div data-testid="location">{l.pathname + l.search}</div>;
}

export function renderAt(path: string, routePath: string, element: ReactElement) {
  const queryClient = createQueryClient();
  queryClient.setDefaultOptions({ queries: { ...queryClient.getDefaultOptions().queries, retry: false } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <Routes>
            <Route path={routePath} element={element} />
            <Route path="*" element={<div>other page</div>} />
          </Routes>
          <Location />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
