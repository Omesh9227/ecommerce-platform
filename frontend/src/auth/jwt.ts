/** Reads (doesn't verify) the claims of user-service's JWT; the gateway verifies it on every call. */
export interface TokenClaims {
  userId: string;
  email: string;
  role: string;
  expiresAt: number; // epoch ms
}

export function readToken(token: string): TokenClaims | null {
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    const claims = JSON.parse(json);
    if (typeof claims.sub !== 'string' || typeof claims.exp !== 'number') return null;
    return { userId: claims.sub, email: claims.email ?? '', role: claims.role ?? 'CUSTOMER', expiresAt: claims.exp * 1000 };
  } catch {
    return null;
  }
}

export function isExpired(claims: TokenClaims, now = Date.now()): boolean {
  // Treat a token as gone 30 s early, so a request doesn't start with one that expires on the way
  return claims.expiresAt - 30_000 <= now;
}
