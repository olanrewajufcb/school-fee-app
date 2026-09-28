import React, { useEffect, useState } from 'react';
import { subscriptionService } from '@/services/subscriptionService';
import type {
  SchoolSubscription,
  SubscriptionInvoice,
  PlanCode,
  BillingCycle,
} from '@/services/subscriptionService';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Sparkles,
  CheckCircle2,
  Clock,
  ArrowRight,
  AlertCircle,
  X,
  RefreshCw,
} from 'lucide-react';

interface Props {
  schoolId: string;
}

export const SubscriptionManagementSection: React.FC<Props> = ({ schoolId }) => {
  const [subscription, setSubscription] = useState<SchoolSubscription | null>(null);
  const [invoices, setInvoices] = useState<SubscriptionInvoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Upgrade form state
  const [selectedPlan, setSelectedPlan] = useState<PlanCode>('FULL_SUITE');
  const [selectedCycle, setSelectedCycle] = useState<BillingCycle>('TERMLY');
  const [isUpgrading, setIsUpgrading] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    loadData();
  }, [schoolId]);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [subData, invData] = await Promise.all([
        subscriptionService.getSchoolSubscription(schoolId).catch(() => null),
        subscriptionService.getInvoices(schoolId).catch(() => []),
      ]);

      if (subData) {
        setSubscription(subData);
        setSelectedPlan(subData.plan.code);
        setSelectedCycle(subData.billingCycle);
      }
      setInvoices(invData);
    } catch (err: any) {
      setError(err?.message || 'Failed to load subscription information');
    } finally {
      setLoading(false);
    }
  };

  const handleUpgrade = async () => {
    try {
      setIsUpgrading(true);
      setError(null);
      const updated = await subscriptionService.upgradePlan(schoolId, selectedPlan, selectedCycle);
      setSubscription(updated);
      setSuccessMessage('Subscription plan updated successfully!');
      // Refresh invoices
      const invs = await subscriptionService.getInvoices(schoolId);
      setInvoices(invs);
    } catch (err: any) {
      setError(err?.message || 'Failed to update subscription plan');
    } finally {
      setIsUpgrading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center p-12">
        <RefreshCw className="h-8 w-8 animate-spin text-blue-600" />
      </div>
    );
  }

  const isTrial = subscription?.status === 'TRIAL';
  const daysLeft = subscription?.daysRemainingInTrial ?? 0;
  const studentCount = subscription?.studentCount || 0;

  return (
    <div className="space-y-8">
      {/* 1. Header & Trial Banner */}
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-900">Subscription & Billing</h2>
        <p className="text-sm text-slate-500">
          Manage your school's operating package, student billing rates, and invoice history.
        </p>
      </div>

      {successMessage && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-sm flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-emerald-600" />
            <span>{successMessage}</span>
          </div>
          <button onClick={() => setSuccessMessage(null)} className="text-emerald-600 hover:text-emerald-800">
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-red-800 text-sm flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Trial Countdown Hero Card */}
      {isTrial && (
        <div className="bg-gradient-to-r from-blue-700 via-indigo-700 to-blue-800 text-white p-6 rounded-2xl shadow-lg relative overflow-hidden">
          <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
            <div className="space-y-2 max-w-xl">
              <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/20 text-white text-xs font-semibold">
                <Sparkles className="h-3.5 w-3.5 text-blue-200" />
                <span>30-Day Complimentary School Trial</span>
              </div>
              <h3 className="text-2xl font-black">
                {daysLeft > 0 ? `${daysLeft} Days Remaining in Your Free Trial` : 'Your Free Trial Has Expired'}
              </h3>
              <p className="text-blue-100 text-sm">
                Your school currently has unrestricted access to all Full FinTech & Academic features. Select your preferred subscription package below to ensure continuous service after your trial ends.
              </p>
            </div>
            <div className="shrink-0 flex items-center gap-3">
              <div className="bg-white/10 backdrop-blur-md px-5 py-3 rounded-xl border border-white/20 text-center">
                <div className="text-2xl font-black">{studentCount}</div>
                <div className="text-xs text-blue-200">Active Students</div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 2. Current Plan Overview */}
      {subscription && (
        <div className="grid md:grid-cols-3 gap-6">
          <Card>
            <CardHeader className="pb-2">
              <CardDescription className="text-xs">Current Plan</CardDescription>
              <CardTitle className="text-xl font-bold flex items-center justify-between">
                <span>{subscription.plan.name}</span>
                <Badge className={isTrial ? 'bg-amber-100 text-amber-800 border-amber-300' : 'bg-emerald-100 text-emerald-800'}>
                  {subscription.status}
                </Badge>
              </CardTitle>
            </CardHeader>
            <CardContent className="text-xs text-slate-500 space-y-2">
              <p>{subscription.plan.description}</p>
              <div className="pt-2 flex items-center gap-2">
                <span className="font-semibold text-slate-700">Online Payments:</span>
                {subscription.plan.hasOnlinePayments ? (
                  <Badge variant="outline" className="text-emerald-700 bg-emerald-50">Enabled</Badge>
                ) : (
                  <Badge variant="outline" className="text-slate-600 bg-slate-100">Disabled (Manual Only)</Badge>
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardDescription className="text-xs">Billing & Student Rate</CardDescription>
              <CardTitle className="text-xl font-bold">
                ₦{subscription.billingCycle === 'TERMLY' 
                  ? subscription.plan.pricePerStudentTermly.toLocaleString()
                  : subscription.plan.pricePerStudentAnnually.toLocaleString()}
                <span className="text-xs text-slate-500 font-normal"> / student / {subscription.billingCycle.toLowerCase()}</span>
              </CardTitle>
            </CardHeader>
            <CardContent className="text-xs text-slate-500 space-y-2">
              <p>Billing Cycle: <strong>{subscription.billingCycle}</strong></p>
              <p>Enrolled Students: <strong>{studentCount}</strong></p>
              <p>Term Estimated Cost: <strong>₦{(studentCount * subscription.plan.pricePerStudentTermly).toLocaleString()}</strong></p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardDescription className="text-xs">Period Dates</CardDescription>
              <CardTitle className="text-xl font-bold flex items-center gap-2">
                <Clock className="h-5 w-5 text-blue-600" />
                <span className="text-base">
                  {isTrial ? 'Trial Expiry' : 'Renewal Due'}
                </span>
              </CardTitle>
            </CardHeader>
            <CardContent className="text-xs text-slate-500 space-y-2">
              {isTrial ? (
                <>
                  <p>Trial Started: <strong>{subscription.trialStartDate ? new Date(subscription.trialStartDate).toLocaleDateString() : 'N/A'}</strong></p>
                  <p>Trial Ends: <strong className="text-amber-700">{subscription.trialEndDate ? new Date(subscription.trialEndDate).toLocaleDateString() : 'N/A'}</strong></p>
                  <p>Grace Period Until: <strong>{subscription.gracePeriodEndDate ? new Date(subscription.gracePeriodEndDate).toLocaleDateString() : 'N/A'}</strong></p>
                </>
              ) : (
                <>
                  <p>Period Start: <strong>{subscription.currentPeriodStart ? new Date(subscription.currentPeriodStart).toLocaleDateString() : 'N/A'}</strong></p>
                  <p>Period End: <strong>{subscription.currentPeriodEnd ? new Date(subscription.currentPeriodEnd).toLocaleDateString() : 'N/A'}</strong></p>
                </>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {/* 3. Choose or Change Package */}
      <div className="space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h3 className="text-lg font-bold text-slate-900">Select or Upgrade Plan</h3>
            <p className="text-xs text-slate-500">
              Pick the right tier for your school based on whether you want parents to pay fees online.
            </p>
          </div>

          {/* Billing cycle toggle */}
          <div className="inline-flex bg-slate-100 p-1 rounded-xl border border-slate-200">
            <button
              onClick={() => setSelectedCycle('TERMLY')}
              className={`px-4 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                selectedCycle === 'TERMLY'
                  ? 'bg-white text-blue-700 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Termly (3x / Year)
            </button>
            <button
              onClick={() => setSelectedCycle('ANNUALLY')}
              className={`px-4 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1 ${
                selectedCycle === 'ANNUALLY'
                  ? 'bg-white text-blue-700 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              Annual Billing
              <span className="bg-emerald-100 text-emerald-700 text-[10px] font-bold px-1.5 py-0.2 rounded-full">
                -20%
              </span>
            </button>
          </div>
        </div>

        <div className="grid md:grid-cols-2 gap-6">
          {/* Plan 1: Academic Essentials */}
          <Card
            className={`cursor-pointer transition-all border-2 ${
              selectedPlan === 'ACADEMIC_ESSENTIALS'
                ? 'border-blue-600 bg-blue-50/20 shadow-md'
                : 'border-slate-200 hover:border-slate-300'
            }`}
            onClick={() => setSelectedPlan('ACADEMIC_ESSENTIALS')}
          >
            <CardHeader>
              <div className="flex items-center justify-between">
                <Badge variant="outline" className="text-xs">Cheaper Option • No Gateway</Badge>
                {selectedPlan === 'ACADEMIC_ESSENTIALS' && (
                  <CheckCircle2 className="h-5 w-5 text-blue-600" />
                )}
              </div>
              <CardTitle className="text-xl font-bold">Academic Essentials</CardTitle>
              <CardDescription className="text-xs">
                For schools collecting fees offline or into traditional bank accounts.
              </CardDescription>
              <div className="pt-2 text-2xl font-black text-slate-900">
                ₦{selectedCycle === 'TERMLY' ? '500' : '1,250'}
                <span className="text-xs text-slate-500 font-normal"> / student / {selectedCycle.toLowerCase()}</span>
              </div>
            </CardHeader>
            <CardContent className="space-y-2 text-xs text-slate-600">
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-emerald-600" /> Continuous assessment & exam scores</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-emerald-600" /> Automated rankings & PDF report cards</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-emerald-600" /> Live parent result checking portal</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-emerald-600" /> Daily attendance roll & absence alerts</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-emerald-600" /> Automated SMS & email announcements</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-emerald-600" /> Manual fee ledger & offline tracking</div>
              <div className="flex items-center gap-2 text-slate-400"><X className="h-4 w-4" /> <span className="line-through">Online parent fee payment gateway</span></div>
            </CardContent>
          </Card>

          {/* Plan 2: Full FinTech Suite */}
          <Card
            className={`cursor-pointer transition-all border-2 ${
              selectedPlan === 'FULL_SUITE'
                ? 'border-blue-600 bg-blue-50/20 shadow-md'
                : 'border-slate-200 hover:border-slate-300'
            }`}
            onClick={() => setSelectedPlan('FULL_SUITE')}
          >
            <CardHeader>
              <div className="flex items-center justify-between">
                <Badge className="bg-blue-600 text-white text-xs">All-In-One Complete</Badge>
                {selectedPlan === 'FULL_SUITE' && (
                  <CheckCircle2 className="h-5 w-5 text-blue-600" />
                )}
              </div>
              <CardTitle className="text-xl font-bold">Full FinTech & Academic Suite</CardTitle>
              <CardDescription className="text-xs">
                Accept online payments from parents with instant reconciliation + all academic tools.
              </CardDescription>
              <div className="pt-2 text-2xl font-black text-blue-600">
                ₦{selectedCycle === 'TERMLY' ? '850' : '2,100'}
                <span className="text-xs text-slate-500 font-normal"> / student / {selectedCycle.toLowerCase()}</span>
              </div>
            </CardHeader>
            <CardContent className="space-y-2 text-xs text-slate-600">
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-blue-600" /> Everything in Academic Essentials</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-blue-600" /> Online payment gateway (Cards, Transfers, USSD)</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-blue-600" /> Instant automated payment reconciliation</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-blue-600" /> Dynamic installment invoices for parents</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-blue-600" /> Digital receipts with QR verification</div>
              <div className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-blue-600" /> Automated debt-recovery SMS reminders</div>
            </CardContent>
          </Card>
        </div>

        <div className="flex justify-end pt-2">
          <Button
            onClick={handleUpgrade}
            disabled={isUpgrading}
            className="bg-blue-600 hover:bg-blue-700 text-white font-semibold shadow-sm"
          >
            {isUpgrading ? 'Processing Plan Change...' : 'Confirm Plan Selection'}
            <ArrowRight className="ml-2 h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* 4. Billing Invoices */}
      {invoices.length > 0 && (
        <div className="space-y-4 pt-4 border-t border-slate-200">
          <h3 className="text-lg font-bold text-slate-900">Subscription Invoice History</h3>
          <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-xs">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4">Invoice #</th>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4">Students</th>
                  <th className="py-3 px-4">Cycle</th>
                  <th className="py-3 px-4">Amount</th>
                  <th className="py-3 px-4">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {invoices.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-50/50">
                    <td className="py-3 px-4 font-mono font-medium">{inv.invoiceNumber}</td>
                    <td className="py-3 px-4">{new Date(inv.createdAt).toLocaleDateString()}</td>
                    <td className="py-3 px-4">{inv.studentCount}</td>
                    <td className="py-3 px-4">{inv.billingCycle}</td>
                    <td className="py-3 px-4 font-bold">₦{inv.amount.toLocaleString()}</td>
                    <td className="py-3 px-4">
                      <Badge variant="outline" className={inv.status === 'PAID' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}>
                        {inv.status}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};

export default SubscriptionManagementSection;
