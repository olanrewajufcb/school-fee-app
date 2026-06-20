import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  ArrowRight,
  BadgeCheck,
  CalendarDays,
  Clock,
  CreditCard,
  Download,
  FileText,
  GraduationCap,
  Loader2,
  LogOut,
  MessageCircle,
  Phone,
  Receipt,
  RefreshCw,
  ShieldCheck,
  Sunrise,
  Sunset,
  UserCheck,
  UserRound,
  Wallet,
} from 'lucide-react';
import { useAuth } from '@/components/auth/AuthProvider';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  type ChildProfile,
  type ChildResultSummary,
  extractCheckoutUrl,
  type PaymentHistoryItem,
  type PublishedTermResult,
  type ReceiptDetail,
  type StudentFee,
  type StudentResult,
  parentService,
} from '@/services/parentService';
import {
  attendanceService,
  type ParentAttendanceResponse,
} from '@/services/attendanceService';

type Section = 'overview' | 'fees' | 'payments' | 'results' | 'attendance' | 'receipts';

const sections: Array<{ id: Section; label: string; icon: React.ComponentType<{ className?: string }> }> = [
  { id: 'overview', label: 'Overview', icon: Wallet },
  { id: 'fees', label: 'Fees', icon: CreditCard },
  { id: 'payments', label: 'Payments', icon: Receipt },
  { id: 'results', label: 'Results', icon: GraduationCap },
  { id: 'attendance', label: 'Attendance', icon: UserCheck },
  { id: 'receipts', label: 'Receipts', icon: FileText },
];

