import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getEquipment } from '../api/equipment';
import { apiMessage } from '../api/client';
import type { Equipment } from '../api/types';
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

export default function EquipmentDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const [item, setItem] = useState<Equipment | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError('');
    try {
      setItem(await getEquipment(id));
    } catch (err) {
      setError(apiMessage(err, 'Could not load equipment'));
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
          <Row label="Registered by" value={item.createdByUsername} />
          <Row label="Updated" value={new Date(item.updatedAt).toLocaleString()} />
        </dl>
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
