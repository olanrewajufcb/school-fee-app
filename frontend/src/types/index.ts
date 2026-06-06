export interface School {
  id: number;
  name: string;
  address: string;
  type: string;
  status: string;
  createdAt?: string;
}

export interface FeeType {
  id: number;
  name: string;
  description: string;
  amount: number;
  frequency: string;
  schoolId: number;
  academicYear: string;
  active: boolean;
  createdAt?: string;
}

export interface StudentFee {
  id: number;
  studentId: number;
  feeType: FeeType;
  amount: number;
  paidAmount: number;
  discount: number;
  dueDate: string;
  paidDate: string | null;
  status: 'PENDING' | 'PARTIAL' | 'PAID' | 'OVERDUE' | 'WAIVED';
  remarks: string;
}

export interface Payment {
  id: number;
  studentFeeId: number;
  studentId: number;
  amount: number;
  paymentMethod: string;
  status: string;
  transactionId: string;
  processedAt: string;
}

export interface Notification {
  id: number;
  type: string;
  recipient: string;
  subject: string;
  message: string;
  status: string;
  sentAt: string;
}

export interface NotificationTemplate {
  id: number;
  code: string;
  name: string;
  content: string;
}

export interface User {
  username: string;
  role: string;
  token?: string;
}

export interface ApiResponse<T> {
  data: T;
  message?: string;
  success?: boolean;
}
