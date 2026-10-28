import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { isApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ErrorNote, Notice } from '../components/Feedback';
import { Field } from '../components/Field';

export function LoginPage() {
  const { user, login, sessionEnded } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as { from?: string; registered?: string } | null;
  const from = state?.from ?? '/';
  const [email, setEmail] = useState(state?.registered ?? '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={from} replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(isApiError(err, 401) ? new Error('That email and password don’t match an account.') : err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm py-6">
      <h1 className="text-3xl font-bold">Sign in</h1>
      <div className="mt-4 space-y-3">
        {state?.registered && <Notice tone="good">Your account is ready. Sign in to continue.</Notice>}
        {sessionEnded && !state?.registered && <Notice tone="warn">You were signed out. Sign in again to continue.</Notice>}
      </div>
      <form onSubmit={onSubmit} className="mt-4 space-y-4">
        <Field label="Email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Field
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {error ? <ErrorNote error={error} /> : null}
        <button type="submit" className="btn-dark w-full py-3" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="mt-6 text-sm">
        New here?{' '}
        <Link to="/register" state={state} className="btn-link">
          Create an account
        </Link>
      </p>
    </div>
  );
}
