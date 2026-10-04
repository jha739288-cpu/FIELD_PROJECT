import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { homeFor, useAuth } from '../auth/AuthContext';
import { apiMessage } from '../api/client';
import { ErrorAlert } from '../components/Feedback';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation() as { state?: { from?: string } };
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ username?: string; password?: string }>({});
  const [serverError, setServerError] = useState('');
  const [busy, setBusy] = useState(false);

  const validate = () => {
    const next: typeof errors = {};
    if (!username.trim()) next.username = 'Username is required';
    if (!password) next.password = 'Password is required';
    else if (password.length < 8) next.password = 'Password must be at least 8 characters';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setServerError('');
    if (!validate()) return;
    setBusy(true);
    try {
      const profile = await login({ username: username.trim(), password });
      // 'from' is set by our own ProtectedRoute, but never trust navigation input:
      // allow same-app paths only (blocks //host and backslash open-redirects).
      const from = location.state?.from;
      const dest =
        from !== undefined && /^\/[^\\/]/.test(from) ? from : homeFor(profile.roles);
      navigate(dest, { replace: true });
    } catch (err) {
      setServerError(apiMessage(err, 'Login failed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card narrow">
      <h1>Login</h1>
      {serverError && <ErrorAlert message={serverError} />}
      <form onSubmit={onSubmit} noValidate>
        <label className="field">
          Username
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            placeholder="e.g. sara"
          />
          {errors.username && <span className="field-error">{errors.username}</span>}
        </label>
        <label className="field">
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
          {errors.password && <span className="field-error">{errors.password}</span>}
        </label>
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Logging in…' : 'Login'}
        </button>
      </form>
      <p className="muted">
        No account? <Link to="/register">Register</Link>
      </p>
    </section>
  );
}
