import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { listEquipment } from '../api/equipment';
import { useAuth } from '../auth/AuthContext';
import StatusBadge from '../components/StatusBadge';
import type { Equipment } from '../api/types';

const STEPS = [
  { title: '1. Browse the catalog', text: 'Search live equipment by name, category, laboratory or status.' },
  { title: '2. Check availability', text: 'See real bookings on the calendar plus data-driven availability forecasts.' },
  { title: '3. Book & check in', text: 'Reserve a slot; scan your booking QR at the instrument to start metered usage.' }
];

const BENEFITS = [
  { title: 'No double bookings', text: 'Server-side conflict detection rejects overlaps — including races.' },
  { title: 'Usage-based', text: 'QR check-in/out meters real usage time per user and instrument.' },
  { title: 'Predictive availability', text: 'Historical patterns forecast busy hours so you plan around them.' },
  { title: 'Sensor-aware', text: 'Current-sensor reports feed live status: in-use, idle, offline, fault.' }
];

function initials(name: string): string {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w.charAt(0).toUpperCase())
    .join('');
}

export default function LandingPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [preview, setPreview] = useState<Equipment[]>([]);

  // Logged-in visitors see a live marketplace preview (real catalog data).
  const loadPreview = useCallback(async () => {
    if (!user) {
      setPreview([]);
      return;
    }
    try {
      const res = await listEquipment({ page: 0, size: 3, sort: 'createdAt,desc' });
      setPreview(res.content);
    } catch {
      setPreview([]);
    }
  }, [user]);

  useEffect(() => {
    void loadPreview();
  }, [loadPreview]);

  return (
    <>
      <section className="hero hero-redesigned">
        <span className="pill">✦ Usage-based marketplace · live availability · forecasts</span>
        <h1>
          Laboratory equipment,
          <br />
          bookable by the hour<span className="accent">.</span>
        </h1>
        <p className="hero-sub">
          Browse the catalog, check live availability on the calendar, and book instruments for
          your lab sessions. Usage is metered — you only occupy what you reserve.
        </p>
        <form
          className="hero-search"
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            navigate(user ? `/equipment?q=${encodeURIComponent(q.trim())}` : '/register');
          }}
        >
          <input
            className="input"
            placeholder="Search microscopes, centrifuges, labs…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Search equipment"
          />
          <button className="btn btn-primary" type="submit">
            Search
          </button>
        </form>
        <div className="actions" style={{ justifyContent: 'center' }}>
          {user ? (
            <Link className="btn btn-primary" to="/equipment">
              Browse equipment
            </Link>
          ) : (
            <>
              <Link className="btn btn-primary" to="/register">
                Get started
              </Link>
              <Link className="btn" to="/login">
                Login
              </Link>
            </>
          )}
        </div>
        <div className="hero-stats">
          <div>
            <strong>24/7</strong>
            <span>booking access</span>
          </div>
          <div>
            <strong>0</strong>
            <span>double bookings</span>
          </div>
          <div>
            <strong>Live</strong>
            <span>sensor status</span>
          </div>
        </div>
      </section>

      {preview.length > 0 && (
        <section>
          <div className="card-row">
            <h2>Fresh in the marketplace</h2>
            <Link className="btn btn-small" to="/equipment">
              View all
            </Link>
          </div>
          <div className="grid">
            {preview.map((e) => (
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
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section>
        <h2>How the platform works</h2>
        <div className="hero-grid">
          {STEPS.map((s) => (
            <div key={s.title} className="card">
              <h2>{s.title}</h2>
              <p className="muted">{s.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section>
        <h2>Why LabMarket</h2>
        <div className="hero-grid">
          {BENEFITS.map((b) => (
            <div key={b.title} className="card">
              <h2>{b.title}</h2>
              <p className="muted">{b.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="card">
        <h2>Predictive availability, honestly labeled</h2>
        <p className="muted">
          Every forecast shows its probability, confidence and reasons — and every screen
          states plainly that predictions never override real bookings or maintenance.
          Browse equipment to see live forecasts per instrument.
        </p>
        <div className="actions">
          <Link className="btn btn-primary" to={user ? '/equipment' : '/register'}>
            {user ? 'Open the marketplace' : 'Create your account'}
          </Link>
        </div>
      </section>
    </>
  );
}
