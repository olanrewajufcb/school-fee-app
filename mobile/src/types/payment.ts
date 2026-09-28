export type PaymentMethod = 'CARD' | 'BANK_TRANSFER' | 'USSD' | 'CASH' | 'POS';
export type PaymentStatus = 'PENDING' | 'SUCCESSFUL' | 'FAILED' | 'PROCESSING';

export interface InitiatePaymentRequest {
  studentFeeId: string;
  amount: number;
  paymentMethod: PaymentMethod;
  email: string;
  callbackUrl?: string;
}

export interface InitiatePaymentResponse {
  paymentId: string;
  paymentReference: string;
  authorizationUrl?: string;
  accessCode?: string;
  amount: number;
}

export interface PaymentRecord {
  paymentId: string;
  reference: string;
  amount: number;
  currency: string;
  paymentMethod: PaymentMethod;
  status: PaymentStatus;
  receiptNumber?: string;
  paidAt: string;
  studentName?: string;
  feeName?: string;
}

export interface ReceiptDetail {
  receiptNumber: string;
  studentName: string;
  admissionNumber: string;
  className: string;
  schoolName: string;
  schoolAddress?: string;
  schoolLogoUrl?: string;
  amountPaid: number;
  currency: string;
  paymentMethod: PaymentMethod;
  paymentDate: string;
  issuedBy: string;
  qrVerificationCode: string;
  feeBreakdown: Array<{
    item: string;
    amount: number;
  }>;
}
