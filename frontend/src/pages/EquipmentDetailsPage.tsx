import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getEquipment } from '../api/equipment';
import { getEquipmentDashboard } from '../api/dashboard';
import { apiMessage } from '../api/client';
import type { Equipment, EquipmentDashboard } from '../api/types';
import StatusBadge from '../components/StatusBadge';
import PredictionAdvisory from '../components/PredictionAdvisory';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="detail-row">
      <dt>{label}</dt>
      <dd>{value ?? <span className="muted">—</span>}</dd>
    </div>
  );
}

function initials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w.charAt(0).toUpperCase())
    .join('');
}

export default function EquipmentDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const [item, setItem] = useState<Equipment | null>(null);
  const [stats, setStats] = useState<EquipmentDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError('');
    try {
      const equipment = await getEquipment(id);
      setItem(equipment);
      setStats(null);
      try {
        // Analytics are VENDOR/ADMIN-only (403 for USER) — stats must never
        // block the details view or the booking action.
        setStats(await getEquipmentDashboard(id));
      } catch {
        setStats(null);
      }
    } catch (err) {
      setError(apiMessage(err, 'Unable to load equipment. Please check your connection and try again.'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) return <Loading label="Loading equipment…" />;
  if (error) return <ErrorAlert message={error} onRetry={load} />;
  if (!item) return <EmptyState message="Equipment not found." />;

  return (
    <section>
      <p>
        <Link to="/equipment">← Back to catalog</Link>
      </p>
      <div className="card">
        <div className="equip-media" aria-hidden style={{ height: 220 }}>
          {item.imageUrl ? (
            <img
              src={item.imageUrl}
              alt=""
              onError={(ev) => {
                (ev.target as HTMLImageElement).style.display = 'none';
              }}
            />
          ) : (
            <span className="equip-mono">{initials(item.name)}</span>
          )}
        </div>
        <div className="card-row">
          <h1>
            {item.name} <span className="muted small">{item.equipmentCode}</span>
          </h1>
          <StatusBadge value={item.currentStatus} />
        </div>
        {item.description && <p>{item.description}</p>}
        <dl className="details">
          <Row label="Category" value={item.category} />
          <Row label="Manufacturer" value={item.manufacturer} />
          <Row label="Model" value={item.model} />
          <Row label="Laboratory" value={item.laboratory} />
          <Row label="Condition" value={<StatusBadge value={item.condition} />} />
          <Row label="Maintenance" value={<StatusBadge value={item.maintenanceStatus} />} />
          {item.pricePerHour != null && (
            <Row label="Price" value={`$${item.pricePerHour} / hour`} />
          )}
          {item.quantity != null && <Row label="Quantity" value={item.quantity} />}
          <Row label="Registered by" value={item.createdByUsername} />
          <Row label="Updated" value={new Date(item.updatedAt).toLocaleString()} />
        </dl>
        {item.specifications && (
          <>
            <h2>Specifications</h2>
            <p>{item.specifications}</p>
          </>
        )}
        {item.usageInstructions && (
          <>
            <h2>Usage instructions</h2>
            <p>{item.usageInstructions}</p>
          </>
        )}
        {item.safetyInfo && (
          <>
            <h2>Safety information</h2>
            <p>{item.safetyInfo}</p>
          </>
        )}
        {stats && (
          <div className="stats">
            <div className="stat">
              <div className="stat-value">{stats.utilizationPercent}%</div>
              <div className="stat-label">Utilization (30d)</div>
            </div>
            <div className="stat">
              <div className="stat-value">{Math.round(stats.usageSeconds / 36) / 100}h</div>
              <div className="stat-label">Usage time</div>
            </div>
            <div className="stat">
              <div className="stat-value">{stats.completedSessions}</div>
              <div className="stat-label">Completed sessions</div>
            </div>
          </div>
        )}
        <div className="actions">
          <Link className="btn btn-primary" to={`/book?equipmentId=${item.id}`}>
            Book this equipment
          </Link>
          <Link className="btn" to={`/equipment/${item.id}/predict`}>
            Predict availability
          </Link>
          <Link className="btn" to={`/calendar?equipmentId=${item.id}`}>
            View calendar
          </Link>
        </div>
        <PredictionAdvisory equipmentId={item.id} />
      </div>
    </section>
  );
}
