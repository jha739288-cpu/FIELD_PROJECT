import { Navigate, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { ToastProvider } from './components/Toast';
import Layout from './components/Layout';
import AppLayout from './components/AppLayout';
import ProtectedRoute from './components/ProtectedRoute';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import EquipmentListPage from './pages/EquipmentListPage';
import EquipmentDetailsPage from './pages/EquipmentDetailsPage';
import PredictPage from './pages/PredictPage';
import PredictionsPage from './pages/PredictionsPage';
import BookingPage from './pages/BookingPage';
import MyBookingsPage from './pages/MyBookingsPage';
import ManageBookingsPage from './pages/ManageBookingsPage';
import CalendarPage from './pages/CalendarPage';
import ProfilePage from './pages/ProfilePage';
import SettingsPage from './pages/SettingsPage';
import VendorDashboard from './pages/VendorDashboard';
import AddEquipmentPage from './pages/AddEquipmentPage';
import AdminDashboard from './pages/AdminDashboard';
import AdminUsersPage from './pages/AdminUsersPage';
import AdminVendorsPage from './pages/AdminVendorsPage';
import ManageEquipmentPage from './pages/ManageEquipmentPage';
import { StudentDashboard } from './pages/Dashboards';
import AnalyticsView from './pages/AnalyticsView';
import { ForbiddenPage, NotFoundPage } from './pages/Placeholders';
import type { Role } from './api/types';

function RoleHome() {
  const { user, loading } = useAuth();
  if (loading) return <p className="muted">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (user.roles.includes('ADMIN')) return <Navigate to="/admin" replace />;
  if (user.roles.includes('VENDOR')) return <Navigate to="/vendor" replace />;
  return <Navigate to="/dashboard" replace />;
}

/** Authenticated pages render inside the role-based sidebar shell. */
function Shell({ children }: { children: ReactNode }) {
  return <AppLayout>{children}</AppLayout>;
}

function staff(element: ReactNode, adminOnly = false) {
  const roles: Role[] = adminOnly ? ['ADMIN'] : ['VENDOR', 'ADMIN'];
  return (
    <ProtectedRoute roles={roles}>
      <Shell>{element}</Shell>
    </ProtectedRoute>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <Routes>
          <Route
            path="/"
            element={
              <Layout>
                <LandingPage />
              </Layout>
            }
          />
          <Route
            path="/login"
            element={
              <Layout>
                <LoginPage />
              </Layout>
            }
          />
          <Route
            path="/register"
            element={
              <Layout>
                <RegisterPage />
              </Layout>
            }
          />
          <Route path="/forbidden" element={<ForbiddenPage />} />

          <Route path="/home" element={<RoleHome />} />

          {/* USER */}
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <Shell>
                  <StudentDashboard />
                </Shell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/equipment"
            element={
              <ProtectedRoute>
                <Shell>
                  <EquipmentListPage />
                </Shell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/equipment/:id"
            element={
              <ProtectedRoute>
                <Shell>
                  <EquipmentDetailsPage />
                </Shell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/equipment/:id/predict"
            element={
              <ProtectedRoute>
                <Shell>
                  <PredictPage />
                </Shell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/book"
            element={
              <ProtectedRoute>
                <Shell>
                  <BookingPage />
                </Shell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/bookings"
            element={
              <ProtectedRoute>
                <Shell>
                  <MyBookingsPage />
                </Shell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/calendar"
            element={
              <ProtectedRoute>
                <Shell>
                  <CalendarPage />
                </Shell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/predict"
            element={
              <ProtectedRoute>
                <Shell>
                  <PredictionsPage />
                </Shell>
              </ProtectedRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <Shell>
                  <ProfilePage />
                </Shell>
              </ProtectedRoute>
            }
          />

          {/* VENDOR */}
          <Route path="/staff" element={<Navigate to="/vendor" replace />} />
          <Route path="/vendor" element={staff(<VendorDashboard />)} />
          <Route
            path="/vendor/equipment"
            element={staff(<ManageEquipmentPage ownerOnly title="My equipment" />)}
          />
          <Route path="/vendor/equipment/new" element={staff(<AddEquipmentPage />)} />
          <Route path="/vendor/bookings" element={staff(<ManageBookingsPage />)} />

          {/* ADMIN */}
          <Route path="/admin" element={staff(<AdminDashboard />, true)} />
          <Route path="/admin/users" element={staff(<AdminUsersPage />, true)} />
          <Route path="/admin/vendors" element={staff(<AdminVendorsPage />, true)} />
          <Route path="/admin/equipment" element={staff(<ManageEquipmentPage />, true)} />
          <Route path="/admin/bookings" element={staff(<ManageBookingsPage />, true)} />
          <Route path="/admin/settings" element={staff(<SettingsPage />, true)} />
          <Route path="/analytics" element={staff(<AnalyticsView />)} />

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </ToastProvider>
    </AuthProvider>
  );
}
