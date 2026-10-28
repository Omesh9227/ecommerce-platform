import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { configureApi } from '../api/client';
import { auth as authApi } from '../api/shop';
import { storage } from '../lib/storage';
import { isExpired, readToken, type TokenClaims } from './jwt';

const TOKEN_KEY = 'cartwheel.token';

interface AuthState {
  user: TokenClaims | null;
  /** Set when the session ended on its own (expired or rejected), so the login page can say why. */
  sessionEnded: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function loadToken(): { token: string; claims: TokenClaims } | null {
  const token = storage.get(TOKEN_KEY);
  const claims = token ? readToken(token) : null;
  if (!token || !claims || isExpired(claims)) {
    storage.remove(TOKEN_KEY);
    return null;
  }
  return { token, claims };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState(loadToken);
  const [sessionEnded, setSessionEnded] = useState(false);

  const endSession = useCallback(
    (expired: boolean) => {
      storage.remove(TOKEN_KEY);
      setSession(null);
      setSessionEnded(expired);
      queryClient.removeQueries({ predicate: (q) => q.meta?.private === true });
    },
    [queryClient],
  );

  // The API client reads the token at call time; a 401 means the gateway no longer accepts it
  configureApi(
    () => {
      if (session && isExpired(session.claims)) return null;
      return session?.token ?? null;
    },
    () => endSession(true),
  );

  // Sign out when the token expires while the page is open
  useEffect(() => {
    if (!session) return;
    const wait = session.claims.expiresAt - 30_000 - Date.now();
    const timer = window.setTimeout(() => endSession(true), Math.max(wait, 0));
    return () => window.clearTimeout(timer);
  }, [session, endSession]);

  const login = useCallback(
    async (email: string, password: string) => {
      const result = await authApi.login(email, password);
      const claims = readToken(result.accessToken);
      if (!claims) throw new Error('The sign-in answer could not be read.');
      storage.set(TOKEN_KEY, result.accessToken);
      setSessionEnded(false);
      setSession({ token: result.accessToken, claims });
      queryClient.removeQueries({ predicate: (q) => q.meta?.private === true });
    },
    [queryClient],
  );

  const logout = useCallback(() => endSession(false), [endSession]);

  const value = useMemo<AuthState>(
    () => ({ user: session?.claims ?? null, sessionEnded, login, logout }),
    [session, sessionEnded, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
