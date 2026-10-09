// Shared DTO types mirroring the Spring Boot API (never invented client-side).

export type Role = 'ADMIN' | 'USER' | 'VENDOR';

export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string | null;
  enabled: boolean;
  roles: Role[];
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  username: string;
  roles: Role[];
  expiresInMs: number;
}

export interface RegisterPayload {
  username: string;
  email: string;
  password: string;
  fullName?: string;
  role?: 'USER' | 'VENDOR';
}

export interface LoginPayload {
  username: string;
  password: string;
}

export type EquipmentStatus =
  | 'AVAILABLE'
  | 'RESERVED'
  | 'IN_USE'
  | 'OVERDUE'
  | 'MAINTENANCE'
  | 'SENSOR_OFFLINE';

export type EquipmentCondition = 'NEW' | 'GOOD' | 'FAIR' | 'POOR' | 'DAMAGED';

export type MaintenanceStatus = 'OPERATIONAL' | 'DUE' | 'IN_MAINTENANCE' | 'OUT_OF_SERVICE';

export interface Equipment {
  id: number;
  equipmentCode: string;
  name: string;
  category: string;
  description: string | null;
  manufacturer: string | null;
  model: string | null;
  laboratory: string | null;
  imageUrl: string | null;
  specifications: string | null;
  pricePerHour: number | null;
  quantity: number | null;
  usageInstructions: string | null;
  safetyInfo: string | null;
  condition: EquipmentCondition;
  currentStatus: EquipmentStatus;
  maintenanceStatus: MaintenanceStatus;
  createdByUsername: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EquipmentCreatePayload {
  equipmentCode: string;
  name: string;
  category: string;
  description?: string;
  manufacturer?: string;
  model?: string;
  laboratory?: string;
  imageUrl?: string;
  specifications?: string;
  pricePerHour?: number | null;
  quantity?: number | null;
  usageInstructions?: string;
  safetyInfo?: string;
  condition?: EquipmentCondition;
  currentStatus?: EquipmentStatus;
  maintenanceStatus?: MaintenanceStatus;
}

export type EquipmentUpdatePayload = Omit<EquipmentCreatePayload, 'equipmentCode'> & {
  condition: EquipmentCondition;
  currentStatus: EquipmentStatus;
  maintenanceStatus: MaintenanceStatus;
};

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface Summary {
  equipment: {
    total: number;
    available: number;
    reserved: number;
    inUse: number;
    overdue: number;
    maintenance: number;
    sensorOffline: number;
  };
  bookings: {
    total: number;
    pending: number;
    confirmed: number;
    checkedIn: number;
    completed: number;
    cancelled: number;
    overdue: number;
    rejected: number;
  };
  usage: { sessionsStarted: number; sessionsCompleted: number; totalSeconds: number; totalHours: number };
  sensors: {
    events: number;
    staleEvents: number;
    faults: number;
    idle: number;
    inUse: number;
    offline: number;
    reliability: number | null;
  };
  alerts: { open: number; resolved: number };
  utilizationPercent: number;
  conflictAttempts: number;
  windowFrom: string;
  windowTo: string;
  computedAt: string;
}

export interface Utilization {
  from: string;
  to: string;
  equipmentCount: number;
  operationalCount: number;
  windowSeconds: number;
  totalUsageSeconds: number;
  utilizationPercent: number;
  items: Array<{
    equipmentId: number;
    equipmentCode: string;
    equipmentName: string;
    usageSeconds: number;
    utilizationPercent: number;
    operational: boolean;
    maintenanceStatus: string;
  }>;
}

export interface BookingAnalytics {
  from: string;
  to: string;
  total: number;
  byStatus: Record<string, number>;
  byDay: Array<{ date: string; count: number }>;
  byEquipment: Array<{ equipmentId: number; equipmentCode: string; count: number }>;
  cancelled: number;
  overdue: number;
}

export interface SensorAnalytics {
  from: string;
  to: string;
  total: number;
  byStatus: Record<string, number>;
  stale: number;
  reliability: number | null;
  faults: number;
  offline: number;
  byEquipment: Array<{ equipmentCode: string; count: number }>;
}

export interface MySummary {
  username: string;
  bookings: Summary['bookings'];
  usage: Summary['usage'];
  windowFrom: string;
  windowTo: string;
  computedAt: string;
}

export interface EquipmentDashboard {
  id: number;
  equipmentCode: string;
  name: string;
  category: string;
  condition: string;
  currentStatus: EquipmentStatus;
  maintenanceStatus: string;
  from: string;
  to: string;
  bookingsByStatus: Record<string, number>;
  sessions: number;
  completedSessions: number;
  usageSeconds: number;
  utilizationPercent: number;
  recentBookings: Array<{ id: number; startTime: string; endTime: string; status: string }>;
}

export interface UsageTrend {
  from: string;
  to: string;
  granularity: string;
  points: Array<{
    date: string;
    usageSeconds: number;
    usageHours: number;
    sessionsEnded: number;
    bookingsCreated: number;
  }>;
}

export interface UsageRecord {
  id: number;
  bookingId: number | null;
  equipmentId: number;
  equipmentCode: string;
  equipmentName: string;
  userId: number;
  username: string;
  startedAt: string;
  endedAt: string | null;
  durationSeconds: number | null;
  source: string;
  status: string;
  note: string | null;
  createdAt: string;
}

export type PredictedStatus = 'AVAILABLE' | 'LIMITED' | 'UNAVAILABLE';

export interface UserAdmin {
  id: number;
  username: string;
  email: string;
  fullName: string | null;
  enabled: boolean;
  roles: Role[];
  createdAt: string;
  bookingCount: number;
}

export interface VendorSummary {
  id: number;
  username: string;
  email: string;
  fullName: string | null;
  enabled: boolean;
  createdAt: string;
  equipmentCount: number;
  bookingCount: number;
}

export interface AdminOverview {
  totalUsers: number;
  usersByRole: Record<string, number>;
  equipmentByStatus: Record<string, number>;
  bookingsByStatus: Record<string, number>;
  openAlerts: number;
  recentUsers: Array<{ id: number; username: string; email: string; roles: Role[]; createdAt: string }>;
  recentBookings: Array<{ id: number; equipmentCode: string; username: string; status: string; startTime: string }>;
  recentEquipment: Array<{ id: number; equipmentCode: string; name: string; status: string }>;
}

export interface VendorDashboard {
  username: string;
  totalEquipment: number;
  equipmentByStatus: Record<string, number>;
  totalBookings: number;
  bookingsByStatus: Record<string, number>;
  completedSessions: number;
  usageSeconds: number;
  usageHours: number;
  recentBookings: Array<{ id: number; equipmentCode: string; username: string; status: string; startTime: string }>;
}

export type BookingStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'OVERDUE'
  | 'REJECTED';

export interface Booking {
  id: number;
  equipmentId: number;
  equipmentCode: string;
  equipmentName: string;
  userId: number;
  username: string;
  startTime: string;
  endTime: string;
  status: BookingStatus;
  purpose: string;
  createdAt: string;
  updatedAt: string;
}

export interface BookingCreatePayload {
  equipmentId: number;
  startTime: string;
  endTime: string;
  purpose: string;
}

export interface TimeSlot {
  startTime: string;
  endTime: string;
}

export interface BookedPeriod extends TimeSlot {
  bookingId: number;
  status: BookingStatus;
}

export interface BookingSlot extends TimeSlot {
  id: number;
  status: BookingStatus;
  purpose: string | null;
  username: string | null;
}

export interface EquipmentSchedule {
  equipmentId: number;
  equipmentCode: string;
  equipmentName: string;
  currentStatus: EquipmentStatus;
  maintenanceStatus: string;
  from: string;
  to: string;
  slots: BookingSlot[];
}

export interface Availability {
  equipmentId: number;
  equipmentCode: string;
  equipmentName: string;
  currentStatus: EquipmentStatus;
  maintenanceStatus: string;
  bookable: boolean;
  from: string;
  to: string;
  bookedPeriods: BookedPeriod[];
  pendingPeriods: TimeSlot[];
  freePeriods: TimeSlot[];
  maintenance: boolean;
  message: string | null;
}

export interface CalendarResponse {
  from: string;
  to: string;
  schedules: EquipmentSchedule[];
}

export type AlertStatus = 'OPEN' | 'RESOLVED';

export interface Alert {
  id: number;
  type: string;
  status: AlertStatus;
  bookingId: number;
  equipmentId: number;
  equipmentCode: string;
  message: string;
  resolutionNote: string | null;
  resolvedByUsername: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface PredictionFactor {
  name: string;
  value: number;
  detail: string;
}

export interface PredictionSlot {
  startTime: string;
  endTime: string;
  predictedStatus: PredictedStatus;
  probabilityAvailable: number;
  confidence: number;
  factors: PredictionFactor[];
}

export interface Prediction {
  equipmentId: number;
  equipmentCode: string;
  method: string;
  from: string;
  to: string;
  slotMinutes: number;
  disclaimer: string;
  slots: PredictionSlot[];
}

export interface PredictionEvaluation {
  equipmentId: number;
  method: string;
  from: string;
  to: string;
  slotsEvaluated: number;
  correct: number;
  accuracy: number;
  precisionUnavailable: number;
  recallUnavailable: number;
  brierScore: number;
  note: string;
}
