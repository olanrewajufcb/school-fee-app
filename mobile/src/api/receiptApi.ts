import apiClient from './client';
import { ApiResponse, ReceiptDetail, ShareReceiptRequest, ShareReceiptResponse } from '../types';

export const receiptApi = {
  /**
   * Fetch receipt details by receipt number
   */
  async getReceiptDetails(receiptNumber: string): Promise<ReceiptDetail> {
    const res = await apiClient.get<ApiResponse<ReceiptDetail>>(`/api/v1/receipts/${encodeURIComponent(receiptNumber)}`);
    return res.data.data;
  },

  /**
   * Share receipt via SMS, Email, or WhatsApp
   */
  async shareReceipt(receiptNumber: string, request: ShareReceiptRequest): Promise<ShareReceiptResponse> {
    const res = await apiClient.post<ApiResponse<ShareReceiptResponse>>(
      `/api/v1/receipts/${encodeURIComponent(receiptNumber)}/share`,
      request
    );
    return res.data.data;
  },
};

export default receiptApi;
