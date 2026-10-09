import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listEquipment } from '../api/equipment';
import { apiMessage } from '../api/client';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';
import type { Equipment } from '../api/types';

/** Predictions entry: pick an instrument, open its forecast. */
export default function PredictionsPage() {
  const [items, setItems] = useState<Equipment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await listEquipment({ page: 0, size: 50, sort: 'name,asc' });
      setItems(res.content);
    } catch (err) {
      setError(apiMessage(err, 'Unable to load equipment. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section>
      <h1>Predictive availability</h1>
      <p className="muted">
        Forecasts are advisory only — confirmed bookings and maintenance always win.
        Choose an instrument to see its forecast.
      </p>
      {loading && <Loading label="Loading equipment…" />}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState message="No equipment in the catalog yet." />
      )}
      <div className="grid">
        {items.map((e) => (
          <div key={e.id} className="card">
            <div className="card-row">
              <strong>{e.name}</strong>
              <StatusBadge value={e.currentStatus} />
            </div>
            <div className="muted small">
              {e.equipmentCode} · {e.category}
              {e.laboratory ? ` · ${e.laboratory}` : ''}
            </div>
            <div className="actions">
              <Link className="btn btn-primary btn-small" to={`/equipment/${e.id}/predict`}>
                View forecast
              </Link>
              <Link className="btn btn-small" to={`/equipment/${e.id}`}>
                Details
              </Link>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
