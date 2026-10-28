import { QueryClient } from '@tanstack/react-query';
import { isApiError } from './api/client';

declare module '@tanstack/react-query' {
  interface Register {
    /** private: belongs to the signed-in customer, dropped on sign-in / sign-out */
    queryMeta: { private?: boolean };
  }
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: false,
        // 4xx answers won't change by asking again
        retry: (count, error) => !(isApiError(error) && error.status >= 400 && error.status < 500) && count < 2,
      },
      mutations: { retry: false },
    },
  });
}
