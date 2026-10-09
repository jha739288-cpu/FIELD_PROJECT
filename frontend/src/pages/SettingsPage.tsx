import { useAuth } from '../auth/AuthContext';

/** Account + deployment info. No fake settings: everything shown is real. */
export default function SettingsPage() {
  const { user } = useAuth();

  return (
    <section>
      <h1>Settings</h1>
      <div className="card">
        <h2>Account</h2>
        <dl className="details">
          <div className="detail-row">
            <dt>Username</dt>
            <dd>{user?.username}</dd>
          </div>
          <div className="detail-row">
            <dt>Email</dt>
            <dd>{user?.email}</dd>
          </div>
          <div className="detail-row">
            <dt>Roles</dt>
            <dd>{user?.roles.join(', ')}</dd>
          </div>
        </dl>
        <p className="muted small">
          Role changes and account status are managed by an administrator.
          Contact yours if your access is wrong.
        </p>
      </div>
      <div className="card">
        <h2>Application</h2>
        <dl className="details">
          <div className="detail-row">
            <dt>API base URL</dt>
            <dd>{import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'}</dd>
          </div>
          <div className="detail-row">
            <dt>Session</dt>
            <dd>JWT, 1 hour; “Remember me” persists it on this device, otherwise it ends with the tab.</dd>
          </div>
        </dl>
      </div>
    </section>
  );
}
