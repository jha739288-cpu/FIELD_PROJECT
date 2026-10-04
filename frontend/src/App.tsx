import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import EquipmentListPage from './pages/EquipmentListPage';
import EquipmentDetailsPage from './pages/EquipmentDetailsPage';
import PredictPage from './pages/PredictPage';
import { AdminDashboard, StaffDashboard, StudentDashboard } from './pages/Dashboards';
import AnalyticsView from './pages/AnalyticsView';
import {
  BookingPage,
  CalendarPage,
  ForbiddenPage,
  MyBookingsPage,
  NotFoundPage
} from './pages/Placeholders';

function RoleHome() {
  const { user, loading } = useAuth();
  if (loading) return <p className="muted">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (user.roles.includes('ADMIN')) return <Navigate to="/admin" replace />;
  if (user.roles.includes('LAB_STAFF')) return <Navigate to="/staff" replace />;
  return <Navigate to="/dashboard" replace />;
}

export default function App() {
  return (
    <AuthProvider>
      <Layout>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forbidden" element={<ForbiddenPage />} />

          <Route path="/home" element={<RoleHome />} />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <StudentDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/equipment"
            element={
              <ProtectedRoute>
                <EquipmentListPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/equipment/:id"
            element={
              <ProtectedRoute>
                <EquipmentDetailsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/equipment/:id/predict"
            element={
              <ProtectedRoute>
                <PredictPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/book"
            element={
              <ProtectedRoute>
                <BookingPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/bookings"
            element={
              <ProtectedRoute>
                <MyBookingsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/calendar"
            element={
              <ProtectedRoute>
                <CalendarPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/staff"
            element={
              <ProtectedRoute roles={['LAB_STAFF', 'ADMIN']}>
                <StaffDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <ProtectedRoute roles={['ADMIN']}>
                <AdminDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/analytics"
            element={
              <ProtectedRoute roles={['LAB_STAFF', 'ADMIN']}>
                <AnalyticsView />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Layout>
    </AuthProvider>
  );
}
