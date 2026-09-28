export interface ReceiptBreakdownItem {
  studentName: string;
  admissionNumber: string;
  className: string;
  term: string;
  amount: number;
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
  breakdown: ReceiptBreakdownItem[];
  generatedAt: string;
  smsSent: boolean;
  emailSent: boolean;
}

export interface ShareReceiptRequest {
  channel: 'SMS' | 'EMAIL' | 'WHATSAPP';
  recipient?: string;
}

export interface ShareReceiptResponse {
  success: boolean;
  message: string;
}
