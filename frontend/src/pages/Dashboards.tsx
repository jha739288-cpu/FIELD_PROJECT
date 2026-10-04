import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { getSummary } from '../api/dashboard';
import { myUsage } from '../api/usage';
import { listEquipment } from '../api/equipment';
import type { Summary } from '../api/types';
import AnalyticsView from './AnalyticsView';

function ProfileCard() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <div className="card">
      <h2>{user.fullName || user.username}</h2>
      <p className="muted">
        {user.email} · {user.roles.join(', ')}
      </p>
    </div>
  );
}

export function StudentDashboard() {
  const [usage, setUsage] = useState<{ sessions: number; hours: number } | null>(null);
  const [catalog, setCatalog] = useState<number | null>(null);

  useEffect(() => {
    myUsage(100)
      .then((p) => ({
        sessions: p.totalElements,
        hours: p.content.reduce((a, r) => a + (r.durationSeconds ?? 0), 0) / 3600
      }))
      .then(setUsage)
      .catch(() => setUsage({ sessions: 0, hours: 0 }));
    listEquipment({ size: 1 })
      .then((p) => setCatalog(p.totalElements))
      .catch(() => setCatalog(0));
  }, []);

  return (
    <section>
      <h1>My dashboard</h1>
      <div className="grid">
        <ProfileCard />
        <div className="card">
          <h2>My usage (recorded)</h2>
          {usage === null || catalog === null ? (
            <p className="muted">Loading…</p>
          ) : (
            <p>
              <strong>{usage.sessions}</strong> session(s) ·{' '}
              <strong>{usage.hours.toFixed(1)}h</strong> total ·{' '}
              <strong>{catalog}</strong> item(s) in catalog
            </p>
          )}
          <div className="actions">
            <Link className="btn btn-primary" to="/equipment">
              Browse equipment
            </Link>
            <Link className="btn" to="/bookings">
              My bookings
            </Link>
            <Link className="btn" to="/calendar">
              Booking calendar
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}

export function StaffDashboard() {
  const [summary, setSummary] = useState<Summary | null>(null);

  useEffect(() => {
    getSummary()
      .then(setSummary)
      .catch(() => setSummary(null));
  }, []);

  return (
    <section>
      <h1>Lab staff dashboard</h1>
      <div className="grid">
        <ProfileCard />
        <div className="card">
          <h2>Right now</h2>
          {summary ? (
            <p>
              <strong>{summary.equipment.total}</strong> items ·{' '}
              <strong>{summary.equipment.available}</strong> available ·{' '}
              <strong>{summary.equipment.inUse}</strong> in use ·{' '}
              <strong>{summary.alerts.open}</strong> open alert(s)
            </p>
          ) : (
            <p className="muted">Loading…</p>
          )}
          <div className="actions">
            <Link className="btn btn-primary" to="/analytics">
              Full analytics
            </Link>
            <Link className="btn" to="/equipment">
              Manage equipment
            </Link>
            <Link className="btn" to="/calendar">
              Booking calendar
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}

export function AdminDashboard() {
  return (
    <section>
      <AnalyticsView />
    </section>
  );
}