export const ParentDashboard: React.FC = () => {
  const { user, isLoading: authLoading, logout } = useAuth();
  const [activeSection, setActiveSection] = useState<Section>('overview');
  const [children, setChildren] = useState<ChildProfile[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState('');
  const [feesByStudent, setFeesByStudent] = useState<Record<string, StudentFee[]>>({});
  const [paymentHistory, setPaymentHistory] = useState<PaymentHistoryItem[]>([]);
  const [resultSummaries, setResultSummaries] = useState<ChildResultSummary[]>([]);
  const [selectedResult, setSelectedResult] = useState<StudentResult | null>(null);
  const [resultHistory, setResultHistory] = useState<PublishedTermResult[]>([]);
  const [selectedReceipt, setSelectedReceipt] = useState<ReceiptDetail | null>(null);
  const [receiptLookup, setReceiptLookup] = useState('');
  const [paymentDialog, setPaymentDialog] = useState(false);
  const [selectedFeeIds, setSelectedFeeIds] = useState<string[]>([]);
  const [partialAmounts, setPartialAmounts] = useState<Record<string, string>>({});
  const [paymentMode, setPaymentMode] = useState<Record<string, 'full' | 'partial'>>({});
  const [latestPaymentId, setLatestPaymentId] = useState('');
  const [todayAttendance, setTodayAttendance] = useState<ParentAttendanceResponse[]>([]);
  const [attendanceDetail, setAttendanceDetail] = useState<ParentAttendanceResponse[]>([]);
  const [attendanceDate, setAttendanceDate] = useState(new Date().toISOString().split('T')[0]);
  const [isAttendanceLoading, setIsAttendanceLoading] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const selectedChild = children.find((child) => child.studentId === selectedStudentId) ?? children[0];
  const selectedFees = selectedChild ? feesByStudent[selectedChild.studentId] ?? [] : [];

  const getFeePaymentAmount = (fee: StudentFee) => {
    const mode = paymentMode[fee.studentFeeId] || 'full';
    if (mode === 'partial') {
      const val = partialAmounts[fee.studentFeeId] || '';
      const num = Number(val);
      if (isNaN(num) || num <= 0) return 0;
      return num;
    }
    return Number(fee.balance || 0);
  };

  const selectedPaymentTotal = selectedFees
    .filter((fee) => selectedFeeIds.includes(fee.studentFeeId))
    .reduce((sum, fee) => sum + getFeePaymentAmount(fee), 0);

  const totals = useMemo(() => {
    const allFees = Object.values(feesByStudent).flat();
    const totalDue = allFees.reduce((sum, fee) => sum + Number(fee.balance || 0), 0);
    const overdue = allFees.filter((fee) => fee.balance > 0 && (fee.status === 'OVERDUE' || Number(fee.daysUntilDue ?? 1) < 0));
    const paidCount = allFees.filter((fee) => fee.status === 'PAID' || fee.balance <= 0).length;
    return { totalDue, overdueCount: overdue.length, paidCount, feesCount: allFees.length };
  }, [feesByStudent]);

  const urgentFee = useMemo(() => {
    const entries = children.flatMap((child) =>
      (feesByStudent[child.studentId] ?? [])
        .filter((fee) => fee.balance > 0)
        .map((fee) => ({ child, fee })),
    );
    return entries.sort((a, b) => Number(a.fee.daysUntilDue ?? 999) - Number(b.fee.daysUntilDue ?? 999))[0];
  }, [children, feesByStudent]);

  useEffect(() => {
    if (!authLoading && user) {
      void loadDashboard().then(() => {
        const params = new URLSearchParams(window.location.search);
        const reference = params.get('reference');
        if (reference) {
          setLatestPaymentId(reference);
          void refreshPaymentStatus(reference).then(() => {
            const url = new URL(window.location.href);
            url.searchParams.delete('reference');
            url.searchParams.delete('status');
            window.history.replaceState({}, '', url.pathname + url.search);
          });
        }
      });
    }
  }, [authLoading, user?.userId]);

  useEffect(() => {
    if (activeSection !== 'attendance' || !selectedStudentId) return;

    const refreshAttendance = () => {
      void loadTodayAttendance();
      void loadChildAttendance(selectedStudentId, attendanceDate);
    };
    refreshAttendance();
    const intervalId = window.setInterval(refreshAttendance, 30_000);
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') refreshAttendance();
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => {
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [activeSection, selectedStudentId, attendanceDate]);

  const loadDashboard = async () => {
    setIsLoading(true);
    setError(null);
    setNotice(null);
    try {
      const [childrenResult, historyResult, resultsResult] = await Promise.allSettled([
        parentService.getChildren(),
        parentService.getPaymentHistory(),
        parentService.getChildrenResults(),
      ]);

      const nextChildren = childrenResult.status === 'fulfilled' ? childrenResult.value : [];
      setChildren(nextChildren);
      setSelectedStudentId((current) => current || nextChildren[0]?.studentId || '');

      if (historyResult.status === 'fulfilled') setPaymentHistory(historyResult.value.content ?? []);
      if (resultsResult.status === 'fulfilled') setResultSummaries(resultsResult.value ?? []);

      const feeEntries = await Promise.all(
        nextChildren.map(async (child) => {
          try {
            return [child.studentId, await parentService.getStudentFees(child.studentId)] as const;
          } catch {
            return [child.studentId, []] as const;
          }
        }),
      );
      setFeesByStudent(Object.fromEntries(feeEntries));

      await loadTodayAttendance();

      const params = new URLSearchParams(window.location.search);
      const resultStudentId = params.get('studentId');
      const resultTermId = params.get('termId');
      if (resultStudentId && resultTermId) {
        const [result, history] = await Promise.all([
          parentService.getStudentResult(resultStudentId, resultTermId),
          parentService.getPublishedStudentResults(resultStudentId),
        ]);
        setSelectedStudentId(resultStudentId);
        setSelectedResult(result);
        setResultHistory(history);
        setActiveSection('results');
      }

      if ([childrenResult, historyResult, resultsResult].some((result) => result.status === 'rejected')) {
        setNotice('Some parent data could not refresh. Available child records are shown.');
      }
    } catch (err) {
      setError(readError(err, 'Unable to load parent dashboard.'));
    } finally {
      setIsLoading(false);
    }
  };

  const loadTodayAttendance = async () => {
    try {
      const result = await attendanceService.getMyChildrenAttendance();
      setTodayAttendance(result);
    } catch (err) {
      console.error('Failed to load today attendance', err);
    }
  };

  const loadChildAttendance = async (studentId: string, date?: string) => {
    setIsAttendanceLoading(true);
    setAttendanceDetail([]);
    try {
      const result = await attendanceService.getMyChildAttendance(studentId, date);
      setAttendanceDetail(result);
    } catch (err) {
      console.error('Failed to load child attendance', err);
      setAttendanceDetail([]);
    } finally {
      setIsAttendanceLoading(false);
    }
  };

  const openPayment = (studentId: string, feeId?: string, initialMode?: 'full' | 'partial') => {
    setSelectedStudentId(studentId);
    const fees = feesByStudent[studentId] ?? [];
    const ids = feeId ? [feeId] : fees.filter((fee) => fee.balance > 0 && fee.status !== 'PAID').map((fee) => fee.studentFeeId);
    setSelectedFeeIds(ids);
    if (feeId) {
      if (initialMode) {
        setPaymentMode(prev => ({ ...prev, [feeId]: initialMode }));
        if (initialMode === 'partial') {
          const fee = fees.find(f => f.studentFeeId === feeId);
          const balance = fee?.balance ?? 0;
          setPartialAmounts(prev => ({ ...prev, [feeId]: String(Math.max(1000, Math.min(20000, balance))) }));
        }
      } else {
        setPaymentMode(prev => ({ ...prev, [feeId]: 'full' }));
      }
    } else {
      // Clear specific payment settings if paying multiple
      setPaymentMode({});
      setPartialAmounts({});
    }
    setPaymentDialog(true);
  };

  const initiatePayment = async () => {
    if (!selectedFeeIds.length || selectedPaymentTotal <= 0) {
      setError('Select at least one unpaid fee before continuing.');
      return;
    }

    // Client-side validations for partial payment amounts
    for (const id of selectedFeeIds) {
      const mode = paymentMode[id] || 'full';
      if (mode === 'partial') {
        const val = partialAmounts[id] || '';
        const num = Number(val);
        const fee = selectedFees.find(f => f.studentFeeId === id);
        const feeName = fee ? `"${fee.structureName}"` : 'Selected fee';
        const balance = fee ? fee.balance : 0;

        if (isNaN(num) || num < 1000) {
          setError(`Minimum payment amount for ${feeName} is ₦1,000.`);
          return;
        }
        if (num > balance) {
          setError(`Amount for ${feeName} cannot exceed the outstanding balance of ${formatCurrency(balance)}.`);
          return;
        }
      }
    }

    await runAction(async () => {
      const response = await parentService.initiatePayment({
        studentFeeIds: selectedFeeIds,
        paymentMethod: 'PAYSTACK',
        phoneNumber: user?.phoneNumber ?? '',
        amount: selectedPaymentTotal,
      });
      setLatestPaymentId(response.paymentId);
      setPaymentDialog(false);
      const checkoutUrl = extractCheckoutUrl(response);
      if (checkoutUrl) {
        window.location.assign(checkoutUrl);
        return;
      }
      setNotice('Payment initialized. Check the payment status from the Payments tab.');
      await loadDashboard();
    });
  };

  const refreshPaymentStatus = async (paymentId = latestPaymentId) => {
    if (!paymentId) {
      setError('Select a payment to check its status.');
      return;
    }
    await runAction(async () => {
      const status = await parentService.getPaymentStatus(paymentId);
      setNotice(`Payment status: ${status.status}${status.receipt?.receiptNumber ? `, receipt ${status.receipt.receiptNumber}` : ''}.`);
      if (status.receipt?.receiptNumber) {
        await loadReceipt(status.receipt.receiptNumber);
        setActiveSection('receipts');
      } else {
        setActiveSection('payments');
      }
      await loadDashboard();
    });
  };

  const loadResult = async (summary: ChildResultSummary) => {
    const termId = summary.termId;
    if (!termId) {
      setError('This result summary does not include a term ID yet, so the full report card cannot be opened.');
      return;
    }
    await runAction(async () => {
      const [result, history] = await Promise.all([
        parentService.getStudentResult(summary.studentId, termId),
        parentService.getPublishedStudentResults(summary.studentId),
      ]);
      setSelectedStudentId(summary.studentId);
      setSelectedResult(result);
      setResultHistory(history);
    });
  };

  const loadHistoricalResult = async (term: PublishedTermResult) => {
    if (!selectedStudentId) return;
    await runAction(async () => {
      const result = await parentService.getStudentResult(selectedStudentId, term.termId);
      setSelectedResult(result);
    });
  };

  const downloadStudentResult = async () => {
    const studentId = selectedResult?.student?.studentId;
    const termId = selectedResult?.term?.termId;
    if (!studentId || !termId) {
      setError('Open a published report card before downloading.');
      return;
    }
    await runAction(async () => {
      const blob = await parentService.downloadStudentResultPdf(studentId, termId);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${selectedResult.student?.fullName ?? 'student'}-${selectedResult.term?.name ?? 'result'}.pdf`
        .replace(/\s+/g, '-')
        .toLowerCase();
      link.click();
      URL.revokeObjectURL(url);
      setNotice('Report card download started.');
    });
  };

  const shareStudentResult = async () => {
    const studentId = selectedResult?.student?.studentId;
    const termId = selectedResult?.term?.termId;
    if (!studentId || !termId) {
      setError('Open a published report card before sharing.');
      return;
    }
    await runAction(async () => {
      const share = await parentService.shareStudentResult(
        studentId,
        termId,
        'WHATSAPP',
        user?.phoneNumber ?? 'WhatsApp',
      );
      window.open(`https://wa.me/?text=${encodeURIComponent(share.shareText)}`, '_blank', 'noopener,noreferrer');
      setNotice('WhatsApp share message prepared.');
    });
  };

  const loadReceipt = async (receiptNumber = receiptLookup) => {
    if (!receiptNumber.trim()) {
      setError('Enter a receipt number first.');
      return;
    }
    await runAction(async () => {
      const receipt = await parentService.getReceipt(receiptNumber.trim());
      setSelectedReceipt(receipt);
      setReceiptLookup(receipt.receiptNumber);
    });
  };

  const downloadReceipt = async (receiptNumber: string) => {
    await runAction(async () => {
      const blob = await parentService.downloadReceiptPdf(receiptNumber);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `receipt-${receiptNumber}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
      setNotice('Receipt download started.');
    });
  };

  const shareReceipt = async (receiptNumber: string, channel: 'SMS' | 'WHATSAPP') => {
    await runAction(async () => {
      await parentService.shareReceipt(receiptNumber, channel, user?.phoneNumber ?? '');
      setNotice(`Receipt shared via ${channel === 'SMS' ? 'SMS' : 'WhatsApp'}.`);
    });
  };

  const runAction = async (action: () => Promise<void>) => {
    setIsSaving(true);
    setError(null);
    setNotice(null);
    try {
      await action();
    } catch (err) {
      setError(readError(err, 'Action failed. Please try again.'));
    } finally {
      setIsSaving(false);
    }
  };

  if (authLoading || isLoading) {
    return <LoadingScreen />;
  }

  if (!user) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
        <div className="w-full max-w-md rounded-md border border-amber-200 bg-white p-6 text-center">
          <AlertCircle className="mx-auto h-10 w-10 text-amber-600" />
          <h1 className="mt-4 text-xl font-semibold text-slate-950">Please sign in</h1>
          <p className="mt-2 text-sm text-slate-500">Your parent portal is available after login.</p>
          <Button className="mt-5 bg-slate-950 text-white hover:bg-slate-800" onClick={() => window.location.assign('/login')}>Go to Login</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-100 text-slate-950">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-72 border-r border-slate-200 bg-white lg:flex lg:flex-col">
        <div className="px-6 py-6">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-md bg-emerald-700 text-white">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-slate-950">{user.schoolName ?? 'SchoolFee'}</p>
              <p className="text-xs text-slate-500">Parent Portal</p>
            </div>
          </div>
        </div>

        <nav className="flex-1 space-y-1 px-3">
          {sections.map((section) => {
            const Icon = section.icon;
            const selected = activeSection === section.id;
            return (
              <button
                key={section.id}
                type="button"
                onClick={() => setActiveSection(section.id)}
                className={`flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium transition ${
                  selected ? 'bg-slate-950 text-white' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'
                }`}
              >
                <Icon className="h-4 w-4" />
                {section.label}
              </button>
            );
          })}
        </nav>

        <div className="border-t border-slate-200 p-4">
          <Button variant="outline" className="w-full justify-start border-slate-200 text-red-600 hover:bg-red-50 hover:text-red-700" onClick={logout}>
            <LogOut className="mr-2 h-4 w-4" />
            Sign Out
          </Button>
        </div>
      </aside>

      <main className="lg:pl-72">
        <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/95 px-4 py-4 backdrop-blur md:px-8">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="outline" className="rounded-full border-slate-300 bg-white text-slate-700">PARENT</Badge>
                <Badge className="rounded-full bg-emerald-100 text-emerald-700 hover:bg-emerald-100">{children.length} child{children.length === 1 ? '' : 'ren'}</Badge>
              </div>
              <h1 className="mt-2 text-2xl font-semibold tracking-tight text-slate-950 md:text-3xl">
                Welcome back, {user.firstName || 'Parent'}
              </h1>
              <p className="mt-1 text-sm text-slate-500">Fees, receipts, and published results in one place.</p>
            </div>
            <div className="grid gap-2 md:grid-cols-[180px_240px_auto]">
              <Select value={activeSection} onValueChange={(value) => setActiveSection(value as Section)}>
                <SelectTrigger className="bg-white lg:hidden">
                  <SelectValue placeholder="Section" />
                </SelectTrigger>
                <SelectContent>
                  {sections.map((section) => (
                    <SelectItem key={section.id} value={section.id}>{section.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select value={selectedChild?.studentId ?? ''} onValueChange={setSelectedStudentId}>
                <SelectTrigger className="bg-white">
                  <SelectValue placeholder="Select child" />
                </SelectTrigger>
                <SelectContent>
                  {children.map((child) => (
                    <SelectItem key={child.studentId} value={child.studentId}>{child.firstName} {child.lastName}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button variant="outline" onClick={() => void loadDashboard()} disabled={isSaving}>
                <RefreshCw className="mr-2 h-4 w-4" />
                Refresh
              </Button>
            </div>
          </div>
        </header>

        <div className="px-4 py-6 md:px-8">
          {error && <Notice tone="error" message={error} />}
          {notice && <Notice tone="info" message={notice} />}

          {!children.length ? (
            <EmptyState />
          ) : (
            <>
              {activeSection === 'overview' && (
                <OverviewSection
                  children={children}
                  feesByStudent={feesByStudent}
                  resultSummaries={resultSummaries}
                  totalDue={totals.totalDue}
                  overdueCount={totals.overdueCount}
                  paymentHistory={paymentHistory}
                  todayAttendance={todayAttendance}
                  urgentFee={urgentFee}
                  onPay={openPayment}
                  onGoTo={setActiveSection}
                  onSelectChild={setSelectedStudentId}
                  onOpenReceipt={loadReceipt}
                />
              )}

              {activeSection === 'fees' && selectedChild && (
                <FeesSection
                  child={selectedChild}
                  fees={selectedFees}
                  selectedFeeIds={selectedFeeIds}
                  onToggleFee={(feeId) => {
                    setSelectedFeeIds((ids) => ids.includes(feeId) ? ids.filter((id) => id !== feeId) : [...ids, feeId]);
                  }}
                  onPaySelected={() => setPaymentDialog(true)}
                  onPayFee={(feeId) => openPayment(selectedChild.studentId, feeId)}
                />
              )}

              {activeSection === 'payments' && (
                <PaymentsSection
                  history={paymentHistory}
                  latestPaymentId={latestPaymentId}
                  isSaving={isSaving}
                  onSetLatestPaymentId={setLatestPaymentId}
                  onRefreshStatus={refreshPaymentStatus}
                  onOpenReceipt={loadReceipt}
                />
              )}

              {activeSection === 'results' && (
                <ResultsSection
                  summaries={resultSummaries}
                  selectedResult={selectedResult}
                  history={resultHistory}
                  isSaving={isSaving}
                  onOpenSummary={loadResult}
                  onOpenHistory={loadHistoricalResult}
                  onDownload={() => void downloadStudentResult()}
                  onShare={() => void shareStudentResult()}
                />
              )}

              {activeSection === 'attendance' && (
                <AttendanceSection
                  children={children}
                  selectedStudentId={selectedStudentId}
                  todayAttendance={todayAttendance}
                  attendanceDetail={attendanceDetail}
                  attendanceDate={attendanceDate}
                  isLoading={isAttendanceLoading}
                  onSelectChild={(id) => { setSelectedStudentId(id); void loadChildAttendance(id, attendanceDate); }}
                  onDateChange={(date) => { setAttendanceDate(date); void loadChildAttendance(selectedStudentId, date); }}
                  onRefresh={() => { void loadTodayAttendance(); void loadChildAttendance(selectedStudentId, attendanceDate); }}
                />
              )}

              {activeSection === 'receipts' && (
                <ReceiptsSection
                  history={paymentHistory}
                  receiptLookup={receiptLookup}
                  selectedReceipt={selectedReceipt}
                  isSaving={isSaving}
                  onLookupChange={setReceiptLookup}
                  onLoadReceipt={loadReceipt}
                  onDownload={downloadReceipt}
                  onShare={shareReceipt}
                />
              )}
            </>
          )}
        </div>
      </main>

      <PaymentDialog
        open={paymentDialog}
        child={selectedChild}
        allFees={selectedFees}
        selectedFeeIds={selectedFeeIds}
        onToggleFee={(feeId) => {
          setSelectedFeeIds((ids) => ids.includes(feeId) ? ids.filter((id) => id !== feeId) : [...ids, feeId]);
        }}
        paymentMode={paymentMode}
        onSetPaymentMode={(feeId, mode) => setPaymentMode(prev => ({ ...prev, [feeId]: mode }))}
        partialAmounts={partialAmounts}
        onSetPartialAmount={(feeId, amountStr) => setPartialAmounts(prev => ({ ...prev, [feeId]: amountStr }))}
        amount={selectedPaymentTotal}
        isSaving={isSaving}
        onOpenChange={setPaymentDialog}
        onPay={initiatePayment}
      />
    </div>
  );
};

function OverviewSection({
  children,
  feesByStudent,
  resultSummaries,
  totalDue,
  overdueCount,
  paymentHistory,
  todayAttendance,
  urgentFee,
  onPay,
  onGoTo,
  onSelectChild,
  onOpenReceipt,
}: {
  children: ChildProfile[];
  feesByStudent: Record<string, StudentFee[]>;
  resultSummaries: ChildResultSummary[];
  totalDue: number;
  overdueCount: number;
  paymentHistory: PaymentHistoryItem[];
  todayAttendance: ParentAttendanceResponse[];
  urgentFee?: { child: ChildProfile; fee: StudentFee };
  onPay: (studentId: string, feeId?: string, initialMode?: 'full' | 'partial') => void;
  onGoTo: (section: Section) => void;
  onSelectChild: (studentId: string) => void;
  onOpenReceipt: (receiptNumber: string) => void;
}) {
  return (
    <div className="space-y-6">
      {urgentFee && (
        <section className={`rounded-md border p-4 ${isOverdue(urgentFee.fee) ? 'border-red-200 bg-red-50' : 'border-amber-200 bg-amber-50'}`}>
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className={`text-sm font-semibold ${isOverdue(urgentFee.fee) ? 'text-red-700' : 'text-amber-800'}`}>
                {isOverdue(urgentFee.fee) ? 'Fee overdue' : 'Fee reminder'}
              </p>
              <p className="mt-1 text-sm text-slate-700">
                {urgentFee.child.firstName}'s {urgentFee.fee.structureName} balance is {formatCurrency(urgentFee.fee.balance)}
                {urgentFee.fee.dueDate ? `, due ${formatDate(urgentFee.fee.dueDate)}` : ''}.
              </p>
            </div>
            <Button className="bg-slate-950 text-white hover:bg-slate-800" onClick={() => onPay(urgentFee.child.studentId, urgentFee.fee.studentFeeId, 'full')}>
              Pay Now
              <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        </section>
      )}

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <Metric icon={Wallet} label="Total Due" value={formatCurrency(totalDue)} detail={`${overdueCount} overdue fee${overdueCount === 1 ? '' : 's'}`} />
        <Metric icon={UserRound} label="Children" value={formatNumber(children.length)} detail="Linked to your account" />
        <Metric icon={Receipt} label="Receipts" value={formatNumber(paymentHistory.filter((item) => item.receiptNumber).length)} detail="Available from payments" />
        <Metric icon={GraduationCap} label="Results" value={formatNumber(resultSummaries.length)} detail="Published summaries" />
      </section>

      <section className="grid gap-4">
        {children.map((child) => {
          const fees = feesByStudent[child.studentId] ?? [];
          const result = resultSummaries.find((item) => item.studentId === child.studentId);
          const childTodayAttendance = todayAttendance.filter((a) => a.studentId === child.studentId);
          const morningRecord = childTodayAttendance.find(hasMorningAttendance);
          const afternoonRecord = childTodayAttendance.find(hasAfternoonAttendance);

          const currentTermFee = fees.find(f => f.isCurrentTerm);
          const upcomingTermFee = fees.find(f => f.isUpcomingTerm);

          const findReceiptNumber = (fee: StudentFee) => {
            const match = paymentHistory.find(item => 
              item.receiptNumber && 
              (item.description?.toLowerCase().includes(fee.structureName.toLowerCase()) || 
               item.description?.toLowerCase().includes(fee.termName.toLowerCase()))
            );
            return match?.receiptNumber;
          };

          return (
            <div key={child.studentId} className="rounded-md border border-slate-200 bg-white p-5">
              <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                <div>
                  <h2 className="text-lg font-semibold text-slate-950">{child.firstName} {child.lastName}</h2>
                  <p className="mt-1 text-sm text-slate-500">{child.currentClass ?? 'Class not assigned'} · {child.admissionNumber}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="outline" onClick={() => { onSelectChild(child.studentId); onGoTo('fees'); }}>View Fees</Button>
                </div>
              </div>

              {/* Detailed Term Fee Cards */}
              <div className="mt-4 space-y-4">
                {currentTermFee ? (
                  <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 transition-all hover:bg-slate-100/50">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">📅 Current Term</span>
                          <span className="text-xs font-semibold text-slate-700">— {currentTermFee.termName}</span>
                        </div>
                        <div className="mt-2 flex items-center gap-2">
                          <span className="text-lg font-bold text-slate-900">{formatCurrency(currentTermFee.totalAmount)}</span>
                          <span className="text-xs text-slate-500">·</span>
                          <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium border ${
                            currentTermFee.balance <= 0 || currentTermFee.status === 'PAID'
                              ? 'bg-emerald-50 text-emerald-700 border-emerald-200' 
                              : 'bg-amber-50 text-amber-700 border-amber-200'
                          }`}>
                            {currentTermFee.balance <= 0 || currentTermFee.status === 'PAID' ? '✅ Paid' : '⚠️ Partially Paid'}
                          </span>
                        </div>
                      </div>
                      {findReceiptNumber(currentTermFee) && (
                        <Button 
                          variant="outline" 
                          size="sm" 
                          onClick={() => {
                            onOpenReceipt(findReceiptNumber(currentTermFee)!);
                            onGoTo('receipts');
                          }}
                          className="bg-white border-slate-200 text-slate-700 hover:bg-slate-50"
                        >
                          View Receipt
                        </Button>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="rounded-lg border border-dashed border-slate-200 p-4 text-center text-sm text-slate-500 bg-slate-50">
                    No active fee structure assigned for the current term.
                  </div>
                )}

                {/* Upcoming Term Card */}
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 transition-all hover:bg-slate-100/50">
                  {upcomingTermFee ? (
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">📅 Upcoming Term</span>
                          <span className="text-xs font-semibold text-slate-700">— {upcomingTermFee.termName}</span>
                        </div>
                        <div className="mt-2 flex items-center gap-2">
                          <span className="text-lg font-bold text-slate-900">{formatCurrency(upcomingTermFee.totalAmount)}</span>
                          <span className="text-xs text-slate-500">·</span>
                          <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium border ${
                            upcomingTermFee.balance <= 0 || upcomingTermFee.status === 'PAID'
                              ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                              : 'bg-amber-100 text-amber-700 border-amber-200'
                          }`}>
                            {upcomingTermFee.balance <= 0 || upcomingTermFee.status === 'PAID' ? '✅ Paid' : '⬜ Not yet paid'}
                          </span>
                        </div>
                        {upcomingTermFee.dueDate && (
                          <p className="text-xs text-slate-500 mt-1">
                            Due: {formatDate(upcomingTermFee.dueDate)} ({upcomingTermFee.daysUntilDue} days)
                          </p>
                        )}
                      </div>
                      {upcomingTermFee.balance > 0 && (
                        <div className="flex gap-2 self-end sm:self-start mt-2 sm:mt-0">
                          <Button 
                            size="sm" 
                            className="bg-slate-950 text-white hover:bg-slate-800"
                            onClick={() => onPay(child.studentId, upcomingTermFee.studentFeeId, 'full')}
                          >
                            Pay Now
                          </Button>
                          <Button 
                            size="sm" 
                            variant="outline" 
                            onClick={() => onPay(child.studentId, upcomingTermFee.studentFeeId, 'partial')}
                          >
                            Pay Partial Amount
                          </Button>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">📅 Upcoming Term</span>
                        <p className="mt-1 text-sm font-semibold text-slate-400">Not yet available</p>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Today's Attendance Widget */}
              <div className="mt-4 rounded-lg border border-blue-100 bg-blue-50/40 p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <UserCheck className="h-4 w-4 text-blue-600" />
                    <span className="text-xs font-semibold uppercase tracking-wider text-blue-700">Today's Attendance</span>
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 gap-1 border-blue-200 px-2 text-[11px] text-blue-700 hover:bg-blue-100"
                    onClick={() => { onSelectChild(child.studentId); onGoTo('attendance'); }}
                  >
                    View History
                    <ArrowRight className="h-3 w-3" />
                  </Button>
                </div>
                <div className="mt-3 grid gap-2 sm:grid-cols-2">
                  {/* Morning */}
                  <div className="flex items-center gap-3 rounded-md border border-white/80 bg-white/70 px-3 py-2.5">
                    <div className={`flex h-8 w-8 items-center justify-center rounded-full ${
                      morningRecord
                        ? attendanceStatusTone(morningRecord.arrivalStatus)
                        : 'bg-slate-100 text-slate-400'
                    }`}>
                      <Sunrise className="h-4 w-4" />
                    </div>
                    <div>
                      {morningRecord?.arrivalStatus === 'ABSENT' ? (
                        <>
                          <p className="text-sm font-medium text-red-700">Absent</p>
                          <p className="text-xs text-red-600">No morning arrival recorded</p>
                        </>
                      ) : morningRecord ? (
                        <>
                          <p className="text-sm font-medium text-slate-900">
                            {morningRecord.arrivalTime
                              ? `Arrived at ${formatAttendanceTime(morningRecord.arrivalTime)}`
                              : attendanceStatusLabel(morningRecord.arrivalStatus)}
                            {morningRecord.arrivalStatus === 'LATE' ? ' · Late' : morningRecord.arrivalTime ? ' · On time' : ''}
                          </p>
                          <p className="text-xs text-slate-500">Brought by: {morningRecord.broughtBy ?? 'N/A'}</p>
                        </>
                      ) : (
                        <>
                          <p className="text-sm font-medium text-slate-400">Morning Arrival</p>
                          <p className="text-xs text-slate-400">Not marked yet</p>
                        </>
                      )}
                    </div>
                  </div>
                  {/* Afternoon */}
                  <div className="flex items-center gap-3 rounded-md border border-white/80 bg-white/70 px-3 py-2.5">
                    <div className={`flex h-8 w-8 items-center justify-center rounded-full ${
                      afternoonRecord
                        ? attendanceStatusTone(afternoonRecord.departureStatus)
                        : 'bg-slate-100 text-slate-400'
                    }`}>
                      <Sunset className="h-4 w-4" />
                    </div>
                    <div>
                      {afternoonRecord?.departureStatus === 'ABSENT' ? (
                        <>
                          <p className="text-sm font-medium text-red-700">Absent</p>
                          <p className="text-xs text-red-600">No afternoon departure recorded</p>
                        </>
                      ) : afternoonRecord ? (
                        <>
                          <p className="text-sm font-medium text-slate-900">
                            {afternoonRecord.departureTime
                              ? `Picked up at ${formatAttendanceTime(afternoonRecord.departureTime)}`
                              : attendanceStatusLabel(afternoonRecord.departureStatus)}
                          </p>
                          <p className="text-xs text-slate-500">
                            By: {afternoonRecord.pickedUpBy ?? afternoonRecord.pickUpPersonName ?? 'N/A'}
                            {afternoonRecord.pickUpPersonName && afternoonRecord.pickUpPersonName !== afternoonRecord.pickedUpBy
                              ? ` (${afternoonRecord.pickUpPersonName})` : ''}
                          </p>
                        </>
                      ) : (
                        <>
                          <p className="text-sm font-medium text-slate-400">Afternoon Departure</p>
                          <p className="text-xs text-slate-400">Not marked yet</p>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              <div className="mt-4 grid gap-3 md:grid-cols-2">
                <InfoStrip icon={GraduationCap} label="Results" value={result?.summary ? `${result.summary.average.toFixed(1)}% (${result.summary.grade})` : 'Not published'} tone={result?.summary ? 'ok' : 'neutral'} />
                <InfoStrip
                  icon={CalendarDays}
                  label="Attendance"
                  value={result?.attendance && result.attendance.daysOpen > 0
                    ? `${result.attendance.daysPresent}/${result.attendance.daysOpen} days (${result.attendance.attendanceRate.toFixed(0)}%)`
                    : 'Not published'
                  }
                  tone={result?.attendance && result.attendance.daysOpen > 0 ? 'ok' : 'neutral'}
                />
              </div>
            </div>
          );
        })}
      </section>
    </div>
  );
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Attendance Section — parent view per-child with date navigation
 * ───────────────────────────────────────────────────────────────────────────── */

function AttendanceSection({
  children: childrenList,
  selectedStudentId,
  todayAttendance,
  attendanceDetail,
  attendanceDate,
  isLoading,
  onSelectChild,
  onDateChange,
  onRefresh,
}: {
  children: ChildProfile[];
  selectedStudentId: string;
  todayAttendance: ParentAttendanceResponse[];
  attendanceDetail: ParentAttendanceResponse[];
  attendanceDate: string;
  isLoading: boolean;
  onSelectChild: (studentId: string) => void;
  onDateChange: (date: string) => void;
  onRefresh: () => void;
}) {
  const todayStr = new Date().toISOString().split('T')[0];
  const isToday = attendanceDate === todayStr;
  const records = isToday && !attendanceDetail.length
    ? todayAttendance.filter((a) => a.studentId === selectedStudentId)
    : attendanceDetail;

  const morningRecord = records.find(hasMorningAttendance);
  const afternoonRecord = records.find(hasAfternoonAttendance);
  const selectedChild = childrenList.find((c) => c.studentId === selectedStudentId) ?? childrenList[0];

  const formattedDate = (() => {
    try {
      return new Date(attendanceDate + 'T00:00:00').toLocaleDateString('en-NG', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      });
    } catch {
      return attendanceDate;
    }
  })();

  const navigateDate = (delta: number) => {
    const d = new Date(attendanceDate + 'T00:00:00');
    d.setDate(d.getDate() + delta);
    const iso = d.toISOString().split('T')[0];
    if (iso <= todayStr) onDateChange(iso);
  };

  return (
    <div className="grid gap-6 xl:grid-cols-[320px_1fr]">
      {/* Child Selector */}
      <section className="rounded-md border border-slate-200 bg-white p-4">
        <p className="text-sm font-medium text-slate-500">My Children</p>
        <div className="mt-4 space-y-2">
          {childrenList.map((child) => {
            const childToday = todayAttendance.filter((a) => a.studentId === child.studentId);
            const hasArrival = childToday.some(hasMorningAttendance);
            const hasDeparture = childToday.some(hasAfternoonAttendance);
            return (
              <button
                key={child.studentId}
                type="button"
                onClick={() => onSelectChild(child.studentId)}
                className={`w-full rounded-md border px-3 py-3 text-left text-sm transition ${
                  selectedStudentId === child.studentId ? 'border-slate-950 bg-slate-50' : 'border-slate-200 hover:bg-slate-50'
                }`}
              >
                <p className="font-medium text-slate-900">{child.firstName} {child.lastName}</p>
                <p className="mt-0.5 text-xs text-slate-500">{child.currentClass ?? 'Class not set'}</p>
                <div className="mt-2 flex gap-1.5">
                  <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${
                    hasArrival ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-400'
                  }`}>
                    <Sunrise className="h-2.5 w-2.5" />
                    {hasArrival ? 'Arrived' : 'Pending'}
                  </span>
                  <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${
                    hasDeparture ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-400'
                  }`}>
                    <Sunset className="h-2.5 w-2.5" />
                    {hasDeparture ? 'Picked up' : 'Pending'}
                  </span>
                </div>
              </button>
            );
          })}
        </div>
      </section>

      {/* Attendance Detail */}
      <section className="space-y-5">
        {/* Date Navigator */}
        <div className="flex flex-col gap-3 rounded-md border border-slate-200 bg-white p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <UserCheck className="h-5 w-5 text-blue-600" />
              <h2 className="text-lg font-semibold text-slate-950">
                {selectedChild ? `${selectedChild.firstName} ${selectedChild.lastName}` : 'Attendance'}
              </h2>
            </div>
            <p className="mt-1 text-sm text-slate-500">{selectedChild?.currentClass ?? ''} · {formattedDate}</p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => navigateDate(-1)} className="h-8 px-2">
              ←
            </Button>
            <input
              type="date"
              value={attendanceDate}
              max={todayStr}
              onChange={(e) => onDateChange(e.target.value)}
              className="h-8 rounded-md border border-slate-200 bg-white px-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-950"
            />
            <Button variant="outline" size="sm" onClick={() => navigateDate(1)} disabled={attendanceDate >= todayStr} className="h-8 px-2">
              →
            </Button>
            {!isToday && (
              <Button variant="outline" size="sm" onClick={() => onDateChange(todayStr)} className="h-8 gap-1 text-xs">
                Today
              </Button>
            )}
            <Button variant="outline" size="sm" onClick={onRefresh} className="h-8 px-2">
              <RefreshCw className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>

        {/* Loading */}
        {isLoading ? (
          <div className="flex min-h-[200px] items-center justify-center rounded-md border border-dashed border-slate-300 bg-white">
            <div className="text-center">
              <Loader2 className="mx-auto h-6 w-6 animate-spin text-slate-500" />
              <p className="mt-2 text-sm text-slate-500">Loading attendance…</p>
            </div>
          </div>
        ) : !morningRecord && !afternoonRecord ? (
          /* No Records */
          <div className="flex min-h-[200px] items-center justify-center rounded-md border border-dashed border-slate-300 bg-white">
            <div className="text-center">
              <CalendarDays className="mx-auto h-10 w-10 text-slate-300" />
              <p className="mt-3 text-sm font-medium text-slate-500">No attendance records</p>
              <p className="mt-1 text-xs text-slate-400">
                {isToday ? 'Attendance has not been marked for today yet.' : `No records found for ${formattedDate}.`}
              </p>
            </div>
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            {/* Morning Arrival Card */}
            <div className={`rounded-lg border p-5 ${
              morningRecord ? 'border-emerald-200 bg-emerald-50/30' : 'border-slate-200 bg-slate-50'
            }`}>
              <div className="flex items-center gap-2">
                <div className={`flex h-10 w-10 items-center justify-center rounded-full ${
                  morningRecord
                    ? attendanceStatusTone(morningRecord.arrivalStatus)
                    : 'bg-slate-200 text-slate-400'
                }`}>
                  <Sunrise className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-slate-700">Morning Arrival</p>
                  <p className="text-xs text-slate-400">7:30 – 8:30 AM window</p>
                </div>
              </div>

              {morningRecord ? (
                <div className="mt-4 space-y-3">
                  <div className="flex items-center gap-2">
                    {morningRecord.arrivalStatus === 'ABSENT' ? (
                      <Badge className="bg-red-100 text-red-700 hover:bg-red-100">Absent</Badge>
                    ) : morningRecord.arrivalStatus === 'LATE' ? (
                      <Badge className="bg-amber-100 text-amber-700 hover:bg-amber-100">Late</Badge>
                    ) : morningRecord.arrivalStatus === 'EXCUSED' ? (
                      <Badge className="bg-blue-100 text-blue-700 hover:bg-blue-100">Excused</Badge>
                    ) : (
                      <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">On Time</Badge>
                    )}
                  </div>
                  {morningRecord.arrivalTime && (
                    <div className="flex items-center gap-2 text-sm">
                      <Clock className="h-4 w-4 text-slate-400" />
                      <span className="font-medium text-slate-900">Arrived at {formatAttendanceTime(morningRecord.arrivalTime)}</span>
                    </div>
                  )}
                  {morningRecord.broughtBy && (
                    <div className="flex items-center gap-2 text-sm">
                      <UserRound className="h-4 w-4 text-slate-400" />
                      <span className="text-slate-700">Brought by: <span className="font-medium">{morningRecord.broughtBy}</span></span>
                    </div>
                  )}
                </div>
              ) : (
                <p className="mt-4 text-sm text-slate-400">Not marked yet.</p>
              )}
            </div>

            {/* Afternoon Departure Card */}
            <div className={`rounded-lg border p-5 ${
              afternoonRecord ? 'border-emerald-200 bg-emerald-50/30' : 'border-slate-200 bg-slate-50'
            }`}>
              <div className="flex items-center gap-2">
                <div className={`flex h-10 w-10 items-center justify-center rounded-full ${
                  afternoonRecord
                    ? attendanceStatusTone(afternoonRecord.departureStatus)
                    : 'bg-slate-200 text-slate-400'
                }`}>
                  <Sunset className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-slate-700">Afternoon Departure</p>
                  <p className="text-xs text-slate-400">1:30 – 2:30 PM window</p>
                </div>
              </div>

              {afternoonRecord ? (
                <div className="mt-4 space-y-3">
                  <StatusBadge status={afternoonRecord.departureStatus === 'PRESENT' ? 'PICKED UP' : afternoonRecord.departureStatus} />
                  {afternoonRecord.departureTime && (
                    <div className="flex items-center gap-2 text-sm">
                      <Clock className="h-4 w-4 text-slate-400" />
                      <span className="font-medium text-slate-900">Left at {formatAttendanceTime(afternoonRecord.departureTime)}</span>
                    </div>
                  )}
                  {(afternoonRecord.pickedUpBy || afternoonRecord.pickUpPersonName) && (
                    <div className="flex items-center gap-2 text-sm">
                      <UserRound className="h-4 w-4 text-slate-400" />
                      <span className="text-slate-700">
                        By: <span className="font-medium">{afternoonRecord.pickedUpBy ?? afternoonRecord.pickUpPersonName ?? 'N/A'}</span>
                        {afternoonRecord.pickUpPersonName && afternoonRecord.pickUpPersonName !== afternoonRecord.pickedUpBy
                          ? ` (${afternoonRecord.pickUpPersonName})` : ''}
                      </span>
                    </div>
                  )}
                  {afternoonRecord.pickUpPersonPhone && (
                    <div className="flex items-center gap-2 text-sm">
                      <Phone className="h-4 w-4 text-slate-400" />
                      <a className="text-slate-700 hover:underline" href={`tel:${afternoonRecord.pickUpPersonPhone}`}>
                        {afternoonRecord.pickUpPersonPhone}
                      </a>
                    </div>
                  )}
                </div>
              ) : (
                <p className="mt-4 text-sm text-slate-400">Not marked yet.</p>
              )}
            </div>
          </div>
        )}

        {/* SMS Info */}
        <div className="rounded-md border border-blue-100 bg-blue-50/30 p-4">
          <div className="flex items-start gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-blue-100 text-blue-600">
              <MessageCircle className="h-4 w-4" />
            </div>
            <div>
              <p className="text-sm font-medium text-blue-800">Automatic Email Notifications</p>
              <p className="mt-1 text-xs text-blue-600">
                You'll receive an email when the teacher submits attendance —
                including arrival time, who brought your child, departure time, and who picked them up.
              </p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}

function FeesSection({
  child,
  fees,
  selectedFeeIds,
  onToggleFee,
  onPaySelected,
  onPayFee,
}: {
  child: ChildProfile;
  fees: StudentFee[];
  selectedFeeIds: string[];
  onToggleFee: (feeId: string) => void;
  onPaySelected: () => void;
  onPayFee: (feeId: string) => void;
}) {
  const selectedTotal = fees.filter((fee) => selectedFeeIds.includes(fee.studentFeeId)).reduce((sum, fee) => sum + Number(fee.balance || 0), 0);
  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
      <section className="rounded-md border border-slate-200 bg-white">
        <div className="flex flex-col gap-3 border-b border-slate-200 p-5 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-950">{child.firstName} {child.lastName}</h2>
            <p className="text-sm text-slate-500">{child.currentClass ?? 'Current class'} fee breakdown</p>
          </div>
          <Button disabled={selectedTotal <= 0} onClick={onPaySelected} className="bg-slate-950 text-white hover:bg-slate-800">
            Pay Selected {selectedTotal > 0 ? formatCurrency(selectedTotal) : ''}
          </Button>
        </div>
        <div className="divide-y divide-slate-100">
          {fees.map((fee) => (
            <div key={fee.studentFeeId} className="p-5">
              <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                <label className="flex gap-3">
                  <input
                    type="checkbox"
                    className="mt-1 h-4 w-4"
                    disabled={fee.balance <= 0 || fee.status === 'PAID'}
                    checked={selectedFeeIds.includes(fee.studentFeeId)}
                    onChange={() => onToggleFee(fee.studentFeeId)}
                  />
                  <span>
                    <span className="block font-semibold text-slate-950">{fee.structureName}</span>
                    <span className="mt-1 block text-sm text-slate-500">{fee.termName} · {fee.dueDate ? `Due ${formatDate(fee.dueDate)}` : 'No due date'}</span>
                  </span>
                </label>
                <div className="text-left md:text-right">
                  <p className="font-semibold text-slate-950">{formatCurrency(fee.balance)}</p>
                  <StatusBadge status={fee.status} />
                </div>
              </div>
              <div className="mt-4 rounded-md bg-slate-50 p-4">
                {fee.items?.map((item) => (
                  <div key={`${fee.studentFeeId}-${item.description}`} className="flex items-center justify-between py-1 text-sm">
                    <span className="text-slate-600">{item.description}{!item.isMandatory ? ' (optional)' : ''}</span>
                    <span className="font-medium text-slate-900">{formatCurrency(item.amount)}</span>
                  </div>
                ))}
                <div className="mt-2 flex items-center justify-between border-t border-slate-200 pt-3 text-sm">
                  <span className="font-medium text-slate-700">Paid</span>
                  <span className="font-semibold text-emerald-700">{formatCurrency(fee.amountPaid)}</span>
                </div>
              </div>
              {fee.balance > 0 && (
                <div className="mt-4 flex justify-end">
                  <Button variant="outline" onClick={() => onPayFee(fee.studentFeeId)}>Pay this fee</Button>
                </div>
              )}
            </div>
          ))}
          {!fees.length && <EmptyBlock message="No fee records returned for this child." />}
        </div>
      </section>

      <section className="rounded-md border border-slate-200 bg-white p-5">
        <p className="text-sm font-medium text-slate-500">Payment Method</p>
        <div className="mt-4 rounded-md border border-emerald-200 bg-emerald-50 p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-semibold text-emerald-900">Paystack</p>
              <p className="mt-1 text-sm text-emerald-700">Card, bank transfer, USSD and other Paystack channels.</p>
            </div>
            <BadgeCheck className="h-5 w-5 text-emerald-700" />
          </div>
        </div>
        <div className="mt-5 space-y-3 text-sm">
          <MetricRow label="Selected" value={formatNumber(selectedFeeIds.length)} />
          <MetricRow label="Amount" value={formatCurrency(selectedTotal)} />
        </div>
      </section>
    </div>
  );
}

function PaymentsSection({
  history,
  latestPaymentId,
  isSaving,
  onSetLatestPaymentId,
  onRefreshStatus,
  onOpenReceipt,
}: {
  history: PaymentHistoryItem[];
  latestPaymentId: string;
  isSaving: boolean;
  onSetLatestPaymentId: (value: string) => void;
  onRefreshStatus: (paymentId?: string) => void;
  onOpenReceipt: (receiptNumber: string) => void;
}) {
  return (
    <div className="space-y-6">
      <section className="rounded-md border border-slate-200 bg-white p-5">
        <div className="grid gap-3 md:grid-cols-[1fr_auto]">
          <div>
            <Label htmlFor="payment-id">Check payment status</Label>
            <Input id="payment-id" className="mt-2" value={latestPaymentId} onChange={(event) => onSetLatestPaymentId(event.target.value)} placeholder="Payment ID" />
          </div>
          <Button className="self-end bg-slate-950 text-white hover:bg-slate-800" disabled={isSaving || !latestPaymentId.trim()} onClick={() => onRefreshStatus()}>
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
            Check Status
          </Button>
        </div>
      </section>
      <HistoryTable history={history} onOpenReceipt={onOpenReceipt} onRefreshStatus={onRefreshStatus} />
    </div>
  );
}

function ResultsSection({
  summaries,
  selectedResult,
  history,
  isSaving,
  onOpenSummary,
  onOpenHistory,
  onDownload,
  onShare,
}: {
  summaries: ChildResultSummary[];
  selectedResult: StudentResult | null;
  history: PublishedTermResult[];
  isSaving: boolean;
  onOpenSummary: (summary: ChildResultSummary) => void;
  onOpenHistory: (term: PublishedTermResult) => void;
  onDownload: () => void;
  onShare: () => void;
}) {
  return (
    <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
      <section className="rounded-md border border-slate-200 bg-white p-4">
        <p className="text-sm font-medium text-slate-500">Published Results</p>
        <div className="mt-4 space-y-2">
          {summaries.map((summary) => (
            <button
              key={`${summary.studentId}-${summary.termName}`}
              type="button"
              disabled={!summary.termId || !summary.summary}
              onClick={() => onOpenSummary(summary)}
              className="w-full rounded-md border border-slate-200 px-3 py-3 text-left text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:opacity-75"
            >
              <p className="font-medium text-slate-900">{summary.firstName} {summary.lastName}</p>
              <p className="mt-1 text-slate-500">
                {summary.termName ?? 'Current term'} · {summary.summary
                  ? `${summary.summary.average.toFixed(1)}% (${summary.summary.grade ?? '-'})`
                  : 'Results not yet published'}
              </p>
              {summary.summary && Number(summary.summary.classPosition ?? 0) > 0 && (
                <p className="mt-1 text-xs text-emerald-700">
                  Position: {ordinal(Number(summary.summary.classPosition))} of {summary.summary.outOf}
                </p>
              )}
              {!summary.termId && <p className="mt-2 text-xs text-amber-700">Full report needs term ID from backend.</p>}
              {summary.termId && !summary.summary && (
                <p className="mt-2 text-xs text-slate-500">Check back after results are published.</p>
              )}
            </button>
          ))}
          {!summaries.length && <EmptyBlock message="No published results yet." />}
        </div>

        {history.some((term) => term.termId !== selectedResult?.term?.termId) && (
          <div className="mt-6 border-t border-slate-200 pt-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Previous terms</p>
            <div className="mt-2 space-y-2">
              {history
                .filter((term) => term.termId !== selectedResult?.term?.termId)
                .map((term) => (
                <button
                  key={term.termId}
                  type="button"
                  onClick={() => onOpenHistory(term)}
                  className="w-full rounded-md border border-slate-200 px-3 py-2.5 text-left hover:bg-slate-50"
                >
                  <p className="text-sm font-medium text-slate-900">{term.termName}</p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {term.sessionName} · {term.average.toFixed(1)}% ({term.overallGrade ?? '-'})
                  </p>
                </button>
                ))}
            </div>
          </div>
        )}
      </section>

      <section className="rounded-md border border-slate-200 bg-white">
        <div className="flex flex-col gap-3 border-b border-slate-200 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-950">{selectedResult?.student?.fullName ?? 'Report Card'}</h2>
            <p className="text-sm text-slate-500">
              {selectedResult?.term
                ? `${selectedResult.student?.className ?? ''} · ${selectedResult.term.name} · ${selectedResult.term.sessionName}`
                : 'Select a published result'}
            </p>
          </div>
          {selectedResult && (
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" size="sm" disabled={isSaving} onClick={onDownload}>
                <Download className="mr-2 h-4 w-4" />
                Download PDF
              </Button>
              <Button size="sm" disabled={isSaving} onClick={onShare} className="bg-emerald-600 text-white hover:bg-emerald-700">
                <MessageCircle className="mr-2 h-4 w-4" />
                Share via WhatsApp
              </Button>
            </div>
          )}
        </div>
        {isSaving ? (
          <div className="flex min-h-64 items-center justify-center">
            <Loader2 className="h-6 w-6 animate-spin text-slate-500" />
          </div>
        ) : selectedResult ? (
          <ReportCard result={selectedResult} />
        ) : (
          <EmptyBlock message="Select a published result to open the full report card." />
        )}
      </section>
    </div>
  );
}

function ReceiptsSection({
  history,
  receiptLookup,
  selectedReceipt,
  isSaving,
  onLookupChange,
  onLoadReceipt,
  onDownload,
  onShare,
}: {
  history: PaymentHistoryItem[];
  receiptLookup: string;
  selectedReceipt: ReceiptDetail | null;
  isSaving: boolean;
  onLookupChange: (value: string) => void;
  onLoadReceipt: (receiptNumber?: string) => void;
  onDownload: (receiptNumber: string) => void;
  onShare: (receiptNumber: string, channel: 'SMS' | 'WHATSAPP') => void;
}) {
  const receiptItems = history.filter((item) => item.receiptNumber);
  return (
    <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
      <section className="rounded-md border border-slate-200 bg-white p-4">
        <Label htmlFor="receipt-number">Receipt number</Label>
        <div className="mt-2 flex gap-2">
          <Input id="receipt-number" value={receiptLookup} onChange={(event) => onLookupChange(event.target.value)} placeholder="RCP-2026-..." />
          <Button disabled={isSaving} onClick={() => onLoadReceipt()}>Open</Button>
        </div>
        <p className="mt-5 text-sm font-medium text-slate-500">Recent receipts</p>
        <div className="mt-3 space-y-2">
          {receiptItems.map((item) => (
            <button
              key={item.paymentId}
              type="button"
              onClick={() => onLoadReceipt(item.receiptNumber)}
              className="w-full rounded-md border border-slate-200 px-3 py-3 text-left text-sm hover:bg-slate-50"
            >
              <p className="font-medium text-slate-900">{item.receiptNumber}</p>
              <p className="mt-1 text-slate-500">{formatCurrency(item.amount)} · {formatDateTime(item.date)}</p>
            </button>
          ))}
          {!receiptItems.length && <EmptyBlock message="No receipts in payment history yet." />}
        </div>
      </section>

      <section className="rounded-md border border-slate-200 bg-white">
        {selectedReceipt ? (
          <ReceiptPanel receipt={selectedReceipt} onDownload={onDownload} onShare={onShare} isSaving={isSaving} />
        ) : (
          <EmptyBlock message="Open a receipt to view, download, or share it." />
        )}
      </section>
    </div>
  );
}

function PaymentDialog({
  open,
  child,
  allFees,
  selectedFeeIds,
  onToggleFee,
  paymentMode,
  onSetPaymentMode,
  partialAmounts,
  onSetPartialAmount,
  amount,
  isSaving,
  onOpenChange,
  onPay,
}: {
  open: boolean;
  child?: ChildProfile;
  allFees: StudentFee[];
  selectedFeeIds: string[];
  onToggleFee: (feeId: string) => void;
  paymentMode: Record<string, 'full' | 'partial'>;
  onSetPaymentMode: (feeId: string, mode: 'full' | 'partial') => void;
  partialAmounts: Record<string, string>;
  onSetPartialAmount: (feeId: string, amountStr: string) => void;
  amount: number;
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onPay: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="bg-white sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Pay School Fees</DialogTitle>
          <DialogDescription>
            {child ? `Student: ${child.firstName} ${child.lastName}` : 'Select fees to pay'}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label className="text-slate-700 font-medium">Select fees to pay:</Label>
            <div className="rounded-md border border-slate-200 divide-y divide-slate-100 max-h-80 overflow-y-auto bg-slate-50/50">
              {allFees.map((fee) => {
                const isPaid = fee.balance <= 0 || fee.status === 'PAID';
                const isChecked = selectedFeeIds.includes(fee.studentFeeId);
                const mode = paymentMode[fee.studentFeeId] || 'full';
                const partialVal = partialAmounts[fee.studentFeeId] || '';

                return (
                  <div key={fee.studentFeeId} className="p-4 bg-white transition-colors">
                    <div className="flex items-start justify-between gap-3">
                      <label className="flex items-start gap-3 cursor-pointer select-none">
                        <input
                          type="checkbox"
                          disabled={isPaid}
                          checked={isChecked && !isPaid}
                          onChange={() => onToggleFee(fee.studentFeeId)}
                          className="mt-1 h-4 w-4 rounded border-slate-300 text-slate-950 focus:ring-slate-950"
                        />
                        <div>
                          <p className={`text-sm font-semibold ${isPaid ? 'text-slate-400 line-through' : 'text-slate-900'}`}>
                            {fee.termName}
                          </p>
                          <p className="text-xs text-slate-500 mt-0.5">
                            {fee.structureName}
                          </p>
                          {isPaid ? (
                            <span className="inline-flex items-center gap-1 mt-1 text-xs font-semibold text-emerald-700 bg-emerald-50 border border-emerald-100 rounded px-1.5 py-0.5">
                              ✅ Already Paid
                            </span>
                          ) : (
                            fee.dueDate && (
                              <p className="text-xs text-slate-400 mt-1">
                                Due: {formatDate(fee.dueDate)}
                              </p>
                            )
                          )}
                        </div>
                      </label>
                      <p className={`text-sm font-semibold ${isPaid ? 'text-slate-400' : 'text-slate-950'}`}>
                        {formatCurrency(fee.balance)}
                      </p>
                    </div>

                    {isChecked && !isPaid && (
                      <div className="mt-3 ml-7 rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-3">
                        <div className="flex flex-wrap items-center gap-4 text-xs font-medium">
                          <label className="flex items-center gap-2 cursor-pointer">
                            <input
                              type="radio"
                              name={`payment-mode-${fee.studentFeeId}`}
                              checked={mode === 'full'}
                              onChange={() => onSetPaymentMode(fee.studentFeeId, 'full')}
                              className="h-4 w-4 border-slate-300 text-slate-950 focus:ring-slate-950"
                            />
                            <span className="text-slate-700">Full Payment: {formatCurrency(fee.balance)}</span>
                          </label>
                          <label className="flex items-center gap-2 cursor-pointer">
                            <input
                              type="radio"
                              name={`payment-mode-${fee.studentFeeId}`}
                              checked={mode === 'partial'}
                              onChange={() => onSetPaymentMode(fee.studentFeeId, 'partial')}
                              className="h-4 w-4 border-slate-300 text-slate-950 focus:ring-slate-950"
                            />
                            <span className="text-slate-700">Partial Payment</span>
                          </label>
                        </div>

                        {mode === 'partial' && (
                          <div className="space-y-1">
                            <div className="relative rounded-md shadow-sm">
                              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                                <span className="text-slate-500 text-xs">₦</span>
                              </div>
                              <Input
                                type="number"
                                min="1000"
                                max={fee.balance}
                                value={partialVal}
                                onChange={(e) => onSetPartialAmount(fee.studentFeeId, e.target.value)}
                                className="pl-7 block w-full border-slate-300 focus:ring-slate-950 focus:border-slate-950 sm:text-sm"
                                placeholder="Amount (min ₦1,000)"
                              />
                            </div>
                            <p className="text-[10px] text-slate-500 font-medium">
                              (min ₦1,000)
                            </p>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
              {!allFees.length && (
                <p className="p-4 text-center text-sm text-slate-500">No school fee records available.</p>
              )}
            </div>
          </div>
          <div className="rounded-md bg-slate-50 p-4">
            <MetricRow label="Payment method" value="Paystack" />
            <MetricRow label="Total Selected" value={formatCurrency(amount)} />
          </div>
          <div className="flex gap-3">
            <Button variant="outline" className="w-1/3" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button 
              className="flex-1 bg-slate-950 text-white hover:bg-slate-800" 
              disabled={isSaving || amount <= 0} 
              onClick={onPay}
            >
              {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <CreditCard className="mr-2 h-4 w-4" />}
              Pay {formatCurrency(amount)} &rarr;
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function HistoryTable({ history, onOpenReceipt, onRefreshStatus }: { history: PaymentHistoryItem[]; onOpenReceipt: (receiptNumber: string) => void; onRefreshStatus: (paymentId: string) => void }) {
  return (
    <section className="rounded-md border border-slate-200 bg-white">
      <div className="border-b border-slate-200 p-5">
        <h2 className="text-lg font-semibold text-slate-950">Payment History</h2>
        <p className="text-sm text-slate-500">Recent school fee payments</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-5 py-3">Date</th>
              <th className="px-5 py-3">Description</th>
              <th className="px-5 py-3">Amount</th>
              <th className="px-5 py-3">Status</th>
              <th className="px-5 py-3">Actions</th>
            </tr>
          </thead>
          <tbody>
            {history.map((item) => (
              <tr key={item.paymentId} className="border-b border-slate-100">
                <td className="px-5 py-4 text-slate-600">{formatDateTime(item.date)}</td>
                <td className="px-5 py-4">
                  <p className="font-medium text-slate-900">{item.description ?? 'School fee payment'}</p>
                  <p className="text-xs text-slate-500">{item.paymentMethod}</p>
                </td>
                <td className="px-5 py-4 font-semibold text-slate-950">{formatCurrency(item.amount)}</td>
                <td className="px-5 py-4"><StatusBadge status={item.status} /></td>
                <td className="px-5 py-4">
                  <div className="flex flex-wrap gap-2">
                    <Button variant="outline" size="sm" onClick={() => onRefreshStatus(item.paymentId)}>Status</Button>
                    {item.receiptNumber && <Button variant="outline" size="sm" onClick={() => onOpenReceipt(item.receiptNumber!)}>Receipt</Button>}
                  </div>
                </td>
              </tr>
            ))}
            {!history.length && (
              <tr>
                <td colSpan={5} className="px-5 py-10 text-center text-sm text-slate-500">No payment history yet.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function ReportCard({ result }: { result: StudentResult }) {
  const totals = (result.subjects ?? []).reduce(
    (total, subject) => ({
      ca: total.ca + Number(subject.caTotal ?? 0),
      exam: total.exam + Number(subject.examScore ?? 0),
      final: total.final + Number(subject.finalScore ?? 0),
    }),
    { ca: 0, exam: 0, final: 0 },
  );
  return (
    <div>
      <div className="grid gap-3 border-b border-slate-200 p-5 sm:grid-cols-2 xl:grid-cols-5">
        <MetricRow label="Total Score" value={`${Number(result.summary?.totalScore ?? 0).toFixed(1)} / ${result.summary?.totalMaxScore ?? 0}`} />
        <MetricRow label="Average" value={`${Number(result.summary?.average ?? 0).toFixed(1)}%`} />
        <MetricRow label="Grade" value={result.summary?.overallGrade ?? '-'} />
        <MetricRow label="Subjects Passed" value={`${result.summary?.subjectsPassed ?? 0} / ${result.summary?.subjectsTaken ?? 0}`} />
        <MetricRow label="Position" value={result.ranking ? `${ordinal(result.ranking.classPosition)} of ${result.ranking.outOf}` : '-'} />
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-5 py-3">Subject</th>
              <th className="px-5 py-3">CA</th>
              <th className="px-5 py-3">Exam</th>
              <th className="px-5 py-3">Final</th>
              <th className="px-5 py-3">Grade</th>
              <th className="px-5 py-3">Pos</th>
            </tr>
          </thead>
          <tbody>
            {(result.subjects ?? []).map((subject) => (
              <tr key={subject.subjectId} className="border-b border-slate-100">
                <td className="px-5 py-4 font-medium text-slate-900">{subject.subjectName}</td>
                <td className="px-5 py-4 text-slate-600">{Number(subject.caTotal ?? 0).toFixed(1)}</td>
                <td className="px-5 py-4 text-slate-600">{Number(subject.examScore ?? 0).toFixed(1)}</td>
                <td className="px-5 py-4 font-semibold text-slate-950">{Number(subject.finalScore ?? 0).toFixed(1)}</td>
                <td className="px-5 py-4"><Badge variant="outline">{subject.grade ?? '-'}</Badge></td>
                <td className="px-5 py-4 text-slate-600">{subject.subjectPosition ? ordinal(subject.subjectPosition) : '-'}</td>
              </tr>
            ))}
            {(result.subjects?.length ?? 0) > 0 && (
              <tr className="bg-slate-50 font-semibold text-slate-900">
                <td className="px-5 py-4">Total</td>
                <td className="px-5 py-4">{totals.ca.toFixed(1)}</td>
                <td className="px-5 py-4">{totals.exam.toFixed(1)}</td>
                <td className="px-5 py-4">{totals.final.toFixed(1)}</td>
                <td className="px-5 py-4">—</td>
                <td className="px-5 py-4">—</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {result.attendance && (
        <div className="border-t border-slate-200 px-5 py-4">
          <p className="text-sm font-semibold text-slate-900">Attendance Summary</p>
          <p className="mt-1 text-sm text-slate-600">
            Present {result.attendance.daysPresent} of {result.attendance.daysOpen} school days
            {' '}({result.attendance.attendanceRate.toFixed(1)}%)
          </p>
        </div>
      )}
      <div className="grid gap-4 p-5 md:grid-cols-2">
        <CommentBox title="Teacher's Comment" text={result.teacherComment} />
        <CommentBox title="Principal's Comment" text={result.principalComment} />
      </div>
    </div>
  );
}

function ReceiptPanel({ receipt, onDownload, onShare, isSaving }: { receipt: ReceiptDetail; onDownload: (receiptNumber: string) => void; onShare: (receiptNumber: string, channel: 'SMS' | 'WHATSAPP') => void; isSaving: boolean }) {
  return (
    <div>
      <div className="border-b border-slate-200 p-5">
        <h2 className="text-lg font-semibold text-slate-950">Receipt {receipt.receiptNumber}</h2>
        <p className="text-sm text-slate-500">{receipt.schoolName} · {formatDateTime(receipt.paymentDate)}</p>
      </div>
      <div className="space-y-4 p-5">
        <div className="grid gap-3 md:grid-cols-2">
          <MetricRow label="Paid by" value={receipt.paidBy} />
          <MetricRow label="Amount" value={formatCurrency(receipt.amount)} />
          <MetricRow label="Method" value={receipt.paymentMethod} />
          <MetricRow label="Generated" value={formatDateTime(receipt.generatedAt)} />
        </div>
        <div className="rounded-md border border-slate-200">
          {receipt.breakdown.map((item) => (
            <div key={`${item.admissionNumber}-${item.term}-${item.amount}`} className="flex items-center justify-between border-b border-slate-100 px-4 py-3 last:border-b-0">
              <div>
                <p className="text-sm font-medium text-slate-900">{item.studentName}</p>
                <p className="text-xs text-slate-500">{item.className ?? 'Class'} · {item.term ?? 'Term'}</p>
              </div>
              <p className="text-sm font-semibold text-slate-950">{formatCurrency(item.amount)}</p>
            </div>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button disabled={isSaving} className="bg-slate-950 text-white hover:bg-slate-800" onClick={() => onDownload(receipt.receiptNumber)}>
            <Download className="mr-2 h-4 w-4" />
            Download PDF
          </Button>
          <Button disabled={isSaving} variant="outline" onClick={() => onShare(receipt.receiptNumber, 'WHATSAPP')}>
            <MessageCircle className="mr-2 h-4 w-4" />
            WhatsApp
          </Button>
          <Button disabled={isSaving} variant="outline" onClick={() => onShare(receipt.receiptNumber, 'SMS')}>
            <Phone className="mr-2 h-4 w-4" />
            SMS
          </Button>
        </div>
      </div>
    </div>
  );
}

function Metric({ icon: Icon, label, value, detail }: { icon: React.ComponentType<{ className?: string }>; label: string; value: string; detail: string }) {
  return (
    <div className="rounded-md border border-slate-200 bg-white p-5">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        <Icon className="h-5 w-5 text-slate-400" />
      </div>
      <p className="mt-3 text-2xl font-semibold text-slate-950">{value}</p>
      <p className="mt-1 text-sm text-slate-500">{detail}</p>
    </div>
  );
}

function InfoStrip({ icon: Icon, label, value, tone }: { icon: React.ComponentType<{ className?: string }>; label: string; value: string; tone: 'ok' | 'warn' | 'neutral' }) {
  const colors = {
    ok: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    warn: 'border-amber-200 bg-amber-50 text-amber-800',
    neutral: 'border-slate-200 bg-slate-50 text-slate-700',
  };
  return (
    <div className={`rounded-md border p-3 ${colors[tone]}`}>
      <div className="flex items-center gap-2">
        <Icon className="h-4 w-4" />
        <span className="text-xs font-medium uppercase">{label}</span>
      </div>
      <p className="mt-2 text-sm font-semibold">{value}</p>
    </div>
  );
}

function MetricRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2">
      <span className="text-sm text-slate-600">{label}</span>
      <span className="text-sm font-semibold text-slate-900">{value}</span>
    </div>
  );
}

function CommentBox({ title, text }: { title: string; text?: string }) {
  return (
    <div className="rounded-md border border-slate-200 bg-slate-50 p-4">
      <p className="text-sm font-semibold text-slate-900">{title}</p>
      <p className="mt-2 text-sm text-slate-600">{text || 'No comment yet.'}</p>
    </div>
  );
}

function StatusBadge({ status }: { status?: string }) {
  const normalized = status ?? 'UNKNOWN';
  const className = normalized === 'PAID' || normalized === 'COMPLETED' || normalized === 'PICKED UP'
    ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-100'
    : normalized === 'OVERDUE' || normalized === 'FAILED'
      ? 'bg-red-100 text-red-700 hover:bg-red-100'
      : 'bg-amber-100 text-amber-700 hover:bg-amber-100';
  return <Badge className={className}>{normalized}</Badge>;
}

function Notice({ tone, message }: { tone: 'error' | 'info'; message: string }) {
  const className = tone === 'error'
    ? 'mb-5 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700'
    : 'mb-5 rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-700';
  return <div className={className}>{message}</div>;
}

function EmptyBlock({ message }: { message: string }) {
  return <p className="p-8 text-center text-sm text-slate-500">{message}</p>;
}

function EmptyState() {
  return (
    <div className="rounded-md border border-dashed border-slate-300 bg-white p-10 text-center">
      <AlertCircle className="mx-auto h-10 w-10 text-amber-600" />
      <h2 className="mt-4 text-xl font-semibold text-slate-950">No children linked yet</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-slate-500">Please contact your school administrator to link your children to this parent account.</p>
    </div>
  );
}

function LoadingScreen() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100">
      <div className="text-center">
        <Loader2 className="mx-auto h-8 w-8 animate-spin text-slate-500" />
        <p className="mt-3 text-sm text-slate-500">Loading parent portal...</p>
      </div>
    </div>
  );
}

function isOverdue(fee: StudentFee) {
  return fee.status === 'OVERDUE' || Number(fee.daysUntilDue ?? 1) < 0;
}

function formatCurrency(value?: number) {
  return new Intl.NumberFormat('en-NG', {
    style: 'currency',
    currency: 'NGN',
    maximumFractionDigits: 0,
  }).format(Number(value || 0));
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('en-NG').format(value || 0);
}

function ordinal(value: number) {
  const mod100 = value % 100;
  if (mod100 >= 11 && mod100 <= 13) return `${value}th`;
  if (value % 10 === 1) return `${value}st`;
  if (value % 10 === 2) return `${value}nd`;
  if (value % 10 === 3) return `${value}rd`;
  return `${value}th`;
}

function formatDate(value?: string) {
  if (!value) return 'Not set';
  return new Intl.DateTimeFormat('en-NG', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value));
}

function formatDateTime(value?: string) {
  if (!value) return 'Not set';
  return new Intl.DateTimeFormat('en-NG', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

function hasMorningAttendance(record: ParentAttendanceResponse) {
  return Boolean(record.arrivalStatus || record.arrivalTime || record.broughtBy);
}

function hasAfternoonAttendance(record: ParentAttendanceResponse) {
  return Boolean(
    record.departureStatus ||
    record.departureTime ||
    record.pickedUpBy ||
    record.pickUpPersonName ||
    record.pickUpPersonPhone,
  );
}

function attendanceStatusLabel(status?: string) {
  if (!status) return 'Not marked';
  return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function attendanceStatusTone(status?: string) {
  if (status === 'ABSENT') return 'bg-red-100 text-red-600';
  if (status === 'LATE') return 'bg-amber-100 text-amber-600';
  if (status === 'EXCUSED') return 'bg-blue-100 text-blue-600';
  return 'bg-emerald-100 text-emerald-600';
}

function formatAttendanceTime(value?: string) {
  if (!value) return '';
  const [hours, minutes] = value.split(':').map(Number);
  if (Number.isNaN(hours) || Number.isNaN(minutes)) return value;
  const date = new Date();
  date.setHours(hours, minutes, 0, 0);
  return new Intl.DateTimeFormat('en-NG', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  }).format(date);
}

function readError(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { errors?: Array<{ message?: string }>; message?: string } } }).response;
    return response?.data?.errors?.[0]?.message || response?.data?.message || fallback;
  }
  return fallback;
}

export default ParentDashboard;
