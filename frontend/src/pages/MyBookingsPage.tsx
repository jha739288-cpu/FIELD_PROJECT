import { useCallback, useEffect, useState } from 'react';
import { myBookings, cancelBooking } from '../api/bookings';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import Modal from '../components/Modal';
import Pagination from '../components/Pagination';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, SkeletonCard } from '../components/Feedback';
import type { Booking, BookingStatus } from '../api/types';

const TABS: Array<{ value: BookingStatus | ''; label: string }> = [
  { value: '', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'CHECKED_IN', label: 'Checked in' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'OVERDUE', label: 'Overdue' }
];

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

/** The student's own reservations with search-by-status and cancellation. */
export default function MyBookingsPage() {
  const toast = useToast();
  const [status, setStatus] = useState<BookingStatus | ''>('');
  const [items, setItems] = useState<Booking[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cancelling, setCancelling] = useState<Booking | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await myBookings(status || undefined, page, 12);
      setItems(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(apiMessage(err, 'Unable to load bookings. Please check your connection and try again.'));
    } finally {
      setLoading(false);
    }
  }, [status, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const doCancel = async () => {
    if (!cancelling) return;
    setBusy(true);
    try {
      await cancelBooking(cancelling.id);
      toast.success(`Booking #${cancelling.id} cancelled.`);
      setCancelling(null);
      await load();
    } catch (err) {
      toast.error(apiMessage(err, 'Cancellation failed.'));
    } finally {
      setBusy(false);
    }
  };

  const cancellable = (b: Booking) => b.status === 'PENDING' || b.status === 'CONFIRMED';

  return (
    <section>
      <h1>My bookings</h1>
      <div className="tabs" role="tablist" aria-label="Filter by status">
        {TABS.map((t) => (
          <button
            key={t.label}
            role="tab"
            aria-selected={status === t.value}
            className={status === t.value ? 'active' : undefined}
            onClick={() => {
              setStatus(t.value);
              setPage(0);
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {loading && (
        <div className="grid">
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
        </div>
      )}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState message="No bookings here yet. Browse the marketplace to reserve equipment." />
      )}

      <div className="grid">
        {items.map((b) => (
          <div key={b.id} className="card">
            <div className="card-row">
              <strong>
                #{b.id} · {b.equipmentName}
              </strong>
              <StatusBadge value={b.status} />
            </div>
            <div className="muted small">
              {b.equipmentCode} · {fmt(b.startTime)} → {fmt(b.endTime)}
            </div>
            <p className="small">{b.purpose}</p>
            {cancellable(b) && (
              <div className="actions">
                <button className="btn btn-small btn-danger" onClick={() => setCancelling(b)}>
                  Cancel booking
                </button>
              </div>
            )}
          </div>
        ))}
      </div>

      {!loading && !error && (
        <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPage={setPage} />
      )}

      {cancelling && (
        <Modal title={`Cancel booking #${cancelling.id}?`} onClose={() => setCancelling(null)}>
          <p>
            This releases <strong>{cancelling.equipmentName}</strong> (
            {fmt(cancelling.startTime)} → {fmt(cancelling.endTime)}). This cannot be undone.
          </p>
          <div className="actions">
            <button className="btn btn-danger" disabled={busy} onClick={doCancel}>
              {busy ? 'Cancelling…' : 'Yes, cancel it'}
            </button>
            <button className="btn" disabled={busy} onClick={() => setCancelling(null)}>
              Keep it
            </button>
          </div>
        </Modal>
      )}
    </section>
  );
}
