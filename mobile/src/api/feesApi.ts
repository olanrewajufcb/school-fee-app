import apiClient from './client';
import {
  ApiResponse,
  PageResponse,
  StudentFee,
  InitiatePaymentRequest,
  InitiatePaymentResponse,
  PaymentRecord,
  ReceiptDetail,
  FeeDashboardSummary,
} from '../types';

export const feesApi = {
  /**
   * Get all fees assigned to a student (tuition, books, uniforms, PTA)
   */
  async getStudentFees(studentId: string): Promise<StudentFee[]> {
    const res = await apiClient.get<ApiResponse<StudentFee[]>>(`/api/v1/fees/students/${studentId}`);
    return res.data.data;
  },

  /**
   * Get administrative fee overview dashboard
   */
  async getFeeDashboard(termId: string = 'current'): Promise<FeeDashboardSummary> {
    const res = await apiClient.get<ApiResponse<FeeDashboardSummary>>('/api/v1/fees/dashboard', {
      params: { termId },
    });
    return res.data.data;
  },

  /**
   * Initiate online payment with gateway (Paystack)
   */
  async initiatePayment(payload: InitiatePaymentRequest): Promise<InitiatePaymentResponse> {
    const res = await apiClient.post<ApiResponse<InitiatePaymentResponse>>('/api/v1/payments', payload);
    return res.data.data;
  },

  /**
   * Check status of a payment by ID
   */
  async getPaymentStatus(paymentId: string): Promise<PaymentRecord> {
    const res = await apiClient.get<ApiResponse<PaymentRecord>>(`/api/v1/payments/${paymentId}`);
    return res.data.data;
  },

  /**
   * Get payment history for a student or logged-in parent
   */
  async getPaymentHistory(studentId?: string, page: number = 0, size: number = 20): Promise<PageResponse<PaymentRecord>> {
    const res = await apiClient.get<ApiResponse<PageResponse<PaymentRecord>>>('/api/v1/payments/history', {
      params: { studentId, page, size },
    });
    return res.data.data;
  },

  /**
   * Get verified receipt details by receipt number
   */
  async getReceipt(receiptNumber: string): Promise<ReceiptDetail> {
    const res = await apiClient.get<ApiResponse<ReceiptDetail>>(`/api/v1/receipts/${receiptNumber}`);
    return res.data.data;
  },

  /**
   * Share receipt via SMS or Email
   */
  async shareReceipt(receiptNumber: string, channel: 'SMS' | 'EMAIL', destination: string): Promise<void> {
    await apiClient.post(`/api/v1/receipts/${receiptNumber}/share`, { channel, destination });
  },
};

export default feesApi;
