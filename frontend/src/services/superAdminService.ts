import api from '@/lib/api';

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  meta?: unknown;
  errors?: Array<{
    code?: string;
    message?: string;
    field?: string;
  }>;
  timestamp?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SchoolSummary {
  schoolId: string;
  name: string;
  code: string;
  city?: string;
  state?: string;
  studentCount?: number;
  activeUsers?: number;
  status?: string;
  currentTerm?: string;
  collectionRate?: number;
  createdAt?: string;
}

export interface SchoolDetail {
  schoolId: string;
  name: string;
  code: string;
  email: string;
  phone: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  logoUrl?: string;
  status?: string;
  currentTerm?: {
    termId: string;
    name: string;
    sessionName: string;
    startDate?: string;
    endDate?: string;
  };
  paymentConfig?: Record<string, unknown>;
  createdAt?: string;
}

export interface AcademicSession {
  sessionId: string;
  name: string;
  startDate: string;
  endDate: string;
  isCurrent: boolean;
  terms: Array<{
    termId: string;
    name: string;
    termNumber: number;
    startDate: string;
    endDate: string;
    isCurrent: boolean;
  }>;
}

export interface UserSummary {
  userId: string;
  email: string;
  phoneNumber?: string;
  firstName?: string;
  lastName?: string;
  userType: string;
  roles?: string[];
  isActive?: boolean;
  childrenCount?: number;
  lastLogin?: string;
  createdAt?: string;
}

export interface FeeDashboard {
  termName?: string;
  summary?: {
    totalExpected?: number;
    totalCollected?: number;
    totalOutstanding?: number;
    collectionRate?: number;
    fullyPaidStudents?: number;
    partiallyPaidStudents?: number;
    unpaidStudents?: number;
  };
  byClass?: Array<{
    classId: string;
    className: string;
    studentCount: number;
    expectedAmount: number;
    collectedAmount: number;
    collectionRate: number;
  }>;
  upcomingDeadlines?: {
    dueIn3Days?: { count?: number; amount?: number };
    dueToday?: { count?: number; amount?: number };
    overdue?: { count?: number; amount?: number };
  };
  dailyCollectionTrend?: Array<{
    date: string;
    amount: number;
    transactions: number;
  }>;
}

export interface DailySummary {
  totalCollected?: number;
  totalTransactions?: number;
  dailyBreakdown?: Array<{
    date: string;
    amount: number;
    transactions: number;
  }>;
  byPaymentMethod?: Record<string, { amount: number; count: number }>;
}

export interface NotificationBalance {
  provider?: string;
  balance?: number;
  currency?: string;
  costPerSms?: number;
  lastPurchased?: string;
  estimatedRemainingDays?: number;
}

export interface NotificationTemplate {
  templateId: string;
  code: string;
  name: string;
  channel: string;
  body: string;
  variables?: string[];
  isDefault?: boolean;
  isActive?: boolean;
  updatedAt?: string;
}

export interface ReminderSchedule {
  scheduleId: string;
  name: string;
  triggerType: string;
  daysOffset?: number;
  templateCode?: string;
  isActive?: boolean;
}

export interface CreateSchoolPayload {
  name: string;
  code: string;
  email: string;
  phone: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  paymentConfig?: {
    paystackPublicKey?: string;
    paystackSubaccountCode?: string;
    acceptedPaymentMethods?: string[];
  };
  smsConfig?: {
    provider?: string;
    apiKey?: string;
    username?: string;
    senderId?: string;
    defaultCountryCode?: string;
  };
  termConfig?: {
    termsPerYear: number;
    termNames: string[];
    academicYearStart: string;
  };
  adminUser?: {
    email: string;
    firstName: string;
    lastName: string;
    phoneNumber: string;
  };
}

export interface CreateSchoolResponse {
  schoolId: string;
  name: string;
  code: string;
  status: string;
  adminUserCreated: boolean;
  adminTemporaryPassword?: string;
  currentSessionId?: string;
  currentSessionName?: string;
  createdAt?: string;
  message?: string;
}

function unwrap<T>(response: { data: ApiEnvelope<T> | T }): T {
  const body = response.data as ApiEnvelope<T>;
  if (body && typeof body === 'object' && 'data' in body && 'success' in body) {
    return body.data;
  }
  return response.data as T;
}

export const superAdminService = {
  async listSchools(status = 'ACTIVE', page = 0, size = 50) {
    const response = await api.get<ApiEnvelope<PageResponse<SchoolSummary>>>('/api/v1/schools', {
      params: { status, page, size },
    });
    return unwrap(response);
  },

  async createSchool(payload: CreateSchoolPayload) {
    const response = await api.post<ApiEnvelope<CreateSchoolResponse>>('/api/v1/schools', payload);
    return unwrap(response);
  },

  async getSchool(schoolId: string) {
    const response = await api.get<ApiEnvelope<SchoolDetail>>(`/api/v1/schools/${schoolId}`);
    return unwrap(response);
  },

  async deactivateSchool(schoolId: string) {
    await api.patch<ApiEnvelope<null>>(`/api/v1/schools/${schoolId}/deactivate`);
  },

  async getSessions() {
    const response = await api.get<ApiEnvelope<AcademicSession[]>>('/api/v1/schools/current/sessions');
    return unwrap(response);
  },

  async listUsers(userType = 'SCHOOL_ADMIN') {
    const response = await api.get<ApiEnvelope<PageResponse<UserSummary>>>('/api/v1/auth/users', {
      params: { userType, status: 'ACTIVE', page: 0, size: 20, sortBy: 'userId' },
    });
    return unwrap(response);
  },

  async getFeeDashboard() {
    const response = await api.get<ApiEnvelope<FeeDashboard>>('/api/v1/fees/dashboard', {
      params: { termId: 'current' },
    });
    return unwrap(response);
  },

  async getDailySummary(startDate: string, endDate: string) {
    const response = await api.get<ApiEnvelope<DailySummary>>('/api/v1/reports/daily-summary', {
      params: { startDate, endDate },
    });
    return unwrap(response);
  },

  async getNotificationBalance() {
    const response = await api.get<ApiEnvelope<NotificationBalance>>('/api/v1/notifications/balance');
    return unwrap(response);
  },

  async getNotificationTemplates() {
    const response = await api.get<ApiEnvelope<NotificationTemplate[]>>('/api/v1/notifications/templates');
    return unwrap(response);
  },

  async getReminderSchedules() {
    const response = await api.get<ApiEnvelope<ReminderSchedule[]>>('/api/v1/notifications/reminder-schedules');
    return unwrap(response);
  },
};
