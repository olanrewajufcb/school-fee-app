import api from '@/lib/api';

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  errors?: Array<{ code?: string; message?: string; field?: string }>;
  message?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
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
  dailyCollectionTrend?: Array<{ date: string; amount: number; transactions: number }>;
}

export interface DailySummary {
  period?: { startDate: string; endDate: string };
  totalCollected?: number;
  totalTransactions?: number;
  byPaymentMethod?: Record<string, { amount: number; count: number }>;
  dailyBreakdown?: Array<{ date: string; amount: number; transactions: number }>;
}

export interface StudentSummary {
  studentId: string;
  admissionNumber: string;
  firstName: string;
  lastName: string;
  middleName?: string;
  currentClass?: { classId: string; name: string; gradeLevel: string };
  status?: string;
  parentPhone?: string;
  parentName?: string;
}

export interface StudentFee {
  studentFeeId: string;
  structureName: string;
  termName: string;
  isCurrentTerm?: boolean;
  isUpcomingTerm?: boolean;
  items?: Array<{ description: string; amount: number; isMandatory: boolean }>;
  totalAmount: number;
  discountAmount?: number;
  amountPaid: number;
  balance: number;
  dueDate?: string;
  daysUntilDue?: number;
  status?: string;
  lateFeeApplicable?: boolean;
  lateFeeAmount?: number;
}

export interface FeeStructure {
  structureId: string;
  name: string;
  termName?: string;
  sessionName?: string;
  totalAmount: number;
  mandatoryAmount?: number;
  applicableToClasses?: string[];
  applicableClassCount?: number;
  studentCount?: number;
  collectionRate?: number;
  dueDate?: string;
  status?: string;
}

export interface OfflinePaymentPayload {
  studentFeeId: string;
  amount: number;
  paymentMethod: 'CASH' | 'TRANSFER' | 'POS';
  paymentDate: string;
  receivedBy?: string;
  notes?: string;
  generateReceipt: boolean;
}

export interface OfflinePaymentResponse {
  paymentId: string;
  status: string;
  receiptNumber?: string;
  approvedBy?: string;
}

export interface PaymentStatus {
  paymentId: string;
  status: string;
  amount: number;
  paymentMethod: string;
  transactionReference?: string;
  paidAt?: string;
  receipt?: {
    receiptNumber?: string;
    receiptUrl?: string;
    breakdown?: Array<{
      studentName: string;
      admissionNumber: string;
      feeDescription: string;
      amount: number;
    }>;
  };
}

export interface SendBulkResponse {
  batchId: string;
  recipientsCount: number;
  estimatedCost: number;
  status: string;
  message: string;
}

export interface ReceiptDetail {
  receiptNumber: string;
  paymentId: string;
  schoolName: string;
  paidBy: string;
  amount: number;
  paymentMethod: string;
  paymentDate: string;
  breakdown: Array<{
    studentName: string;
    admissionNumber: string;
    className?: string;
    term?: string;
    amount: number;
  }>;
  generatedAt: string;
}

export interface NotificationBalance {
  provider: string;
  balance: number;
  currency: string;
  costPerSms: number;
  lastPurchased: string;
  estimatedRemainingDays: number;
}

export interface PaymentHistoryItem {
  paymentId: string;
  date: string;
  amount: number;
  paymentMethod: string;
  status: string;
  description: string;
  receiptNumber: string;
}

export interface NotificationTemplate {
  templateId: string;
  code: string;
  name: string;
  channel: string;
  body: string;
  variables: string[];
  isDefault: boolean;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ReminderSchedule {
  scheduleId: string;
  name: string;
  triggerType: string;
  daysOffset: number;
  sendTime: string;
  templateCode: string;
  isActive: boolean;
}

function unwrap<T>(response: { data: ApiEnvelope<T> | T }): T {
  const body = response.data as ApiEnvelope<T>;
  if (body && typeof body === 'object' && 'data' in body && 'success' in body) {
    return body.data;
  }
  return response.data as T;
}

export const accountantService = {
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

  async searchStudents(search?: string) {
    const response = await api.get<ApiEnvelope<PageResponse<StudentSummary>>>('/api/v1/students', {
      params: { search, status: 'ACTIVE', page: 0, size: 50 },
    });
    return unwrap(response);
  },

  async getStudentFees(studentId: string) {
    const response = await api.get<ApiEnvelope<StudentFee[]>>(`/api/v1/fees/students/${studentId}`);
    return unwrap(response);
  },

  async recordOfflinePayment(payload: OfflinePaymentPayload) {
    const response = await api.post<ApiEnvelope<OfflinePaymentResponse>>('/api/v1/payments/offline', payload);
    return unwrap(response);
  },

  async getPaymentStatus(paymentId: string) {
    const response = await api.get<ApiEnvelope<PaymentStatus>>(`/api/v1/payments/${paymentId}`);
    return unwrap(response);
  },

  async sendBulkReminders(payload: { studentFeeIds: string[]; templateCode: string; channel: 'SMS' | 'WHATSAPP' | 'BOTH' }) {
    const response = await api.post<ApiEnvelope<SendBulkResponse>>('/api/v1/notifications/send-bulk', payload);
    return unwrap(response);
  },

  async getFeeStructures() {
    const response = await api.get<ApiEnvelope<FeeStructure[]>>('/api/v1/fees/structures', {
      params: { status: 'ACTIVE' },
    });
    return unwrap(response);
  },

  async getReceipt(receiptNumber: string) {
    const response = await api.get<ApiEnvelope<ReceiptDetail>>(`/api/v1/receipts/${receiptNumber}`);
    return unwrap(response);
  },

  async downloadReceiptPdf(receiptNumber: string) {
    const response = await api.get<Blob>(`/api/v1/receipts/${receiptNumber}/pdf`, {
      responseType: 'blob',
    });
    return response.data;
  },

  async downloadFeeCollectionReport(format: 'PDF' | 'CSV', termId = 'current') {
    const response = await api.get<Blob>('/api/v1/reports/fee-collection', {
      params: { termId, format },
      responseType: 'blob',
    });
    return response.data;
  },

  async getNotificationBalance() {
    const response = await api.get<ApiEnvelope<NotificationBalance>>('/api/v1/notifications/balance');
    return unwrap(response);
  },

  async getPaymentHistory(page = 0, size = 10) {
    const response = await api.get<ApiEnvelope<PageResponse<PaymentHistoryItem>>>('/api/v1/payments/history', {
      params: { page, size }
    });
    return unwrap(response);
  },

  async getNotificationTemplates() {
    const response = await api.get<ApiEnvelope<NotificationTemplate[]>>('/api/v1/notifications/templates');
    return unwrap(response);
  },

  async updateNotificationTemplate(templateId: string, payload: { name: string; body: string; isActive: boolean }) {
    const response = await api.put<ApiEnvelope<{ templateId: string; status: string }>>(`/api/v1/notifications/templates/${templateId}`, payload);
    return unwrap(response);
  },

  async getReminderSchedules() {
    const response = await api.get<ApiEnvelope<ReminderSchedule[]>>('/api/v1/notifications/reminder-schedules');
    return unwrap(response);
  },

  async getOutstandingFeeIds(filter: string) {
    const response = await api.get<ApiEnvelope<string[]>>('/api/v1/fees/outstanding-ids', {
      params: { filter }
    });
    return unwrap(response);
  },
};
