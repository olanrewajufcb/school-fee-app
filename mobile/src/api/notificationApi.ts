import apiClient from './client';
import {
  ApiResponse,
  AppNotification,
  NotificationTemplate,
  SendBulkNotificationRequest,
  NotificationBalance,
} from '../types';

export const notificationApi = {
  /**
   * Fetch active notification templates
   */
  async getTemplates(channel?: string): Promise<NotificationTemplate[]> {
    const res = await apiClient.get<ApiResponse<NotificationTemplate[]>>('/api/v1/notifications/templates', {
      params: { channel },
    });
    return res.data.data;
  },

  /**
   * Send bulk notification (Admin/Accountant)
   */
  async sendBulk(request: SendBulkNotificationRequest): Promise<{ batchId: string; totalQueued: number }> {
    const res = await apiClient.post<ApiResponse<{ batchId: string; totalQueued: number }>>(
      '/api/v1/notifications/send-bulk',
      request
    );
    return res.data.data;
  },

  /**
   * Get notification balance (SMS balance, email quota)
   */
  async getBalance(): Promise<NotificationBalance> {
    const res = await apiClient.get<ApiResponse<NotificationBalance>>('/api/v1/notifications/balance');
    return res.data.data;
  },
};

export default notificationApi;
