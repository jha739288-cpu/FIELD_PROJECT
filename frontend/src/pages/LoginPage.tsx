import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { homeFor, useAuth } from '../auth/AuthContext';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import { ErrorAlert } from '../components/Feedback';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const location = useLocation() as { state?: { from?: string } };
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
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
      const profile = await login({ username: username.trim(), password }, rememberMe);
      toast.success(`Welcome back, ${profile.username}!`);
      const from = location.state?.from;
      const dest = from !== undefined && /^\/[^\\/]/.test(from) ? from : homeFor(profile.roles);
      navigate(dest, { replace: true });
    } catch (err) {
      setServerError(apiMessage(err, 'Login failed. Please check your connection and try again.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="auth-wrap">
      <div className="auth-side">
        <h2>Welcome back to the lab</h2>
        <p>Your instruments, bookings and usage history are one login away.</p>
        <ul>
          <li>Live equipment availability</li>
          <li>Conflict-free reservations</li>
          <li>QR check-in with metered usage</li>
        </ul>
      </div>
      <div className="auth-form">
        <h1>Login</h1>
      <p className="muted">Access your lab equipment workspace.</p>
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
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
          {errors.password && <span className="field-error">{errors.password}</span>}
        </label>
        <div className="card-row">
          <label className="checkbox-row" style={{ margin: 0 }}>
            <input
              type="checkbox"
              checked={showPassword}
              onChange={(e) => setShowPassword(e.target.checked)}
            />
            Show password
          </label>
          <label className="checkbox-row" style={{ margin: 0 }} title="Keep me logged in on this device">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
            />
            Remember me
          </label>
        </div>
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Logging in…' : 'Login'}
        </button>
      </form>
      <p className="muted">
        No account? <Link to="/register">Register</Link>
      </p>
      </div>
    </section>
  );
}
