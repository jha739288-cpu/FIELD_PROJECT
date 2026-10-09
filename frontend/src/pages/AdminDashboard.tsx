import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getAdminOverview } from '../api/dashboard';
import { apiMessage } from '../api/client';
import type { AdminOverview } from '../api/types';
import { EmptyState, ErrorAlert, SkeletonCard } from '../components/Feedback';

/** Platform overview with real counts and recent rows. ADMIN only. */
export default function AdminDashboard() {
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setOverview(await getAdminOverview());
    } catch (err) {
      setError(apiMessage(err, 'Unable to load platform data. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section>
      <h1>Platform administration</h1>
      {loading && (
        <div className="grid">
          <SkeletonCard />
          <SkeletonCard />
        </div>
      )}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && overview && (
        <>
          <div className="stats">
            <div className="stat">
              <div className="stat-value">{overview.totalUsers}</div>
              <div className="stat-label">Total users</div>
            </div>
            <div className="stat">
              <div className="stat-value">{overview.usersByRole.VENDOR ?? 0}</div>
              <div className="stat-label">Vendors</div>
            </div>
            <div className="stat">
              <div className="stat-value">
                {Object.values(overview.equipmentByStatus).reduce((a, b) => a + b, 0)}
              </div>
              <div className="stat-label">Equipment</div>
            </div>
            <div className="stat">
              <div className="stat-value">
                {Object.values(overview.bookingsByStatus).reduce((a, b) => a + b, 0)}
              </div>
              <div className="stat-label">Bookings</div>
            </div>
            <div className="stat">
              <div className="stat-value">{overview.openAlerts}</div>
              <div className="stat-label">Open alerts</div>
            </div>
          </div>
          <div className="grid">
            <div className="card">
              <div className="card-row">
                <h2>Recent registrations</h2>
                <Link className="btn btn-small" to="/admin/users">
                  All users
                </Link>
              </div>
              {overview.recentUsers.length === 0 && <EmptyState message="No users yet." />}
              <ul className="legend">
                {overview.recentUsers.map((u) => (
                  <li key={u.id}>
                    <strong>{u.username}</strong>{' '}
                    <span className="muted small">
                      {u.email} · {u.roles.join(', ')}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="card">
              <div className="card-row">
                <h2>Recent bookings</h2>
                <Link className="btn btn-small" to="/admin/bookings">
                  All bookings
                </Link>
              </div>
              {overview.recentBookings.length === 0 && <EmptyState message="No bookings yet." />}
              <ul className="legend">
                {overview.recentBookings.map((b) => (
                  <li key={b.id}>
                    <strong>#{b.id}</strong>{' '}
                    <span className="muted small">
                      {b.equipmentCode} · {b.username} · {b.status}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="card">
              <div className="card-row">
                <h2>Recent equipment</h2>
                <Link className="btn btn-small" to="/admin/equipment">
                  All equipment
                </Link>
              </div>
              {overview.recentEquipment.length === 0 && <EmptyState message="No equipment yet." />}
              <ul className="legend">
                {overview.recentEquipment.map((e) => (
                  <li key={e.id}>
                    <strong>{e.equipmentCode}</strong>{' '}
                    <span className="muted small">
                      {e.name} · {e.status.replace(/_/g, ' ')}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <div className="card">
            <h2>Manage</h2>
            <div className="actions">
              <Link className="btn btn-primary" to="/admin/users">
                Users
              </Link>
              <Link className="btn" to="/admin/vendors">
                Vendors
              </Link>
              <Link className="btn" to="/admin/equipment">
                Equipment
              </Link>
              <Link className="btn" to="/analytics">
                Analytics
              </Link>
            </div>
          </div>
        </>
      )}
    </section>
  );
}
