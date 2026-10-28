import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, buildQuery, configureApi } from './client';

function respond(status: number, body?: unknown) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(body === undefined ? null : JSON.stringify(body), { status })));
}

afterEach(() => {
  vi.unstubAllGlobals();
  configureApi(() => null, () => {});
});

describe('api client', () => {
  it('builds query strings, repeating arrays and skipping empty values', () => {
    expect(buildQuery({ q: 'pixel 9', brand: ['Google', 'Samsung'], page: 0, minPrice: '', inStock: undefined })).toBe(
      '?q=pixel+9&brand=Google&brand=Samsung&page=0',
    );
    expect(buildQuery({})).toBe('');
  });

  it('returns data from the envelope and sends the token', async () => {
    configureApi(() => 'tkn', () => {});
    respond(200, { success: true, message: 'OK', data: { id: 1 } });
    await expect(api('/api/v1/x')).resolves.toEqual({ id: 1 });
    const init = vi.mocked(fetch).mock.calls[0][1]!;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer tkn');
  });

  it('returns null for 204', async () => {
    respond(204);
    await expect(api('/api/v1/x', { method: 'DELETE' })).resolves.toBeNull();
  });

  it('turns error bodies into ApiError with field errors', async () => {
    respond(400, { status: 400, message: 'Validation failed', validationErrors: { email: 'Email must be valid' } });
    const error = (await api('/api/v1/x').catch((e) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe('Please check the highlighted fields.');
    expect(error.fieldErrors).toEqual({ email: 'Email must be valid' });
  });

  it('keeps the services’ own messages', async () => {
    respond(409, { status: 409, message: 'Pixel 9 is out of stock' });
    await expect(api('/api/v1/cart/items')).rejects.toThrow('Pixel 9 is out of stock');
  });

  it('ends the session when the gateway rejects the token', async () => {
    const onUnauthorized = vi.fn();
    configureApi(() => 'old', onUnauthorized);
    respond(401, { status: 401, message: 'Token expired' });
    await expect(api('/api/v1/cart')).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('says plainly when the gateway can’t be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))));
    await expect(api('/api/v1/x')).rejects.toMatchObject({ status: 0, message: expect.stringContaining('Can’t reach the shop') });
  });
});
