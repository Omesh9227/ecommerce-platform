import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, isApiError } from '../api/client';
import { auth } from '../api/shop';
import type { RegisterRequest } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorNote } from '../components/Feedback';
import { Field } from '../components/Field';
import { rules, type Errors } from '../lib/validation';

type Key = keyof RegisterRequest;

export function validateRegistration(form: RegisterRequest): Errors<Key> {
  const errors: Errors<Key> = {};
  if (!form.firstName.trim()) errors.firstName = 'Enter your first name';
  else if (!rules.name.test(form.firstName.trim())) errors.firstName = 'Use letters, spaces, dots, apostrophes or hyphens';
  if (!form.lastName.trim()) errors.lastName = 'Enter your last name';
  else if (!rules.name.test(form.lastName.trim())) errors.lastName = 'Use letters, spaces, dots, apostrophes or hyphens';
  if (!rules.email.test(form.email.trim())) errors.email = 'Enter an email like name@example.com';
  if (!rules.phone.test(form.phoneNumber)) errors.phoneNumber = 'Enter 10 to 15 digits';
  if (!rules.password.test(form.password)) {
    errors.password = 'Use 8–64 characters with a lowercase and an uppercase letter, a digit and a symbol, no spaces';
  }
  return errors;
}

export function RegisterPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const [form, setForm] = useState<RegisterRequest>({ firstName: '', lastName: '', email: '', phoneNumber: '', password: '' });
  const [errors, setErrors] = useState<Errors<Key>>({});
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  function set(key: Key, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
    if (errors[key]) setErrors((e) => ({ ...e, [key]: undefined }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const found = validateRegistration(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    setBusy(true);
    setError(null);
    const request = { ...form, firstName: form.firstName.trim(), lastName: form.lastName.trim(), email: form.email.trim() };
    try {
      await auth.register(request);
    } catch (err) {
      setBusy(false);
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setErrors(err.fieldErrors as Errors<Key>);
      setError(isApiError(err, 409) ? new Error('An account with this email already exists. Sign in instead.') : err);
      return;
    }
    try {
      await login(request.email, request.password);
      navigate(from, { replace: true });
    } catch {
      navigate('/login', { replace: true, state: { from, registered: request.email } });
    }
  }

  return (
    <div className="mx-auto max-w-md py-6">
      <h1 className="text-3xl font-bold">Create your account</h1>
      <form onSubmit={onSubmit} noValidate className="mt-6 grid gap-4 sm:grid-cols-2">
        <Field label="First name" autoComplete="given-name" value={form.firstName} error={errors.firstName} onChange={(e) => set('firstName', e.target.value)} />
        <Field label="Last name" autoComplete="family-name" value={form.lastName} error={errors.lastName} onChange={(e) => set('lastName', e.target.value)} />
        <Field
          className="sm:col-span-2"
          label="Email"
          type="email"
          autoComplete="email"
          value={form.email}
          error={errors.email}
          onChange={(e) => set('email', e.target.value)}
        />
        <Field
          className="sm:col-span-2"
          label="Mobile number"
          inputMode="numeric"
          autoComplete="tel-national"
          value={form.phoneNumber}
          error={errors.phoneNumber}
          onChange={(e) => set('phoneNumber', e.target.value.replace(/\D/g, ''))}
        />
        <Field
          className="sm:col-span-2"
          label="Password"
          type="password"
          autoComplete="new-password"
          value={form.password}
          error={errors.password}
          hint="8–64 characters with a lowercase and an uppercase letter, a digit and a symbol"
          onChange={(e) => set('password', e.target.value)}
        />
        {error ? (
          <div className="sm:col-span-2">
            <ErrorNote error={error} />
          </div>
        ) : null}
        <button type="submit" className="btn-dark py-3 sm:col-span-2" disabled={busy}>
          {busy ? 'Creating your account…' : 'Create account'}
        </button>
      </form>
      <p className="mt-6 text-sm">
        Already have an account?{' '}
        <Link to="/login" state={location.state} className="btn-link">
          Sign in
        </Link>
      </p>
    </div>
  );
}
