import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getVendorDashboard } from '../api/dashboard';
import { apiMessage } from '../api/client';
import StatusBadge from '../components/StatusBadge';
import AlertsPanel from '../components/AlertsPanel';
import { EmptyState, ErrorAlert, SkeletonCard } from '../components/Feedback';
import type { VendorDashboard as VendorStats } from '../api/types';

function fmt(dt: string): string {
  return new Date(dt).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });
}

/** Vendor home: their inventory, demand on it, utilization and recent bookings. */
export default function VendorDashboard() {
  const [stats, setStats] = useState<VendorStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setStats(await getVendorDashboard());
    } catch (err) {
      setError(apiMessage(err, 'Unable to load vendor data. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section>
      <h1>Vendor dashboard</h1>
      {loading && (
        <div className="grid">
          <SkeletonCard />
          <SkeletonCard />
        </div>
      )}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && stats && (
        <>
          <div className="stats">
            <div className="stat">
              <div className="stat-value">{stats.totalEquipment}</div>
              <div className="stat-label">Equipment listed</div>
            </div>
            <div className="stat">
              <div className="stat-value">{stats.equipmentByStatus.AVAILABLE ?? 0}</div>
              <div className="stat-label">Available</div>
            </div>
            <div className="stat">
              <div className="stat-value">{stats.totalBookings}</div>
              <div className="stat-label">Total bookings</div>
            </div>
            <div className="stat">
              <div className="stat-value">{stats.bookingsByStatus.PENDING ?? 0}</div>
              <div className="stat-label">Pending bookings</div>
            </div>
            <div className="stat">
              <div className="stat-value">{stats.usageHours}h</div>
              <div className="stat-label">Usage time</div>
            </div>
            <div className="stat">
              <div className="stat-value">{stats.completedSessions}</div>
              <div className="stat-label">Completed sessions</div>
            </div>
          </div>
          <div className="card">
            <div className="card-row">
              <h2>Recent bookings on my equipment</h2>
              <Link className="btn btn-small" to="/vendor/bookings">
                All bookings
              </Link>
            </div>
            {stats.recentBookings.length === 0 && (
              <EmptyState message="No bookings on your equipment yet." />
            )}
            {stats.recentBookings.map((b) => (
              <div key={b.id} className="card-row">
                <span>
                  <strong>#{b.id}</strong> {b.equipmentCode} · {b.username} · {fmt(b.startTime)}
                </span>
                <StatusBadge value={b.status} />
              </div>
            ))}
            <div className="actions">
              <Link className="btn btn-primary" to="/vendor/equipment/new">
                + Add equipment
              </Link>
              <Link className="btn" to="/vendor/equipment">
                My equipment
              </Link>
            </div>
          </div>
          <AlertsPanel />
        </>
      )}
    </section>
  );
}
