import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listEquipment } from '../api/equipment';
import { apiMessage } from '../api/client';
import type { Equipment, EquipmentStatus } from '../api/types';
import Pagination from '../components/Pagination';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, SkeletonGrid } from '../components/Feedback';

const PAGE_SIZE = 12;
const STATUSES: EquipmentStatus[] = [
  'AVAILABLE',
  'RESERVED',
  'IN_USE',
  'OVERDUE',
  'MAINTENANCE',
  'SENSOR_OFFLINE'
];
const SORTS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
  { value: 'name,asc', label: 'Name A–Z' },
  { value: 'name,desc', label: 'Name Z–A' }
];

function initials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w.charAt(0).toUpperCase())
    .join('');
}

export default function EquipmentListPage() {
  const [params] = useSearchParams();
  const [items, setItems] = useState<Equipment[]>([]);
  const [q, setQ] = useState(params.get('q') ?? '');
  const [debouncedQ, setDebouncedQ] = useState(params.get('q') ?? '');
  const [status, setStatus] = useState<EquipmentStatus | ''>('');
  const [category, setCategory] = useState('');
  const [laboratory, setLaboratory] = useState('');
  const [sort, setSort] = useState(SORTS[0].value);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const timer = useRef<number | undefined>(undefined);

  // Debounced free-text search: the API is hit 400 ms after typing stops.
  useEffect(() => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      setDebouncedQ(q.trim());
      setPage(0);
    }, 400);
    return () => window.clearTimeout(timer.current);
  }, [q]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await listEquipment({
        q: debouncedQ || undefined,
        status: status || undefined,
        category: category.trim() || undefined,
        laboratory: laboratory.trim() || undefined,
        page,
        size: PAGE_SIZE,
        sort
      });
      setItems(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(apiMessage(err, 'Unable to load equipment. Please check your connection and try again.'));
    } finally {
      setLoading(false);
    }
  }, [debouncedQ, status, category, laboratory, page, sort]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section>
      <h1>Equipment marketplace</h1>
      <form
        className="filters"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          void load();
        }}
      >
        <input
          className="input"
          placeholder="Search name, code, manufacturer, lab…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="Search equipment"
        />
        <select
          className="input"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as EquipmentStatus | '');
            setPage(0);
          }}
          aria-label="Filter by status"
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
          onChange={(e) => {
            setCategory(e.target.value);
            setPage(0);
          }}
          aria-label="Filter by category"
        />
        <input
          className="input"
          placeholder="Laboratory"
          value={laboratory}
          onChange={(e) => {
            setLaboratory(e.target.value);
            setPage(0);
          }}
          aria-label="Filter by laboratory"
        />
        <select
          className="input"
          value={sort}
          onChange={(e) => {
            setSort(e.target.value);
            setPage(0);
          }}
          aria-label="Sort equipment"
        >
          {SORTS.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
      </form>

      {loading && <SkeletonGrid count={6} />}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState message="No equipment matches these filters. Try clearing the search." />
      )}

      <div className="grid">
        {items.map((e) => (
          <Link key={e.id} to={`/equipment/${e.id}`} className="card card-link">
            <div className="equip-media" aria-hidden>
              {e.imageUrl ? (
                <img
                  src={e.imageUrl}
                  alt=""
                  loading="lazy"
                  onError={(ev) => {
                    (ev.target as HTMLImageElement).style.display = 'none';
                  }}
                />
              ) : (
                <span className="equip-mono">{initials(e.name)}</span>
              )}
            </div>
            <div className="card-row">
              <strong>{e.name}</strong>
              <StatusBadge value={e.currentStatus} />
            </div>
            <div className="muted small">
              {e.equipmentCode} · {e.category}
              {e.laboratory ? ` · ${e.laboratory}` : ''}
              {e.pricePerHour != null && (
                <>
                  {' · '}
                  <span className="price">${e.pricePerHour}/h</span>
                </>
              )}
            </div>
            <div className="card-row small" style={{ marginTop: '0.5rem' }}>
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
