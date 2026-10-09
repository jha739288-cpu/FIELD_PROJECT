import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { cancelBooking, confirmBooking, listBookings, rejectBooking } from '../api/bookings';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import Pagination from '../components/Pagination';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';
import type { Booking, BookingStatus } from '../api/types';

const TABS: Array<{ value: BookingStatus | ''; label: string }> = [
  { value: '', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'CHECKED_IN', label: 'Checked in' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' }
];

function fmt(dt: string): string {
  return new Date(dt).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });
}

/** Staff/admin view of every booking with confirm/reject/cancel actions. */
export default function ManageBookingsPage() {
  const toast = useToast();
  const [status, setStatus] = useState<BookingStatus | ''>('PENDING');
  const [items, setItems] = useState<Booking[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await listBookings({ status: status || undefined, page, size: 15 });
      setItems(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(apiMessage(err, 'Unable to load bookings. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, [status, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const act = async (id: number, action: 'confirm' | 'reject' | 'cancel', label: string) => {
    setBusyId(id);
    try {
      if (action === 'confirm') await confirmBooking(id);
      else if (action === 'reject') await rejectBooking(id);
      else await cancelBooking(id);
      toast.success(`Booking #${id} ${label}.`);
      await load();
    } catch (err) {
      toast.error(apiMessage(err, `Could not ${label} booking #${id}.`));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section>
      <h1>Manage bookings</h1>
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

      {loading && <Loading label="Loading bookings…" />}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState message="No bookings with this status." />
      )}
      {!loading && !error && items.length > 0 && (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>ID</th>
                <th>Equipment</th>
                <th>User</th>
                <th>Slot</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((b) => (
                <tr key={b.id}>
                  <td>#{b.id}</td>
                  <td>
                    <Link to={`/equipment/${b.equipmentId}`}>{b.equipmentName}</Link>
                    <div className="muted small">{b.equipmentCode}</div>
                  </td>
                  <td>{b.username}</td>
                  <td>
                    {fmt(b.startTime)} → {fmt(b.endTime)}
                  </td>
                  <td>
                    <StatusBadge value={b.status} />
                  </td>
                  <td>
                    {b.status === 'PENDING' && (
                      <>
                        <button
                          className="btn btn-small btn-primary"
                          disabled={busyId === b.id}
                          onClick={() => act(b.id, 'confirm', 'confirmed')}
                        >
                          Confirm
                        </button>{' '}
                        <button
                          className="btn btn-small"
                          disabled={busyId === b.id}
                          onClick={() => act(b.id, 'reject', 'rejected')}
                        >
                          Reject
                        </button>
                      </>
                    )}
                    {(b.status === 'PENDING' || b.status === 'CONFIRMED') && (
                      <>
                        {' '}
                        <button
                          className="btn btn-small btn-danger"
                          disabled={busyId === b.id}
                          onClick={() => act(b.id, 'cancel', 'cancelled')}
                        >
                          Cancel
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {!loading && !error && (
        <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPage={setPage} />
      )}
    </section>
  );
}
