import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/** Staff pages: guests sign in first; signed-in customers are told this area isn't for them. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (user.role !== 'ADMIN') {
    return (
      <div className="mx-auto max-w-md py-16 text-center">
        <h1 className="text-2xl font-bold">This area is for shop staff</h1>
        <p className="mt-3 text-ink-soft">You’re signed in as {user.email}, which isn’t an admin account.</p>
        <Link to="/" className="btn-link mt-6 inline-block">
          Back to the shop
        </Link>
      </div>
    );
  }
  return <>{children}</>;
}
