export type NotificationType = 'FEES' | 'RESULTS' | 'ATTENDANCE' | 'GENERAL' | 'SYSTEM';

export interface AppNotification {
  id: string;
  title: string;
  message: string;
  type: NotificationType;
  read: boolean;
  createdAt: string;
  data?: Record<string, any>;
}

export interface NotificationTemplate {
  templateId: string;
  templateCode: string;
  channel: 'SMS' | 'EMAIL';
  name: string;
  subject?: string;
  body: string;
  variables: string[];
  active: boolean;
}

export interface SendBulkNotificationRequest {
  recipientType: 'ALL_PARENTS' | 'CLASS_PARENTS' | 'ALL_TEACHERS' | 'ALL_STAFF';
  classIds?: string[];
  channel: 'SMS' | 'EMAIL';
  subject?: string;
  message: string;
}

export interface NotificationBalance {
  smsBalance: number;
  emailQuotaRemaining: number;
  currency: string;
}
