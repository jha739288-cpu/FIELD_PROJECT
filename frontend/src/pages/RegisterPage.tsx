import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { homeFor, useAuth } from '../auth/AuthContext';
import { apiMessage } from '../api/client';
import { ErrorAlert } from '../components/Feedback';

const USERNAME_RE = /^[A-Za-z0-9._-]+$/;

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', email: '', password: '', fullName: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  const validate = () => {
    const next: Record<string, string> = {};
    if (form.username.trim().length < 3 || form.username.trim().length > 50)
      next.username = 'Username must be 3–50 characters';
    else if (!USERNAME_RE.test(form.username.trim()))
      next.username = "Letters, digits, '.', '_' or '-' only";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Enter a valid email';
    if (form.password.length < 8 || form.password.length > 100)
      next.password = 'Password must be 8–100 characters';
    if (form.fullName && form.fullName.length > 100)
      next.fullName = 'Full name must be at most 100 characters';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setServerError('');
    if (!validate()) return;
    setBusy(true);
    try {
      const profile = await register({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        fullName: form.fullName.trim() || undefined
      });
      navigate(homeFor(profile.roles), { replace: true });
    } catch (err) {
      setServerError(apiMessage(err, 'Registration failed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card narrow">
      <h1>Register</h1>
      <p className="muted">Self-registration creates a STUDENT account.</p>
      {serverError && <ErrorAlert message={serverError} />}
      <form onSubmit={onSubmit} noValidate>
        <label className="field">
          Username
          <input value={form.username} onChange={set('username')} autoComplete="username" />
          {errors.username && <span className="field-error">{errors.username}</span>}
        </label>
        <label className="field">
          Email
          <input value={form.email} onChange={set('email')} autoComplete="email" />
          {errors.email && <span className="field-error">{errors.email}</span>}
        </label>
        <label className="field">
          Password
          <input
            type="password"
            value={form.password}
            onChange={set('password')}
            autoComplete="new-password"
          />
          {errors.password && <span className="field-error">{errors.password}</span>}
        </label>
        <label className="field">
          Full name (optional)
          <input value={form.fullName} onChange={set('fullName')} autoComplete="name" />
          {errors.fullName && <span className="field-error">{errors.fullName}</span>}
        </label>
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Registering…' : 'Register'}
        </button>
      </form>
      <p className="muted">
        Have an account? <Link to="/login">Login</Link>
      </p>
    </section>
  );
}
