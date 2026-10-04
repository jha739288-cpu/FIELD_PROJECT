import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listEquipment } from '../api/equipment';
import { apiMessage } from '../api/client';
import type { Equipment, EquipmentStatus } from '../api/types';
import Pagination from '../components/Pagination';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';

const PAGE_SIZE = 12;
const STATUSES: EquipmentStatus[] = [
  'AVAILABLE',
  'RESERVED',
  'IN_USE',
  'OVERDUE',
  'MAINTENANCE',
  'SENSOR_OFFLINE'
];

export default function EquipmentListPage() {
  const [items, setItems] = useState<Equipment[]>([]);
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<EquipmentStatus | ''>('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await listEquipment({
        q: q.trim() || undefined,
        status: status || undefined,
        category: category.trim() || undefined,
        page,
        size: PAGE_SIZE
      });
      setItems(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(apiMessage(err, 'Could not load equipment'));
    } finally {
      setLoading(false);
    }
  }, [q, status, category, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const applyFilters = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    void load();
  };

  return (
    <section>
      <h1>Equipment catalog</h1>
      <form className="filters" onSubmit={applyFilters}>
        <input
          className="input"
          placeholder="Search name, code, manufacturer…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select
          className="input"
          value={status}
          onChange={(e) => setStatus(e.target.value as EquipmentStatus | '')}
        >
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s.replace(/_/g, ' ')}
            </option>
          ))}
        </select>
        <input
          className="input"
          placeholder="Category"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
        />
        <button className="btn btn-primary" type="submit">
          Search
        </button>
      </form>

      {loading && <Loading label="Loading equipment…" />}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState message="No equipment found for these filters." />
      )}

      <div className="grid">
        {items.map((e) => (
          <Link key={e.id} to={`/equipment/${e.id}`} className="card card-link">
            <div className="card-row">
              <strong>{e.name}</strong>
              <StatusBadge value={e.currentStatus} />
            </div>
            <div className="muted small">
              {e.equipmentCode} · {e.category}
              {e.laboratory ? ` · ${e.laboratory}` : ''}
            </div>
            <div className="card-row small">
              <StatusBadge value={e.condition} />
              <StatusBadge value={e.maintenanceStatus} />
            </div>
          </Link>
        ))}
      </div>

      {!loading && !error && (
        <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPage={setPage} />
      )}
    </section>
  );
}
