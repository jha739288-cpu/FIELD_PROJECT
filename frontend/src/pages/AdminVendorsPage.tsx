import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listVendors, setUserEnabled } from '../api/users';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import Pagination from '../components/Pagination';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';
import type { VendorSummary } from '../api/types';

/** Admin vendor management: activity per vendor with activation control. */
export default function AdminVendorsPage() {
  const toast = useToast();
  const [q, setQ] = useState('');
  const [items, setItems] = useState<VendorSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await listVendors(q.trim() || undefined, page, 15);
      setItems(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(apiMessage(err, 'Unable to load vendors. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, [q, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const toggleEnabled = async (v: VendorSummary) => {
    setBusy(true);
    try {
      await setUserEnabled(v.id, !v.enabled);
      toast.success(`Vendor ${v.username} ${v.enabled ? 'disabled' : 'enabled'}.`);
      await load();
    } catch (err) {
      toast.error(apiMessage(err, 'Could not change vendor status.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <h1>Vendors</h1>
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
          placeholder="Search vendor name or email…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="Search vendors"
        />
        <button className="btn btn-primary" type="submit">
          Search
        </button>
      </form>

      {loading && <Loading label="Loading vendors…" />}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState message="No vendor accounts yet. Vendors register with the VENDOR role." />
      )}
      {!loading && !error && items.length > 0 && (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Vendor</th>
                <th>Email</th>
                <th>Equipment</th>
                <th>Bookings</th>
                <th>Status</th>
                <th>Registered</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((v) => (
                <tr key={v.id}>
                  <td>{v.fullName || v.username}</td>
                  <td>{v.email}</td>
                  <td>{v.equipmentCount}</td>
                  <td>{v.bookingCount}</td>
                  <td>
                    <StatusBadge value={v.enabled ? 'ACTIVE' : 'DISABLED'} />
                  </td>
                  <td>{new Date(v.createdAt).toLocaleDateString()}</td>
                  <td>
                    <button
                      className="btn btn-small btn-danger"
                      disabled={busy}
                      onClick={() => toggleEnabled(v)}
                    >
                      {v.enabled ? 'Disable' : 'Enable'}
                    </button>{' '}
                    <Link className="btn btn-small" to={`/admin/users?q=${encodeURIComponent(v.username)}`}>
                      Account
                    </Link>
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
