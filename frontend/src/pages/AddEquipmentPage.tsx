import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { createEquipment } from '../api/equipment';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import { ErrorAlert } from '../components/Feedback';
import type {
  EquipmentCondition,
  EquipmentStatus,
  MaintenanceStatus
} from '../api/types';

const CATEGORIES = [
  'Microscope', 'Centrifuge', 'Spectrophotometer', 'PCR Machine', 'Autoclave',
  'Incubator', 'Analytical Balance', 'pH Meter', 'Laboratory Oven', 'Shaker',
  'Electrophoresis Unit', 'Thermal Cycler', 'Oscilloscope', 'Other'
];
const CONDITIONS: EquipmentCondition[] = ['NEW', 'GOOD', 'FAIR', 'POOR', 'DAMAGED'];
const STATUSES: EquipmentStatus[] = [
  'AVAILABLE', 'RESERVED', 'IN_USE', 'OVERDUE', 'MAINTENANCE', 'SENSOR_OFFLINE'
];
const MAINTENANCE: MaintenanceStatus[] = ['OPERATIONAL', 'DUE', 'IN_MAINTENANCE', 'OUT_OF_SERVICE'];

/** Vendor listing form: validates locally, creates the real database record. */
export default function AddEquipmentPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [form, setForm] = useState({
    equipmentCode: '',
    name: '',
    category: 'Microscope',
    customCategory: '',
    description: '',
    manufacturer: '',
    model: '',
    laboratory: '',
    imageUrl: '',
    specifications: '',
    pricePerHour: '',
    quantity: '1',
    usageInstructions: '',
    safetyInfo: '',
    condition: 'GOOD' as EquipmentCondition,
    currentStatus: 'AVAILABLE' as EquipmentStatus,
    maintenanceStatus: 'OPERATIONAL' as MaintenanceStatus
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (key: keyof typeof form) => (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const validate = () => {
    const next: Record<string, string> = {};
    if (!/^[A-Za-z0-9-]+$/.test(form.equipmentCode.trim()))
      next.equipmentCode = 'Code may contain letters, digits and hyphens only';
    else if (form.equipmentCode.trim().length > 50) next.equipmentCode = 'At most 50 characters';
    if (!form.name.trim()) next.name = 'Name is required';
    else if (form.name.trim().length > 150) next.name = 'At most 150 characters';
    if (form.category === 'Other' && !form.customCategory.trim())
      next.category = 'Describe the custom category';
    if (form.pricePerHour && (Number.isNaN(Number(form.pricePerHour)) || Number(form.pricePerHour) < 0))
      next.pricePerHour = 'Price must be a number ≥ 0';
    if (!/^\d+$/.test(form.quantity) || Number(form.quantity) < 1)
      next.quantity = 'Quantity must be a whole number ≥ 1';
    if (form.description.length > 1000) next.description = 'At most 1000 characters';
    if (form.specifications.length > 2000) next.specifications = 'At most 2000 characters';
    if (form.usageInstructions.length > 1000) next.usageInstructions = 'At most 1000 characters';
    if (form.safetyInfo.length > 1000) next.safetyInfo = 'At most 1000 characters';
    if (form.imageUrl && form.imageUrl.length > 500) next.imageUrl = 'At most 500 characters';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setServerError('');
    if (!validate()) return;
    setBusy(true);
    try {
      await createEquipment({
        equipmentCode: form.equipmentCode.trim(),
        name: form.name.trim(),
        category: form.category === 'Other' ? form.customCategory.trim() : form.category,
        description: form.description.trim() || undefined,
        manufacturer: form.manufacturer.trim() || undefined,
        model: form.model.trim() || undefined,
        laboratory: form.laboratory.trim() || undefined,
        imageUrl: form.imageUrl.trim() || undefined,
        specifications: form.specifications.trim() || undefined,
        pricePerHour: form.pricePerHour === '' ? null : Number(form.pricePerHour),
        quantity: Number(form.quantity),
        usageInstructions: form.usageInstructions.trim() || undefined,
        safetyInfo: form.safetyInfo.trim() || undefined,
        condition: form.condition,
        currentStatus: form.currentStatus,
        maintenanceStatus: form.maintenanceStatus
      });
      toast.success('Equipment added successfully.');
      navigate('/vendor/equipment', { replace: true });
    } catch (err) {
      setServerError(apiMessage(err, 'Could not add equipment. Please try again.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <h1>Add equipment</h1>
      <p className="muted">
        Listed instruments appear in the marketplace immediately after creation.
      </p>
      {serverError && <ErrorAlert message={serverError} />}
      <form className="card" onSubmit={onSubmit} noValidate>
        <label className="field">
          Equipment code (immutable once created)
          <input value={form.equipmentCode} onChange={set('equipmentCode')} placeholder="e.g. MICRO-2026-001" />
          {errors.equipmentCode && <span className="field-error">{errors.equipmentCode}</span>}
        </label>
        <label className="field">
          Equipment name
          <input value={form.name} onChange={set('name')} placeholder="e.g. Digital Microscope" />
          {errors.name && <span className="field-error">{errors.name}</span>}
        </label>
        <label className="field">
          Category
          <select value={form.category} onChange={set('category')}>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          {errors.category && <span className="field-error">{errors.category}</span>}
        </label>
        {form.category === 'Other' && (
          <label className="field">
            Custom category
            <input value={form.customCategory} onChange={set('customCategory')} />
          </label>
        )}
        <label className="field">
          Description
          <textarea rows={3} value={form.description} onChange={set('description')} />
          {errors.description && <span className="field-error">{errors.description}</span>}
        </label>
        <label className="field">
          Manufacturer
          <input value={form.manufacturer} onChange={set('manufacturer')} />
        </label>
        <label className="field">
          Model number
          <input value={form.model} onChange={set('model')} />
        </label>
        <label className="field">
          Location (laboratory)
          <input value={form.laboratory} onChange={set('laboratory')} placeholder="e.g. Mumbai Laboratory" />
        </label>
        <label className="field">
          Image URL (optional)
          <input value={form.imageUrl} onChange={set('imageUrl')} placeholder="https://…" />
          {errors.imageUrl && <span className="field-error">{errors.imageUrl}</span>}
        </label>
        <label className="field">
          Specifications
          <textarea
            rows={3}
            value={form.specifications}
            onChange={set('specifications')}
            placeholder="Range, accuracy, capacity…"
          />
          {errors.specifications && <span className="field-error">{errors.specifications}</span>}
        </label>
        <label className="field">
          Price per hour (optional — leave blank for “contact vendor”)
          <input
            value={form.pricePerHour}
            onChange={set('pricePerHour')}
            inputMode="decimal"
            placeholder="e.g. 12.50"
          />
          {errors.pricePerHour && <span className="field-error">{errors.pricePerHour}</span>}
        </label>
        <label className="field">
          Quantity
          <input value={form.quantity} onChange={set('quantity')} inputMode="numeric" />
          {errors.quantity && <span className="field-error">{errors.quantity}</span>}
        </label>
        <label className="field">
          Usage instructions
          <textarea rows={2} value={form.usageInstructions} onChange={set('usageInstructions')} />
          {errors.usageInstructions && <span className="field-error">{errors.usageInstructions}</span>}
        </label>
        <label className="field">
          Safety information
          <textarea rows={2} value={form.safetyInfo} onChange={set('safetyInfo')} />
          {errors.safetyInfo && <span className="field-error">{errors.safetyInfo}</span>}
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
          Availability status
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
            {busy ? 'Adding…' : 'Add equipment'}
          </button>
        </div>
      </form>
    </section>
  );
}
