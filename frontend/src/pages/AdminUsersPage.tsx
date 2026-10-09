import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { listUsers, setUserEnabled, setUserRoles } from '../api/users';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import Modal from '../components/Modal';
import Pagination from '../components/Pagination';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';
import type { Role, UserAdmin } from '../api/types';

const ROLES: Role[] = ['ADMIN', 'USER', 'VENDOR'];

/** Admin user management: search, filter, activate/deactivate, re-role. */
export default function AdminUsersPage() {
  const toast = useToast();
  const [params] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const [role, setRole] = useState<Role | ''>('');
  const [enabled, setEnabled] = useState<'' | 'true' | 'false'>('');
  const [items, setItems] = useState<UserAdmin[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<UserAdmin | null>(null);
  const [editRoles, setEditRoles] = useState<Role[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await listUsers({
        q: q.trim() || undefined,
        role: role || undefined,
        enabled: enabled === '' ? undefined : enabled === 'true',
        page,
        size: 15
      });
      setItems(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(apiMessage(err, 'Unable to load users. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, [q, role, enabled, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const toggleEnabled = async (u: UserAdmin) => {
    setBusy(true);
    try {
      await setUserEnabled(u.id, !u.enabled);
      toast.success(`Account ${u.username} ${u.enabled ? 'disabled' : 'enabled'}.`);
      await load();
    } catch (err) {
      toast.error(apiMessage(err, 'Could not change account status.'));
    } finally {
      setBusy(false);
    }
  };

  const toggleRole = (r: Role) => {
    setEditRoles((prev) => (prev.includes(r) ? prev.filter((x) => x !== r) : [...prev, r]));
  };

  const saveRoles = async () => {
    if (!editing || editRoles.length === 0) return;
    setBusy(true);
    try {
      await setUserRoles(editing.id, editRoles);
      toast.success(`Roles of ${editing.username} updated.`);
      setEditing(null);
      await load();
    } catch (err) {
      toast.error(apiMessage(err, 'Could not update roles.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <h1>Users</h1>
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
          placeholder="Search username or email…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="Search users"
        />
        <select
          className="input"
          value={role}
          onChange={(e) => {
            setRole(e.target.value as Role | '');
            setPage(0);
          }}
          aria-label="Filter by role"
        >
          <option value="">All roles</option>
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </select>
        <select
          className="input"
          value={enabled}
          onChange={(e) => {
            setEnabled(e.target.value as '' | 'true' | 'false');
            setPage(0);
          }}
          aria-label="Filter by status"
        >
          <option value="">Active + disabled</option>
          <option value="true">Active</option>
          <option value="false">Disabled</option>
        </select>
        <button className="btn btn-primary" type="submit">
          Search
        </button>
      </form>

      {loading && <Loading label="Loading users…" />}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && items.length === 0 && <EmptyState message="No accounts match." />}
      {!loading && !error && items.length > 0 && (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>ID</th>
                <th>Username</th>
                <th>Email</th>
                <th>Roles</th>
                <th>Status</th>
                <th>Bookings</th>
                <th>Registered</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((u) => (
                <tr key={u.id}>
                  <td>{u.id}</td>
                  <td>{u.username}</td>
                  <td>{u.email}</td>
                  <td>{u.roles.join(', ')}</td>
                  <td>
                    <StatusBadge value={u.enabled ? 'ACTIVE' : 'DISABLED'} />
                  </td>
                  <td>{u.bookingCount}</td>
                  <td>{new Date(u.createdAt).toLocaleDateString()}</td>
                  <td>
                    <button
                      className="btn btn-small"
                      disabled={busy}
                      onClick={() => {
                        setEditing(u);
                        setEditRoles([...u.roles]);
                      }}
                    >
                      Roles
                    </button>{' '}
                    <button
                      className="btn btn-small btn-danger"
                      disabled={busy}
                      onClick={() => toggleEnabled(u)}
                    >
                      {u.enabled ? 'Disable' : 'Enable'}
                    </button>
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

      {editing && (
        <Modal title={`Roles of ${editing.username}`} onClose={() => setEditing(null)}>
          <p className="muted small">
            Replacing roles takes effect on next login (current tokens keep old roles until expiry).
            You cannot change your own account.
          </p>
          {ROLES.map((r) => (
            <label key={r} className="checkbox-row">
              <input type="checkbox" checked={editRoles.includes(r)} onChange={() => toggleRole(r)} />
              {r}
            </label>
          ))}
          <div className="actions">
            <button className="btn btn-primary" disabled={busy || editRoles.length === 0} onClick={saveRoles}>
              {busy ? 'Saving…' : 'Save roles'}
            </button>
            <button className="btn" disabled={busy} onClick={() => setEditing(null)}>
              Cancel
            </button>
          </div>
        </Modal>
      )}
    </section>
  );
}
