import apiClient from './client';
import {
  ApiResponse,
  SubscriptionPlan,
  SchoolSubscription,
  SubscriptionInvoice,
  PlanCode,
  BillingCycle,
} from '../types';

export const subscriptionApi = {
  /**
   * Get all active subscription plans (Academic Essentials vs Full Suite)
   */
  async getPlans(): Promise<SubscriptionPlan[]> {
    const res = await apiClient.get<ApiResponse<SubscriptionPlan[]>>('/api/v1/subscriptions/plans');
    return res.data.data;
  },

  /**
   * Calculate price dynamically based on student count and billing cycle
   */
  async calculatePrice(planCode: PlanCode, billingCycle: BillingCycle, studentCount: number): Promise<{
    planCode: PlanCode;
    ratePerStudent: number;
    totalAmount: number;
    savingsAmount: number;
  }> {
    const res = await apiClient.post<ApiResponse<any>>('/api/v1/subscriptions/calculate-price', {
      planCode,
      billingCycle,
      studentCount,
    });
    return res.data.data;
  },

  /**
   * Get current school subscription status, 30-day trial countdown, and billing cycle
   */
  async getSchoolSubscription(schoolId: string): Promise<SchoolSubscription> {
    const res = await apiClient.get<ApiResponse<SchoolSubscription>>(`/api/v1/subscriptions/schools/${schoolId}`);
    return res.data.data;
  },

  /**
   * Upgrade or activate school subscription plan
   */
  async upgradePlan(schoolId: string, planCode: PlanCode, billingCycle: BillingCycle): Promise<SchoolSubscription> {
    const res = await apiClient.post<ApiResponse<SchoolSubscription>>(`/api/v1/subscriptions/schools/${schoolId}/upgrade`, {
      planCode,
      billingCycle,
    });
    return res.data.data;
  },

  /**
   * Get billing invoice history
   */
  async getInvoices(schoolId: string): Promise<SubscriptionInvoice[]> {
    const res = await apiClient.get<ApiResponse<SubscriptionInvoice[]>>(`/api/v1/subscriptions/schools/${schoolId}/invoices`);
    return res.data.data;
  },
};

export default subscriptionApi;
