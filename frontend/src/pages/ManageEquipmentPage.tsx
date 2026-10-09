import { useCallback, useEffect, useState } from 'react';
import {
  createEquipment,
  deleteEquipment,
  listEquipment,
  updateEquipment
} from '../api/equipment';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import Modal from '../components/Modal';
import Pagination from '../components/Pagination';
import StatusBadge from '../components/StatusBadge';
import { EmptyState, ErrorAlert, Loading } from '../components/Feedback';
import type {
  Equipment,
  EquipmentCondition,
  EquipmentCreatePayload,
  EquipmentStatus,
  EquipmentUpdatePayload,
  MaintenanceStatus
} from '../api/types';

const CONDITIONS: EquipmentCondition[] = ['NEW', 'GOOD', 'FAIR', 'POOR', 'DAMAGED'];
const STATUSES: EquipmentStatus[] = [
  'AVAILABLE',
  'RESERVED',
  'IN_USE',
  'OVERDUE',
  'MAINTENANCE',
  'SENSOR_OFFLINE'
];
const MAINTENANCE: MaintenanceStatus[] = ['OPERATIONAL', 'DUE', 'IN_MAINTENANCE', 'OUT_OF_SERVICE'];

interface FormState {
  equipmentCode: string;
  name: string;
  category: string;
  description: string;
  manufacturer: string;
  model: string;
  laboratory: string;
  imageUrl: string;
  condition: EquipmentCondition;
  currentStatus: EquipmentStatus;
  maintenanceStatus: MaintenanceStatus;
}

const EMPTY_FORM: FormState = {
  equipmentCode: '',
  name: '',
  category: '',
  description: '',
  manufacturer: '',
  model: '',
  laboratory: '',
  imageUrl: '',
  condition: 'GOOD',
  currentStatus: 'AVAILABLE',
  maintenanceStatus: 'OPERATIONAL'
};

