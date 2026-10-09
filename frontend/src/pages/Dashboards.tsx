import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMySummary, getSummary } from '../api/dashboard';
import { myBookings } from '../api/bookings';
import { apiMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import StatusBadge from '../components/StatusBadge';
import AlertsPanel from '../components/AlertsPanel';
import { EmptyState, ErrorAlert, SkeletonCard } from '../components/Feedback';
import type { Booking, MySummary, Summary } from '../api/types';

function fmt(dt: string): string {
  return new Date(dt).toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });
}

export function StudentDashboard() {
  const { user } = useAuth();
  const [summary, setSummary] = useState<MySummary | null>(null);
  const [recent, setRecent] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [mine, bookings] = await Promise.all([getMySummary(), myBookings('', 0, 5)]);
      setSummary(mine);
      setRecent(bookings.content);
    } catch (err) {
      setError(apiMessage(err, 'Unable to load dashboard. Please check your connection and try again.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section>
      <h1>Welcome{user?.fullName ? `, ${user.fullName.split(' ')[0]}` : ''}</h1>
      {loading && (
        <div className="grid">
          <SkeletonCard />
          <SkeletonCard />
        </div>
      )}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && summary && (
        <>
          <div className="stats">
            <div className="stat">
              <div className="stat-value">{summary.bookings.total}</div>
              <div className="stat-label">My bookings (30d)</div>
            </div>
            <div className="stat">
              <div className="stat-value">{summary.bookings.confirmed}</div>
              <div className="stat-label">Confirmed</div>
            </div>
            <div className="stat">
              <div className="stat-value">{summary.bookings.pending}</div>
              <div className="stat-label">Pending</div>
            </div>
            <div className="stat">
              <div className="stat-value">{summary.usage.totalHours}h</div>
              <div className="stat-label">Usage time</div>
            </div>
          </div>
          <div className="card">
            <div className="card-row">
              <h2>Recent bookings</h2>
              <Link className="btn btn-small" to="/bookings">
                View all
              </Link>
            </div>
            {recent.length === 0 && <EmptyState message="No bookings yet — reserve your first instrument." />}
            {recent.map((b) => (
              <div key={b.id} className="card-row">
                <span>
                  <strong>#{b.id}</strong> {b.equipmentName} · {fmt(b.startTime)}
                </span>
                <StatusBadge value={b.status} />
              </div>
            ))}
            <div className="actions">
              <Link className="btn btn-primary" to="/equipment">
                Browse equipment
              </Link>
              <Link className="btn" to="/calendar">
                Booking calendar
              </Link>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

export function StaffDashboard() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setSummary(await getSummary());
    } catch (err) {
      setError(apiMessage(err, 'Unable to load operations data. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section>
      <h1>Lab operations</h1>
      {loading && (
        <div className="grid">
          <SkeletonCard />
          <SkeletonCard />
        </div>
      )}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && summary && (
        <>
          <div className="stats">
            <div className="stat">
              <div className="stat-value">{summary.equipment.total}</div>
              <div className="stat-label">Equipment</div>
            </div>
            <div className="stat">
              <div className="stat-value">{summary.equipment.available}</div>
              <div className="stat-label">Available</div>
            </div>
            <div className="stat">
              <div className="stat-value">{summary.equipment.inUse}</div>
              <div className="stat-label">In use</div>
            </div>
            <div className="stat">
              <div className="stat-value">{summary.equipment.maintenance}</div>
              <div className="stat-label">Maintenance</div>
            </div>
            <div className="stat">
              <div className="stat-value">{summary.bookings.pending}</div>
              <div className="stat-label">Pending bookings</div>
            </div>
            <div className="stat">
              <div className="stat-value">{summary.alerts.open}</div>
              <div className="stat-label">Open alerts</div>
            </div>
            <div className="stat">
              <div className="stat-value">{summary.utilizationPercent}%</div>
              <div className="stat-label">Utilization (30d)</div>
            </div>
          </div>
          <AlertsPanel />
          <div className="card">
            <h2>Manage</h2>
            <div className="actions">
              <Link className="btn btn-primary" to="/manage/equipment">
                Equipment
              </Link>
              <Link className="btn" to="/manage/bookings">
                Bookings
              </Link>
              <Link className="btn" to="/analytics">
                Full analytics
              </Link>
              <Link className="btn" to="/calendar">
                Booking calendar
              </Link>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

export function AdminDashboard() {
  return (
    <section>
      <h1>Administration</h1>
      <StaffDashboard />
      <div className="card">
        <h2>User management</h2>
        <p className="muted">
          The backend does not expose a user-administration API yet (accounts are
          created by self-registration; roles are granted directly in the database).
          User management UI will arrive with that API — nothing is faked here.
        </p>
      </div>
    </section>
  );
}
