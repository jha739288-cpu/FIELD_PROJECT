import { useCallback, useEffect, useState } from 'react';
import { getMySummary } from '../api/dashboard';
import { apiMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';
import type { MySummary } from '../api/types';

/** Read-only profile: everything shown comes from the backend (no edit API exists). */
export default function ProfilePage() {
  const { user } = useAuth();
  const [summary, setSummary] = useState<MySummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setSummary(await getMySummary());
    } catch (err) {
      setError(apiMessage(err, 'Unable to load profile. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) return <Loading label="Loading profile…" />;
  if (error) return <ErrorAlert message={error} onRetry={load} />;
  if (!user || !summary) return <EmptyState message="Profile unavailable." />;

  const initials = (user.fullName || user.username).slice(0, 2).toUpperCase();

  return (
    <section>
      <h1>Profile</h1>
      <div className="card">
        <div className="card-row">
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <span className="equip-mono" style={{ fontSize: '2rem' }} aria-hidden>
              {initials}
            </span>
            <div>
              <h2 style={{ margin: 0 }}>{user.fullName || user.username}</h2>
              <p className="muted small" style={{ margin: 0 }}>
                @{user.username} · {user.email}
              </p>
            </div>
          </div>
          <div style={{ display: 'flex', gap: '0.4rem' }}>
            {user.roles.map((r) => (
              <StatusBadge key={r} value={r} />
            ))}
          </div>
        </div>
        <p className="muted small">
          Account {user.enabled ? 'active' : 'disabled'} · trailing 30 days below
        </p>
      </div>

      <div className="stats">
        <div className="stat">
          <div className="stat-value">{summary.bookings.total}</div>
          <div className="stat-label">Bookings</div>
        </div>
        <div className="stat">
          <div className="stat-value">{summary.bookings.confirmed}</div>
          <div className="stat-label">Confirmed</div>
        </div>
        <div className="stat">
          <div className="stat-value">{summary.bookings.completed}</div>
          <div className="stat-label">Completed</div>
        </div>
        <div className="stat">
          <div className="stat-value">{summary.usage.totalHours}h</div>
          <div className="stat-label">Usage time</div>
        </div>
      </div>

      <div className="card">
        <h2>Bookings by status</h2>
        <ul className="legend">
          {(
            [
              ['Pending', summary.bookings.pending],
              ['Confirmed', summary.bookings.confirmed],
              ['Checked in', summary.bookings.checkedIn],
              ['Completed', summary.bookings.completed],
              ['Cancelled', summary.bookings.cancelled],
              ['Overdue', summary.bookings.overdue],
              ['Rejected', summary.bookings.rejected]
            ] as Array<[string, number]>
          ).map(([label, count]) => (
            <li key={label}>
              {label}: <strong>{count}</strong>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