/** Staff/admin equipment CRUD. With `ownerOnly`, lists and manages the caller's own items (vendor view). */
export default function ManageEquipmentPage({
  ownerOnly = false,
  title = 'Manage equipment'
}: {
  ownerOnly?: boolean;
  title?: string;
}) {
  const toast = useToast();
  const [items, setItems] = useState<Equipment[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<Equipment | 'new' | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [formError, setFormError] = useState('');
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState<Equipment | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await listEquipment({ mine: ownerOnly || undefined, page, size: 15, sort: 'createdAt,desc' });
      setItems(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(apiMessage(err, 'Unable to load equipment. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, [page, ownerOnly]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setFormError('');
    setEditing('new');
  };

  const openEdit = (item: Equipment) => {
    setForm({
      equipmentCode: item.equipmentCode,
      name: item.name,
      category: item.category,
      description: item.description ?? '',
      manufacturer: item.manufacturer ?? '',
      model: item.model ?? '',
      laboratory: item.laboratory ?? '',
      imageUrl: item.imageUrl ?? '',
      condition: item.condition,
      currentStatus: item.currentStatus,
      maintenanceStatus: item.maintenanceStatus
    });
    setFormError('');
    setEditing(item);
  };

  const set = (key: keyof FormState) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const onSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.equipmentCode.trim() || !form.name.trim() || !form.category.trim()) {
      setFormError('Equipment code, name and category are required.');
      return;
    }
    setBusy(true);
    setFormError('');
    try {
      const base = {
        name: form.name.trim(),
        category: form.category.trim(),
        description: form.description.trim() || undefined,
        manufacturer: form.manufacturer.trim() || undefined,
        model: form.model.trim() || undefined,
        laboratory: form.laboratory.trim() || undefined,
        imageUrl: form.imageUrl.trim() || undefined,
        condition: form.condition,
        currentStatus: form.currentStatus,
        maintenanceStatus: form.maintenanceStatus
      };
      if (editing === 'new') {
        const payload: EquipmentCreatePayload = {
          equipmentCode: form.equipmentCode.trim(),
          ...base
        };
        const created = await createEquipment(payload);
        toast.success(`Equipment ${created.equipmentCode} created.`);
      } else if (editing) {
        const payload: EquipmentUpdatePayload = base;
        await updateEquipment(editing.id, payload);
        toast.success(`Equipment ${editing.equipmentCode} updated.`);
      }
      setEditing(null);
      await load();
    } catch (err) {
      setFormError(apiMessage(err, 'Save failed. Please check the values and try again.'));
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    if (!deleting) return;
    setBusy(true);
    try {
      await deleteEquipment(deleting.id);
      toast.success(`Equipment ${deleting.equipmentCode} deleted.`);
      setDeleting(null);
      await load();
    } catch (err) {
      toast.error(apiMessage(err, 'Delete failed — items with bookings or active use cannot be removed.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <div className="card-row">
        <h1>{title}</h1>
        <button className="btn btn-primary" onClick={openCreate}>
          + Add equipment
        </button>
      </div>
      {loading && <Loading label="Loading equipment…" />}
      {error && <ErrorAlert message={error} onRetry={load} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState message="No equipment yet. Add the first instrument." />
      )}
      {!loading && !error && items.length > 0 && (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Category</th>
                <th>Laboratory</th>
                <th>Status</th>
                <th>Condition</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <td>{item.equipmentCode}</td>
                  <td>{item.name}</td>
                  <td>{item.category}</td>
                  <td>{item.laboratory ?? <span className="muted">—</span>}</td>
                  <td>
                    <StatusBadge value={item.currentStatus} />
                  </td>
                  <td>
                    <StatusBadge value={item.condition} />
                  </td>
                  <td>
                    <button className="btn btn-small" onClick={() => openEdit(item)}>
                      Edit
                    </button>{' '}
                    <button className="btn btn-small btn-danger" onClick={() => setDeleting(item)}>
                      Delete
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
        <Modal
          title={editing === 'new' ? 'Add equipment' : `Edit ${editing.equipmentCode}`}
          onClose={() => setEditing(null)}
          wide
        >
          {formError && <ErrorAlert message={formError} />}
          <form onSubmit={onSave} noValidate>
            <label className="field">
              Equipment code (immutable once created)
              <input
                value={form.equipmentCode}
                onChange={set('equipmentCode')}
                disabled={editing !== 'new'}
                placeholder="e.g. OSC-2026-014"
              />
            </label>
            <label className="field">
              Name
              <input value={form.name} onChange={set('name')} />
            </label>
            <label className="field">
              Category
              <input value={form.category} onChange={set('category')} />
            </label>
            <label className="field">
              Description
              <textarea rows={2} value={form.description} onChange={set('description')} />
            </label>
            <label className="field">
              Manufacturer
              <input value={form.manufacturer} onChange={set('manufacturer')} />
            </label>
            <label className="field">
              Model
              <input value={form.model} onChange={set('model')} />
            </label>
            <label className="field">
              Laboratory
              <input value={form.laboratory} onChange={set('laboratory')} />
            </label>
            <label className="field">
              Photo URL (optional)
              <input
                value={form.imageUrl}
                onChange={set('imageUrl')}
                placeholder="https://…"
              />
            </label>
            <label className="field">
              Condition
              <select value={form.condition} onChange={set('condition')}>
                {CONDITIONS.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Current status
              <select value={form.currentStatus} onChange={set('currentStatus')}>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Maintenance status
              <select value={form.maintenanceStatus} onChange={set('maintenanceStatus')}>
                {MAINTENANCE.map((m) => (
                  <option key={m} value={m}>
                    {m.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
            </label>
            <div className="actions">
              <button className="btn btn-primary" disabled={busy}>
                {busy ? 'Saving…' : 'Save'}
              </button>
              <button type="button" className="btn" disabled={busy} onClick={() => setEditing(null)}>
                Cancel
              </button>
            </div>
          </form>
        </Modal>
      )}

      {deleting && (
        <Modal title={`Delete ${deleting.equipmentCode}?`} onClose={() => setDeleting(null)}>
          <p>
            <strong>{deleting.name}</strong> will be removed. Items with bookings or
            active use cannot be deleted.
          </p>
          <div className="actions">
            <button className="btn btn-danger" disabled={busy} onClick={doDelete}>
              {busy ? 'Deleting…' : 'Yes, delete it'}
            </button>
            <button className="btn" disabled={busy} onClick={() => setDeleting(null)}>
              Keep it
            </button>
          </div>
        </Modal>
      )}
    </section>
  );
}
