import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { createBooking, equipmentAvailability } from '../api/bookings';
import { getEquipment, listEquipment } from '../api/equipment';
import { apiMessage } from '../api/client';
import { useToast } from '../components/Toast';
import Modal from '../components/Modal';
import StatusBadge from '../components/StatusBadge';
import { ErrorAlert } from '../components/Feedback';
import type { Availability, Booking, Equipment } from '../api/types';

function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}

function fmtRange(start: string, end: string): string {
  const s = new Date(start);
  const e = new Date(end);
  const day = s.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
  const t = (d: Date) => d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', hour12: false });
  return `${day} ${t(s)}–${t(e)}`;
}

/** Real booking form: equipment, slot and purpose → server-validated reservation. */
export default function BookingPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [equipmentId, setEquipmentId] = useState(params.get('equipmentId') ?? '');
  const [options, setOptions] = useState<Equipment[]>([]);
  const [item, setItem] = useState<Equipment | null>(null);
  const [start, setStart] = useState(() => toLocalInput(new Date(Date.now() + 3_600_000).toISOString()));
  const [end, setEnd] = useState(() => toLocalInput(new Date(Date.now() + 7_200_000).toISOString()));
  const [purpose, setPurpose] = useState('');
  const [availability, setAvailability] = useState<Availability | null>(null);
  const [checking, setChecking] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [created, setCreated] = useState<Booking | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    listEquipment({ page: 0, size: 100, sort: 'name,asc' })
      .then((res) => setOptions(res.content))
      .catch(() => setOptions([]));
  }, []);

  useEffect(() => {
    if (!equipmentId) {
      setItem(null);
      return;
    }
    getEquipment(equipmentId)
      .then(setItem)
      .catch(() => setItem(null));
  }, [equipmentId]);

  const checkAvailability = useCallback(async () => {
    if (!equipmentId || !start || !end) return;
    setChecking(true);
    try {
      const dayStart = new Date(start);
      dayStart.setHours(0, 0, 0, 0);
      const dayEnd = new Date(dayStart.getTime() + 86_400_000);
      setAvailability(
        await equipmentAvailability(equipmentId, dayStart.toISOString(), dayEnd.toISOString())
      );
    } catch {
      setAvailability(null);
    } finally {
      setChecking(false);
    }
  }, [equipmentId, start, end]);

  useEffect(() => {
    void checkAvailability();
  }, [checkAvailability]);

  const validate = () => {
    const next: Record<string, string> = {};
    if (!equipmentId) next.equipment = 'Choose equipment';
    if (!start) next.start = 'Start time is required';
    if (!end) next.end = 'End time is required';
    if (start && end && new Date(end) <= new Date(start))
      next.end = 'End time must be after start time';
    if (start && new Date(start) <= new Date()) next.start = 'Start time must be in the future';
    if (!purpose.trim()) next.purpose = 'Purpose is required';
    else if (purpose.trim().length > 500) next.purpose = 'Purpose must be at most 500 characters';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;
    setBusy(true);
    try {
      const booking = await createBooking({
        equipmentId: Number(equipmentId),
        startTime: fromLocalInput(start),
        endTime: fromLocalInput(end),
        purpose: purpose.trim()
      });
      setCreated(booking);
      toast.success(`Booking #${booking.id} created — pending staff confirmation.`);
    } catch (err) {
      const msg = apiMessage(err, 'Booking failed. Please try again.');
      setError(msg);
      toast.error(msg);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <h1>Book equipment</h1>
      {error && <ErrorAlert message={error} />}
      <div className="card narrow" style={{ marginInline: 0 }}>
        <form onSubmit={onSubmit} noValidate>
          <label className="field">
            Equipment
            <select value={equipmentId} onChange={(e) => setEquipmentId(e.target.value)}>
              <option value="">Select equipment…</option>
              {options.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name} ({o.equipmentCode}) — {o.currentStatus.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
            {errors.equipment && <span className="field-error">{errors.equipment}</span>}
          </label>
          {item && (
            <p>
              <StatusBadge value={item.currentStatus} />{' '}
              <span className="muted small">
                {item.category}
                {item.laboratory ? ` · ${item.laboratory}` : ''}
              </span>
            </p>
          )}
          <label className="field">
            Start
            <input type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} />
            {errors.start && <span className="field-error">{errors.start}</span>}
          </label>
          <label className="field">
            End
            <input type="datetime-local" value={end} onChange={(e) => setEnd(e.target.value)} />
            {errors.end && <span className="field-error">{errors.end}</span>}
          </label>
          <label className="field">
            Purpose
            <textarea
              rows={3}
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
              placeholder="What will you use it for?"
            />
            {errors.purpose && <span className="field-error">{errors.purpose}</span>}
          </label>
          <button className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Reserving…' : 'Reserve this slot'}
          </button>
          <p className="muted small">
            The backend validates overlaps, maintenance and booking windows — a 409 means
            someone holds the slot.
          </p>
        </form>
      </div>

      <div className="card">
        <h2>That day at a glance {checking ? '(checking…)' : ''}</h2>
        {!availability && <p className="muted">Pick equipment and times to see live availability.</p>}
        {availability && (
          <>
            {!availability.bookable && (
              <ErrorAlert
                message={availability.message ?? 'This equipment is currently not bookable.'}
              />
            )}
            {availability.bookedPeriods.length === 0 && availability.bookable && (
              <p className="muted">No confirmed bookings that day — good time to reserve.</p>
            )}
            <ul className="legend">
              {availability.bookedPeriods.map((b) => (
                <li key={b.bookingId}>
                  <StatusBadge value={b.status} /> {fmtRange(b.startTime, b.endTime)}
                </li>
              ))}
            </ul>
          </>
        )}
      </div>

      {created && (
        <Modal title={`Booking #${created.id} created`} onClose={() => navigate('/bookings')}>
          <p>
            <strong>{created.equipmentName}</strong>
            <br />
            {fmtRange(created.startTime, created.endTime)}
          </p>
          <p>
            Status: <StatusBadge value={created.status} />
          </p>
          <div className="actions">
            <button className="btn btn-primary" onClick={() => navigate('/bookings')}>
              View my bookings
            </button>
            <button className="btn" onClick={() => setCreated(null)}>
              Book another
            </button>
          </div>
        </Modal>
      )}
    </section>
  );
}
