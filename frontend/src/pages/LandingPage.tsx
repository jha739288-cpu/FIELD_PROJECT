import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function LandingPage() {
  const { user } = useAuth();
  return (
    <section className="hero">
      <h1>Laboratory equipment, bookable by the hour</h1>
      <p className="muted">
        Browse the catalog, check live availability on the calendar, and book instruments for
        your lab sessions. Usage is metered — you only occupy what you reserve.
      </p>
      <div className="actions">
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
    </section>
  );
}
