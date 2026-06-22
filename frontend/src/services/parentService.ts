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

export interface ChildProfile {
  studentId: string;
  admissionNumber: string;
  firstName: string;
  lastName: string;
  currentClass?: string;
  profilePhotoUrl?: string;
  feeStatus?: {
    termName?: string;
    totalFee?: number;
    amountPaid?: number;
    balance?: number;
    status?: string;
    dueDate?: string;
  };
}

export interface StudentFee {
  studentFeeId: string;
  structureName: string;
  termName: string;
  isCurrentTerm: boolean;
  isUpcomingTerm: boolean;
  items: Array<{
    description: string;
    amount: number;
    isMandatory: boolean;
  }>;
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

export interface PaymentHistoryItem {
  paymentId: string;
  date: string;
  amount: number;
  paymentMethod: string;
  status: string;
  description?: string;
  receiptNumber?: string;
}

export interface InitiatePaymentResponse {
  paymentId: string;
  status: string;
  paymentMethod: string;
  amount: number;
  gatewayMessage?: string;
  checkoutRequestId?: string;
  expiresInSeconds?: number;
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

export interface ChildResultSummary {
  studentId: string;
  termId?: string;
  firstName: string;
  lastName: string;
  className?: string;
  termName?: string;
  summary?: {
    average: number;
    totalSubjects: number;
    grade: string;
    classPosition?: number;
    outOf?: number;
  };
  topSubjects?: Array<{ name: string; score: number; grade: string }>;
  attendance?: {
    daysOpen: number;
    daysPresent: number;
    daysAbsent: number;
    attendanceRate: number;
  };
}

export interface StudentResult {
  student?: {
    studentId: string;
    admissionNumber: string;
    fullName: string;
    className: string;
    classSize: number;
    profilePhotoUrl?: string;
  };
  term?: { termId: string; name: string; sessionName: string };
  subjects?: Array<{
    subjectId: string;
    subjectName: string;
    caScores?: Array<{ component: string; score: number; maxScore: number }>;
    caTotal?: number;
    caMaxTotal?: number;
    examScore?: number;
    examMaxScore?: number;
    finalScore?: number;
    finalMaxScore?: number;
    percentage?: number;
    grade?: string;
    remark?: string;
    subjectPosition?: number;
  }>;
  summary?: {
    totalScore?: number;
    totalMaxScore?: number;
    average?: number;
    overallGrade?: string;
    subjectsTaken?: number;
    subjectsPassed?: number;
    subjectsFailed?: number;
  };
  ranking?: { classPosition: number; outOf: number; percentile: number; topThird: boolean };
  attendance?: { daysOpen: number; daysPresent: number; daysAbsent: number; attendanceRate: number };
  teacherComment?: string;
  principalComment?: string;
}

export interface PublishedTermResult {
  termId: string;
  termName: string;
  sessionName?: string;
  average: number;
  overallGrade?: string;
  classPosition: number;
  outOf: number;
}

export interface ShareResultResponse {
  channel: 'SMS' | 'WHATSAPP' | 'EMAIL';
  sentAt: string;
  message: string;
  shareText: string;
  shareUrl: string;
}

export interface ReceiptDetail {
  receiptNumber: string;
  paymentId: string;
  schoolName: string;
  schoolAddress?: string;
  paidBy: string;
  amount: number;
  amountInWords?: string;
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
  smsSent: boolean;
  emailSent: boolean;
}

function unwrap<T>(response: { data: ApiEnvelope<T> | T }): T {
  const body = response.data as ApiEnvelope<T>;
  if (body && typeof body === 'object' && 'data' in body && 'success' in body) {
    return body.data;
  }
  return response.data as T;
}

export function extractCheckoutUrl(response: InitiatePaymentResponse) {
  const message = response.gatewayMessage ?? '';
  const match = message.match(/https?:\/\/\S+/i);
  return match?.[0];
}

export const parentService = {
  async getChildren() {
    const response = await api.get<ApiEnvelope<ChildProfile[]>>('/api/v1/students/my-children');
    return unwrap(response);
  },

  async getStudentFees(studentId: string) {
    const response = await api.get<ApiEnvelope<StudentFee[]>>(`/api/v1/fees/students/${studentId}`);
    return unwrap(response);
  },

  async initiatePayment(payload: {
    studentFeeIds: string[];
    paymentMethod: 'PAYSTACK';
    phoneNumber: string;
    amount: number;
    payOptionalItems?: Record<string, string[]>;
  }) {
    const response = await api.post<ApiEnvelope<InitiatePaymentResponse>>('/api/v1/payments', payload);
    return unwrap(response);
  },

  async getPaymentStatus(paymentId: string) {
    const response = await api.get<ApiEnvelope<PaymentStatus>>(`/api/v1/payments/${paymentId}`);
    return unwrap(response);
  },

  async getPaymentHistory(studentId?: string) {
    const response = await api.get<ApiEnvelope<PageResponse<PaymentHistoryItem>>>('/api/v1/payments/history', {
      params: { studentId, page: 0, size: 20 },
    });
    return unwrap(response);
  },

  async getChildrenResults() {
    const response = await api.get<ApiEnvelope<ChildResultSummary[]>>('/api/v1/results/my-children/current');
    return unwrap(response);
  },

  async getStudentResult(studentId: string, termId: string) {
    const response = await api.get<ApiEnvelope<StudentResult>>(`/api/v1/results/students/${studentId}/term/${termId}`);
    return unwrap(response);
  },

  async getPublishedStudentResults(studentId: string) {
    const response = await api.get<ApiEnvelope<PublishedTermResult[]>>(
      `/api/v1/results/students/${studentId}/published-terms`,
    );
    return unwrap(response);
  },

  async downloadStudentResultPdf(studentId: string, termId: string) {
    const response = await api.get<Blob>(
      `/api/v1/results/students/${studentId}/term/${termId}/download`,
      { responseType: 'blob' },
    );
    return response.data;
  },

  async shareStudentResult(
    studentId: string,
    termId: string,
    channel: 'SMS' | 'WHATSAPP' | 'EMAIL',
    recipient: string,
  ) {
    const response = await api.post<ApiEnvelope<ShareResultResponse>>(
      `/api/v1/results/students/${studentId}/term/${termId}/share`,
      { channel, recipient },
    );
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

  async shareReceipt(receiptNumber: string, channel: 'SMS' | 'WHATSAPP', recipient: string) {
    const response = await api.post<ApiEnvelope<unknown>>(`/api/v1/receipts/${receiptNumber}/share`, {
      channel,
      recipient,
    });
    return unwrap(response);
  },
};
