export type PlanCode = 'ACADEMIC_ESSENTIALS' | 'FULL_SUITE';
export type BillingCycle = 'TERMLY' | 'ANNUALLY';
export type SubscriptionStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'EXPIRED' | 'CANCELLED';

export interface SubscriptionPlan {
  id: string;
  code: PlanCode;
  name: string;
  description: string;
  pricePerStudentTermly: number;
  pricePerStudentAnnually: number;
  currency: string;
  hasOnlinePayments: boolean;
  hasResultsManagement: boolean;
  hasAttendanceTracking: boolean;
  hasAutomatedNotifications: boolean;
}

export interface SchoolSubscription {
  id: string;
  schoolId: string;
  plan: SubscriptionPlan;
  billingCycle: BillingCycle;
  status: SubscriptionStatus;
  studentCount: number;
  trialStartDate?: string;
  trialEndDate?: string;
  daysRemainingInTrial?: number;
  currentPeriodStart?: string;
  currentPeriodEnd?: string;
  gracePeriodEndDate?: string;
  amountDue: number;
  autoRenew: boolean;
}

export interface SubscriptionInvoice {
  id: string;
  subscriptionId: string;
  schoolId: string;
  invoiceNumber: string;
  amount: number;
  currency: string;
  studentCount: number;
  billingCycle: BillingCycle;
  status: 'PENDING' | 'PAID' | 'OVERDUE' | 'CANCELLED';
  dueDate: string;
  paidAt?: string;
  createdAt: string;
}
