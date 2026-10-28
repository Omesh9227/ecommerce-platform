import { defineConfig, devices } from '@playwright/test';

/**
 * npm run e2e        → against the in-memory mock API (no backend needed; starts its own servers)
 * npm run e2e:live   → against your running services: the dev server (npm run dev, port 3000) → api-gateway :8080
 */
const live = process.env.E2E_TARGET === 'live';
const baseURL = process.env.E2E_BASE_URL ?? (live ? 'http://localhost:3000' : 'http://localhost:3100');

export default defineConfig({
  testDir: './e2e',
  timeout: live ? 180_000 : 60_000,
  expect: { timeout: live ? 30_000 : 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'en-IN',
    timezoneId: 'Asia/Kolkata',
  },
  projects: [{ name: live ? 'live' : 'mock', use: { ...devices['Desktop Chrome'] } }],
  webServer: live
    ? { command: 'npm run dev', url: baseURL, reuseExistingServer: true, timeout: 60_000 }
    : [
        { command: 'node e2e/mock-api/server.mjs', url: 'http://localhost:4010/api/v1/categories', reuseExistingServer: true, env: { MOCK_API_PORT: '4010' } },
        {
          command: 'npx vite --port 3100',
          url: baseURL,
          reuseExistingServer: true,
          env: { VITE_API_TARGET: 'http://localhost:4010', PORT: '3100' },
        },
      ],
});
