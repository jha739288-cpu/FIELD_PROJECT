import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { openOverdueAlerts, resolveAlert } from '../api/alerts';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import Modal from '../components/Modal';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';
import type { Alert } from '../api/types';

/** Staff/admin alert inbox: open overdue alerts with one-click resolution. */
export default function AlertsPanel() {
  const toast = useToast();
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [resolving, setResolving] = useState<Alert | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await openOverdueAlerts(0, 20);
      setAlerts(res.content);
    } catch (err) {
      setError(apiMessage(err, 'Unable to load alerts. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const doResolve = async () => {
    if (!resolving) return;
    setBusy(true);
    try {
      await resolveAlert(resolving.id, note.trim() || undefined);
      toast.success(`Alert #${resolving.id} resolved.`);
      setResolving(null);
      setNote('');
      await load();
    } catch (err) {
      toast.error(apiMessage(err, 'Resolution failed.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card">
      <div className="card-row">
        <h2>Open overdue alerts ({alerts.length})</h2>
        <Link className="btn btn-small" to="/analytics">
          Analytics
        </Link>
      </div>
      {loading && <Loading label="Loading alerts…" />}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && alerts.length === 0 && (
        <EmptyState message="No open alerts. The lab is running clean." />
      )}
      {!loading && !error && alerts.length > 0 && (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Alert</th>
                <th>Equipment</th>
                <th>Message</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((a) => (
                <tr key={a.id}>
                  <td>
                    #{a.id} <StatusBadge value={a.status} />
                  </td>
                  <td>
                    <Link to={`/equipment/${a.equipmentId}`}>{a.equipmentCode}</Link>
                  </td>
                  <td style={{ whiteSpace: 'normal' }}>{a.message}</td>
                  <td>
                    <button className="btn btn-small btn-primary" onClick={() => setResolving(a)}>
                      Resolve
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {resolving && (
        <Modal title={`Resolve alert #${resolving.id}`} onClose={() => setResolving(null)}>
          <p className="muted small">{resolving.message}</p>
          <label className="field">
            Resolution note (optional)
            <textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
          </label>
          <div className="actions">
            <button className="btn btn-primary" disabled={busy} onClick={doResolve}>
              {busy ? 'Resolving…' : 'Resolve alert'}
            </button>
            <button className="btn" disabled={busy} onClick={() => setResolving(null)}>
              Cancel
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
