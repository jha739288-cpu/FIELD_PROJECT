import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { homeFor, useAuth } from '../auth/AuthContext';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import { ErrorAlert } from '../components/Feedback';

const USERNAME_RE = /^[A-Za-z0-9._-]+$/;

function strength(password: string): { score: number; label: string } {
  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) score++;
  if (/\d/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;
  const label =
    score <= 1 ? 'Weak' : score <= 3 ? 'Fair' : score === 4 ? 'Good' : 'Strong';
  return { score, label };
}

const STRENGTH_COLORS = ['#dc2626', '#d97706', '#d97706', '#16a34a', '#16a34a', '#16a34a'];

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [form, setForm] = useState({ username: '', email: '', password: '', confirm: '', fullName: '', role: 'USER' });
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState('');
  const [busy, setBusy] = useState(false);

  const pw = useMemo(() => strength(form.password), [form.password]);

  const set = (key: keyof typeof form) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const validate = () => {
    const next: Record<string, string> = {};
    if (form.username.trim().length < 3 || form.username.trim().length > 50)
      next.username = 'Username must be 3–50 characters';
    else if (!USERNAME_RE.test(form.username.trim()))
      next.username = "Letters, digits, '.', '_' or '-' only";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Enter a valid email';
    if (form.password.length < 8 || form.password.length > 100)
      next.password = 'Password must be 8–100 characters';
    if (form.confirm !== form.password) next.confirm = 'Passwords do not match';
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
        fullName: form.fullName.trim() || undefined,
        role: form.role === 'VENDOR' ? 'VENDOR' : 'USER'
      });
      toast.success(`Account created — welcome, ${profile.username}!`);
      navigate(homeFor(profile.roles), { replace: true });
    } catch (err) {
      setServerError(apiMessage(err, 'Registration failed. Please try again.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="auth-wrap">
      <div className="auth-side">
        <h2>Join the marketplace</h2>
        <p>One account for booking instruments, tracking usage and forecasting availability.</p>
        <ul>
          <li>Users book and use equipment</li>
          <li>Vendors list and manage instruments</li>
          <li>JWT-secured API, role-checked everywhere</li>
        </ul>
      </div>
      <div className="auth-form">
        <h1>Register</h1>
      <p className="muted">Choose your account type below.</p>
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
            type={showPassword ? 'text' : 'password'}
            value={form.password}
            onChange={set('password')}
            autoComplete="new-password"
          />
          {form.password && (
            <span className="hint">
              Strength: {pw.label}
              <span className="strength">
                <span style={{ width: `${(pw.score / 5) * 100}%`, background: STRENGTH_COLORS[pw.score] }} />
              </span>
            </span>
          )}
          {errors.password && <span className="field-error">{errors.password}</span>}
        </label>
        <label className="field">
          Confirm password
          <input
            type={showPassword ? 'text' : 'password'}
            value={form.confirm}
            onChange={set('confirm')}
            autoComplete="new-password"
          />
          {errors.confirm && <span className="field-error">{errors.confirm}</span>}
        </label>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={showPassword}
            onChange={(e) => setShowPassword(e.target.checked)}
          />
          Show passwords
        </label>
        <label className="field">
          Full name (optional)
          <input value={form.fullName} onChange={set('fullName')} autoComplete="name" />
          {errors.fullName && <span className="field-error">{errors.fullName}</span>}
        </label>
        <label className="field">
          Register as
          <select value={form.role} onChange={set('role')}>
            <option value="USER">User — book and use equipment</option>
            <option value="VENDOR">Vendor — list and manage equipment</option>
          </select>
          <span className="hint">Admin accounts are created by an administrator, never here.</span>
        </label>
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Registering…' : 'Register'}
        </button>
      </form>
      <p className="muted">
        Have an account? <Link to="/login">Login</Link>
      </p>
      </div>
    </section>
  );
}
