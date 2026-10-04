import { Link } from 'react-router-dom';

/** Honest placeholders: these modules land next — no fake data is shown. */
function Upcoming({ title, text }: { title: string; text: string }) {
  return (
    <section className="card narrow">
      <h1>{title}</h1>
      <p className="muted">{text}</p>
      <Link className="btn btn-primary" to="/equipment">
        Browse equipment
      </Link>
    </section>
  );
}

export function BookingPage() {
  return (
    <Upcoming
      title="Book equipment"
      text="The booking form arrives with the booking UI module. The API (POST /api/bookings with server-side conflict detection) is already live."
    />
  );
}

export function MyBookingsPage() {
  return (
    <Upcoming
      title="My bookings"
      text="Your reservations (GET /api/bookings/my) will be listed here in the booking UI module."
    />
  );
}

export function CalendarPage() {
  return (
    <Upcoming
      title="Booking calendar"
      text="Schedule, availability and calendar lanes (GET /api/bookings/calendar) will render here in the booking UI module."
    />
  );
}

export function ForbiddenPage() {
  return (
    <section className="card narrow">
      <h1>403 — Access denied</h1>
      <p className="muted">Your account does not have the role required for this page.</p>
      <Link className="btn" to="/">
        Home
      </Link>
    </section>
  );
}

export function NotFoundPage() {
  return (
    <section className="card narrow">
      <h1>404 — Not found</h1>
      <p className="muted">This page does not exist.</p>
      <Link className="btn" to="/">
        Home
      </Link>
    </section>
  );
}
