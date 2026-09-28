import api from '@/lib/api';
import type { ApiEnvelope } from './superAdminService';

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
  paymentReference?: string;
  createdAt: string;
}

export interface PriceCalculation {
  planCode: PlanCode;
  planName: string;
  billingCycle: BillingCycle;
  studentCount: number;
  ratePerStudent: number;
  totalAmount: number;
  currency: string;
  savingsAmount: number;
}

export const subscriptionService = {
  /**
   * Get all active subscription plans
   */
  async getPlans(): Promise<SubscriptionPlan[]> {
    const res = await api.get<ApiEnvelope<SubscriptionPlan[]>>('/api/v1/subscriptions/plans');
    return res.data.data;
  },

  /**
   * Calculate price dynamically for a given student count and billing cycle
   */
  async calculatePrice(planCode: PlanCode, billingCycle: BillingCycle, studentCount: number): Promise<PriceCalculation> {
    const res = await api.post<ApiEnvelope<PriceCalculation>>('/api/v1/subscriptions/calculate-price', {
      planCode,
      billingCycle,
      studentCount,
    });
    return res.data.data;
  },

  /**
   * Get subscription status for a school
   */
  async getSchoolSubscription(schoolId: string): Promise<SchoolSubscription> {
    const res = await api.get<ApiEnvelope<SchoolSubscription>>(`/api/v1/subscriptions/schools/${schoolId}`);
    return res.data.data;
  },

  /**
   * Upgrade or change school subscription plan
   */
  async upgradePlan(schoolId: string, planCode: PlanCode, billingCycle: BillingCycle): Promise<SchoolSubscription> {
    const res = await api.post<ApiEnvelope<SchoolSubscription>>(`/api/v1/subscriptions/schools/${schoolId}/upgrade`, {
      planCode,
      billingCycle,
    });
    return res.data.data;
  },

  /**
   * Get invoice history for a school
   */
  async getInvoices(schoolId: string): Promise<SubscriptionInvoice[]> {
    const res = await api.get<ApiEnvelope<SubscriptionInvoice[]>>(`/api/v1/subscriptions/schools/${schoolId}/invoices`);
    return res.data.data;
  },
};

export default subscriptionService;
