import { Link } from 'react-router-dom';

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
