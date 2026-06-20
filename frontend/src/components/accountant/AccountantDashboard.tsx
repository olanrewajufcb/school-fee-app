import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  Banknote,
  BellRing,
  ClipboardList,
  CreditCard,
  Download,
  FileText,
  LayoutDashboard,
  Loader2,
  LogOut,
  Receipt,
  RefreshCw,
  Search,
  Send,
  History,
  Users,
} from 'lucide-react';
import { useAuth } from '@/components/auth/AuthProvider';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import {
  accountantService,
  type DailySummary,
  type FeeDashboard,
  type FeeStructure,
  type OfflinePaymentResponse,
  type PaymentStatus,
  type ReceiptDetail,
  type StudentFee,
  type StudentSummary,
  type PaymentHistoryItem,
} from '@/services/accountantService';

type Section = 'overview' | 'offline' | 'reminders' | 'history' | 'reports' | 'receipts' | 'reference';

const sections: Array<{ id: Section; label: string; icon: React.ComponentType<{ className?: string }> }> = [
  { id: 'overview', label: 'Overview', icon: LayoutDashboard },
  { id: 'offline', label: 'Offline Payment', icon: Banknote },
  { id: 'reminders', label: 'Reminders', icon: BellRing },
  { id: 'history', label: 'Payment History', icon: History },
  { id: 'reports', label: 'Reports', icon: FileText },
  { id: 'receipts', label: 'Receipts', icon: Receipt },
  { id: 'reference', label: 'Reference', icon: ClipboardList },
];

export const AccountantDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const [activeSection, setActiveSection] = useState<Section>('overview');
  const [dashboard, setDashboard] = useState<FeeDashboard | null>(null);
  const [dailySummary, setDailySummary] = useState<DailySummary | null>(null);
  const [feeStructures, setFeeStructures] = useState<FeeStructure[]>([]);
  const [students, setStudents] = useState<StudentSummary[]>([]);
  const [studentSearch, setStudentSearch] = useState('');
  const [selectedStudentId, setSelectedStudentId] = useState('');
  const [studentFees, setStudentFees] = useState<StudentFee[]>([]);
  const [selectedFeeId, setSelectedFeeId] = useState('');
  const [offlineDialog, setOfflineDialog] = useState(false);
  const [offlineAmount, setOfflineAmount] = useState('');
  const [offlineMethod, setOfflineMethod] = useState<'CASH' | 'TRANSFER' | 'POS'>('CASH');
  const [offlineNotes, setOfflineNotes] = useState('');
  const [offlineResponse, setOfflineResponse] = useState<OfflinePaymentResponse | null>(null);
  const [selectedReminderFeeIds, setSelectedReminderFeeIds] = useState<string[]>([]);
  const [reminderChannel, setReminderChannel] = useState<'SMS' | 'WHATSAPP' | 'BOTH'>('SMS');
  const [paymentId, setPaymentId] = useState('');
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus | null>(null);
  const [receiptNumber, setReceiptNumber] = useState('');
  const [receipt, setReceipt] = useState<ReceiptDetail | null>(null);
  const [smsBalance, setSmsBalance] = useState<any | null>(null);
  const [paymentHistory, setPaymentHistory] = useState<any[]>([]);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyTotal, setHistoryTotal] = useState(0);
  const historySize = 10;

  const [templates, setTemplates] = useState<any[]>([]);
  const [reminderSchedules, setReminderSchedules] = useState<any[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  const [editTemplateBody, setEditTemplateBody] = useState('');
  const [editTemplateName, setEditTemplateName] = useState('');
  const [editTemplateActive, setEditTemplateActive] = useState(true);
  const [editTemplateDialog, setEditTemplateDialog] = useState(false);

  const [refTab, setRefTab] = useState<'structures' | 'lookup' | 'templates' | 'schedules'>('structures');

  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const selectedStudent = students.find((student) => student.studentId === selectedStudentId);
  const selectedFee = studentFees.find((fee) => fee.studentFeeId === selectedFeeId);
  const openFees = studentFees.filter((fee) => Number(fee.balance || 0) > 0 && fee.status !== 'PAID');
  const today = new Date().toISOString().slice(0, 10);

  const reminderTotal = useMemo(
    () => studentFees
      .filter((fee) => selectedReminderFeeIds.includes(fee.studentFeeId))
      .reduce((sum, fee) => sum + Number(fee.balance || 0), 0),
    [selectedReminderFeeIds, studentFees],
  );

  useEffect(() => {
    void loadDashboard();
  }, []);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void searchStudents(studentSearch);
    }, 250);
    return () => window.clearTimeout(handle);
  }, [studentSearch]);

  useEffect(() => {
    if (selectedStudentId) {
      void loadStudentFees(selectedStudentId);
    }
  }, [selectedStudentId]);

  const loadPaymentHistory = async (page: number) => {
    try {
      const res = await accountantService.getPaymentHistory(page, historySize);
      setPaymentHistory(res.content ?? []);
      setHistoryTotal(res.totalElements ?? 0);
      setHistoryPage(res.page ?? 0);
    } catch (err) {
      setError(readError(err, 'Unable to load payment history.'));
    }
  };

  useEffect(() => {
    if (activeSection === 'history') {
      void loadPaymentHistory(historyPage);
    }
  }, [activeSection, historyPage]);

  const loadTemplatesAndSchedules = async () => {
    try {
      const [tResult, sResult] = await Promise.allSettled([
        accountantService.getNotificationTemplates(),
        accountantService.getReminderSchedules(),
      ]);
      if (tResult.status === 'fulfilled') setTemplates(tResult.value);
      if (sResult.status === 'fulfilled') setReminderSchedules(sResult.value);
    } catch {
      // Silently ignore auxiliary errors
    }
  };

  useEffect(() => {
    if (activeSection === 'reference') {
      void loadTemplatesAndSchedules();
    }
  }, [activeSection]);

  const loadGlobalTargets = async (filter: string) => {
    await runAction(async () => {
      const ids = await accountantService.getOutstandingFeeIds(filter);
      setSelectedReminderFeeIds(ids);
      setNotice(`Loaded ${ids.length} outstanding fee targets for filter: ${filter}.`);
    });
  };

  const handleEditTemplate = (template: any) => {
    setSelectedTemplateId(template.templateId);
    setEditTemplateName(template.name);
    setEditTemplateBody(template.body);
    setEditTemplateActive(template.isActive);
    setEditTemplateDialog(true);
  };

  const saveTemplate = async () => {
    await runAction(async () => {
      await accountantService.updateNotificationTemplate(selectedTemplateId, {
        name: editTemplateName,
        body: editTemplateBody,
        isActive: editTemplateActive,
      });
      setNotice('SMS template updated successfully.');
      setEditTemplateDialog(false);
      await loadTemplatesAndSchedules();
    });
  };

  const loadDashboard = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [dashboardResult, dailyResult, structuresResult, studentsResult, balanceResult] = await Promise.allSettled([
        accountantService.getFeeDashboard(),
        accountantService.getDailySummary(today, today),
        accountantService.getFeeStructures(),
        accountantService.searchStudents(),
        accountantService.getNotificationBalance(),
      ]);

      if (dashboardResult.status === 'fulfilled') setDashboard(dashboardResult.value);
      if (dailyResult.status === 'fulfilled') setDailySummary(dailyResult.value);
      if (structuresResult.status === 'fulfilled') setFeeStructures(structuresResult.value);
      if (studentsResult.status === 'fulfilled') {
        setStudents(studentsResult.value.content ?? []);
        setSelectedStudentId((current) => current || studentsResult.value.content?.[0]?.studentId || '');
      }
      if (balanceResult.status === 'fulfilled') setSmsBalance(balanceResult.value);

      if ([dashboardResult, dailyResult, structuresResult, studentsResult].some((result) => result.status === 'rejected')) {
        setNotice('Some accountant data could not refresh. Available financial data is shown.');
      }
    } catch (err) {
      setError(readError(err, 'Unable to load accountant dashboard.'));
    } finally {
      setIsLoading(false);
    }
  };

  const searchStudents = async (search?: string) => {
    try {
      const result = await accountantService.searchStudents(search?.trim() || undefined);
      setStudents(result.content ?? []);
    } catch {
      // Search is auxiliary; leave existing results visible.
    }
  };

  const loadStudentFees = async (studentId: string) => {
    try {
      const fees = await accountantService.getStudentFees(studentId);
      setStudentFees(fees);
      const firstOpenFee = fees.find((fee) => Number(fee.balance || 0) > 0);
      setSelectedFeeId(firstOpenFee?.studentFeeId || fees[0]?.studentFeeId || '');
      setSelectedReminderFeeIds(fees.filter((fee) => Number(fee.balance || 0) > 0).map((fee) => fee.studentFeeId));
    } catch (err) {
      setError(readError(err, 'Unable to load student fees.'));
    }
  };

  const recordOfflinePayment = async () => {
    if (!selectedFee || !offlineAmount) {
      setError('Select a student fee and enter an amount.');
      return;
    }
    await runAction(async () => {
      const result = await accountantService.recordOfflinePayment({
        studentFeeId: selectedFee.studentFeeId,
        amount: Number(offlineAmount),
        paymentMethod: offlineMethod,
        paymentDate: new Date().toISOString(),
        receivedBy: [user?.firstName, user?.lastName].filter(Boolean).join(' ') || user?.email || 'Accountant',
        notes: offlineNotes,
        generateReceipt: true,
      });
      setOfflineResponse(result);
      if (result.receiptNumber) setReceiptNumber(result.receiptNumber);
      setNotice(`Offline payment recorded${result.receiptNumber ? ` with receipt ${result.receiptNumber}` : ''}.`);
      await loadStudentFees(selectedStudentId);
      await loadDashboard();
    });
  };

  const sendReminders = async () => {
    if (!selectedReminderFeeIds.length) {
      setError('Select at least one outstanding student fee first.');
      return;
    }
    await runAction(async () => {
      const result = await accountantService.sendBulkReminders({
        studentFeeIds: selectedReminderFeeIds,
        templateCode: 'FEE_REMINDER',
        channel: reminderChannel,
      });
      setNotice(`${result.recipientsCount} reminder${result.recipientsCount === 1 ? '' : 's'} queued. Batch ${result.batchId}.`);
    });
  };

  const checkPayment = async () => {
    if (!paymentId.trim()) {
      setError('Enter a payment ID first.');
      return;
    }
    await runAction(async () => {
      const status = await accountantService.getPaymentStatus(paymentId.trim());
      setPaymentStatus(status);
      if (status.receipt?.receiptNumber) setReceiptNumber(status.receipt.receiptNumber);
      setNotice(`Payment status: ${status.status}.`);
    });
  };

  const loadReceipt = async () => {
    if (!receiptNumber.trim()) {
      setError('Enter a receipt number first.');
      return;
    }
    await runAction(async () => {
      const result = await accountantService.getReceipt(receiptNumber.trim());
      setReceipt(result);
    });
  };

  const downloadReceipt = async () => {
    if (!receiptNumber.trim()) {
      setError('Enter a receipt number first.');
      return;
    }
    await runAction(async () => {
      const blob = await accountantService.downloadReceiptPdf(receiptNumber.trim());
      downloadBlob(blob, `receipt-${receiptNumber.trim()}.pdf`);
      setNotice('Receipt download started.');
    });
  };

  const downloadReport = async (format: 'PDF' | 'CSV') => {
    await runAction(async () => {
      const blob = await accountantService.downloadFeeCollectionReport(format);
      downloadBlob(blob, `fee-collection-report.${format.toLowerCase()}`);
      setNotice(`${format} report download started.`);
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

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100">
        <div className="text-center">
          <Loader2 className="mx-auto h-8 w-8 animate-spin text-slate-500" />
          <p className="mt-3 text-sm text-slate-500">Loading accountant workspace...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-100 text-slate-950">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-72 border-r border-slate-200 bg-white lg:flex lg:flex-col">
        <div className="px-6 py-6">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-md bg-slate-950 text-white">
              <Banknote className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-slate-950">{user?.schoolName ?? 'SchoolFee'}</p>
              <p className="text-xs text-slate-500">Accountant Workspace</p>
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
                <Badge variant="outline" className="rounded-full border-slate-300 bg-white text-slate-700">ACCOUNTANT</Badge>
                {dashboard?.termName && <Badge className="rounded-full bg-emerald-100 text-emerald-700 hover:bg-emerald-100">{dashboard.termName}</Badge>}
              </div>
              <h1 className="mt-2 text-2xl font-semibold tracking-tight text-slate-950 md:text-3xl">Financial Operations</h1>
              <p className="mt-1 text-sm text-slate-500">Collections, offline payments, reports, reminders, and receipts.</p>
            </div>
            <div className="grid gap-2 md:grid-cols-[190px_auto]">
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

          {activeSection === 'overview' && (
            <OverviewSection
              dashboard={dashboard}
              dailySummary={dailySummary}
              onGoTo={setActiveSection}
              onDownloadReport={downloadReport}
            />
          )}

          {activeSection === 'offline' && (
            <OfflineSection
              students={students}
              studentSearch={studentSearch}
              selectedStudentId={selectedStudentId}
              selectedStudent={selectedStudent}
              fees={studentFees}
              selectedFeeId={selectedFeeId}
              selectedFee={selectedFee}
              offlineResponse={offlineResponse}
              onSearch={setStudentSearch}
              onSelectStudent={setSelectedStudentId}
              onSelectFee={setSelectedFeeId}
              onOpenRecord={() => {
                setOfflineAmount(String(selectedFee?.balance ?? ''));
                setOfflineDialog(true);
              }}
            />
          )}

          {activeSection === 'reminders' && (
            <RemindersSection
              selectedStudent={selectedStudent}
              fees={openFees}
              selectedFeeIds={selectedReminderFeeIds}
              channel={reminderChannel}
              reminderTotal={reminderTotal}
              isSaving={isSaving}
              onToggleFee={(feeId) => setSelectedReminderFeeIds((ids) => ids.includes(feeId) ? ids.filter((id) => id !== feeId) : [...ids, feeId])}
              onChannel={setReminderChannel}
              onSend={sendReminders}
              balance={smsBalance}
              onLoadGlobalTargets={loadGlobalTargets}
            />
          )}

          {activeSection === 'history' && (
            <HistorySection
              history={paymentHistory}
              page={historyPage}
              total={historyTotal}
              size={historySize}
              onPageChange={setHistoryPage}
              onSelectPayment={(id) => {
                setPaymentId(id);
                void checkPayment();
              }}
              onGoTo={setActiveSection}
            />
          )}

          {activeSection === 'reports' && (
            <ReportsSection dailySummary={dailySummary} isSaving={isSaving} onDownloadReport={downloadReport} />
          )}

          {activeSection === 'receipts' && (
            <ReceiptsSection
              paymentId={paymentId}
              paymentStatus={paymentStatus}
              receiptNumber={receiptNumber}
              receipt={receipt}
              isSaving={isSaving}
              onPaymentId={setPaymentId}
              onReceiptNumber={setReceiptNumber}
              onCheckPayment={checkPayment}
              onLoadReceipt={loadReceipt}
              onDownloadReceipt={downloadReceipt}
            />
          )}

          {activeSection === 'reference' && (
            <ReferenceSection
              feeStructures={feeStructures}
              students={students}
              onSearch={setStudentSearch}
              onSelectStudent={setSelectedStudentId}
              onGoTo={setActiveSection}
              refTab={refTab}
              onTabChange={setRefTab}
              templates={templates}
              schedules={reminderSchedules}
              onEditTemplate={handleEditTemplate}
            />
          )}
        </div>
      </main>

      <Dialog open={offlineDialog} onOpenChange={setOfflineDialog}>
        <DialogContent className="bg-white sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Record Offline Payment</DialogTitle>
            <DialogDescription>{selectedStudent ? `${selectedStudent.firstName} ${selectedStudent.lastName}` : 'Selected student'} · {selectedFee?.structureName ?? 'Selected fee'}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <Field label="Amount paid" type="number" min="1" value={offlineAmount} onChange={setOfflineAmount} />
              <div>
                <Label>Payment Method</Label>
                <Select value={offlineMethod} onValueChange={(value) => setOfflineMethod(value as 'CASH' | 'TRANSFER' | 'POS')}>
                  <SelectTrigger className="mt-2 bg-white">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CASH">Cash</SelectItem>
                    <SelectItem value="TRANSFER">Bank transfer</SelectItem>
                    <SelectItem value="POS">POS</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div>
              <Label htmlFor="offline-notes">Notes</Label>
              <Textarea id="offline-notes" className="mt-2" value={offlineNotes} onChange={(event) => setOfflineNotes(event.target.value)} placeholder="Parent paid at school office..." />
            </div>
            <div className="rounded-md bg-slate-50 p-4">
              <MetricRow label="Outstanding balance" value={formatCurrency(selectedFee?.balance)} />
              <MetricRow label="Receipt" value="Generate automatically" />
            </div>
            <Button className="w-full bg-slate-950 text-white hover:bg-slate-800" disabled={isSaving || !selectedFee || !offlineAmount} onClick={() => void recordOfflinePayment()}>
              {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Banknote className="mr-2 h-4 w-4" />}
              Record Payment
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={editTemplateDialog} onOpenChange={setEditTemplateDialog}>
        <DialogContent className="bg-white sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Edit SMS Template</DialogTitle>
            <DialogDescription>Modify the template name and SMS body. Available variables: [student_name], [amount_due], [due_date], [school_name].</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label htmlFor="template-name">Template Name</Label>
              <Input id="template-name" className="mt-2" value={editTemplateName} onChange={(event) => setEditTemplateName(event.target.value)} />
            </div>
            <div>
              <Label htmlFor="template-body">SMS Body</Label>
              <Textarea id="template-body" className="mt-2 h-32 font-mono text-sm" value={editTemplateBody} onChange={(event) => setEditTemplateBody(event.target.value)} />
            </div>
            <div className="flex items-center gap-2">
              <input type="checkbox" id="template-active" checked={editTemplateActive} onChange={(event) => setEditTemplateActive(event.target.checked)} />
              <Label htmlFor="template-active" className="cursor-pointer">Template Active</Label>
            </div>
            <Button className="w-full bg-slate-950 text-white hover:bg-slate-800" disabled={isSaving || !editTemplateName || !editTemplateBody} onClick={() => void saveTemplate()}>
              {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Send className="mr-2 h-4 w-4" />}
              Save Template
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
};

function OverviewSection({ dashboard, dailySummary, onGoTo, onDownloadReport }: { dashboard: FeeDashboard | null; dailySummary: DailySummary | null; onGoTo: (section: Section) => void; onDownloadReport: (format: 'PDF' | 'CSV') => void }) {
  const summary = dashboard?.summary;
  const deadlines = dashboard?.upcomingDeadlines;
  return (
    <div className="space-y-6">
      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <Metric icon={Banknote} label="Collected" value={formatCurrency(summary?.totalCollected)} detail={`${formatPercent(summary?.collectionRate)} collection rate`} />
        <Metric icon={AlertCircle} label="Outstanding" value={formatCurrency(summary?.totalOutstanding)} detail={`${formatNumber(summary?.unpaidStudents ?? 0)} unpaid students`} />
        <Metric icon={BellRing} label="Due Soon" value={formatCurrency(deadlines?.dueIn3Days?.amount)} detail={`${formatNumber(deadlines?.dueIn3Days?.count ?? 0)} due in 3 days`} />
        <Metric icon={CreditCard} label="Today" value={formatCurrency(dailySummary?.totalCollected)} detail={`${formatNumber(dailySummary?.totalTransactions ?? 0)} transactions`} />
      </section>

      <section className="grid gap-6 xl:grid-cols-[1fr_380px]">
        <div className="rounded-md border border-slate-200 bg-white p-5">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">Collection by Class</p>
              <h2 className="mt-1 text-xl font-semibold text-slate-950">{dashboard?.termName ?? 'Current term'}</h2>
            </div>
            <Button variant="outline" onClick={() => onDownloadReport('PDF')}>
              <Download className="mr-2 h-4 w-4" />
              Report
            </Button>
          </div>
          <div className="mt-5 space-y-4">
            {(dashboard?.byClass ?? []).map((item) => (
              <div key={item.classId}>
                <div className="flex items-center justify-between text-sm">
                  <span className="font-medium text-slate-900">{item.className}</span>
                  <span className="text-slate-500">{formatPercent(item.collectionRate)} · {formatCurrency(item.collectedAmount)}</span>
                </div>
                <div className="mt-2 h-2 rounded-full bg-slate-100">
                  <div className="h-2 rounded-full bg-emerald-600" style={{ width: `${Math.min(100, Math.max(0, item.collectionRate || 0))}%` }} />
                </div>
              </div>
            ))}
            {!dashboard?.byClass?.length && <EmptyBlock message="No class collection data returned yet." />}
          </div>
        </div>

        <div className="space-y-6">
          <div className="rounded-md border border-slate-200 bg-white p-5">
            <p className="text-sm font-medium text-slate-500">Upcoming Deadlines</p>
            <div className="mt-4 space-y-3">
              <MetricRow label="Due in 3 days" value={`${formatNumber(deadlines?.dueIn3Days?.count ?? 0)} · ${formatCurrency(deadlines?.dueIn3Days?.amount)}`} />
              <MetricRow label="Due today" value={`${formatNumber(deadlines?.dueToday?.count ?? 0)} · ${formatCurrency(deadlines?.dueToday?.amount)}`} />
              <MetricRow label="Overdue" value={`${formatNumber(deadlines?.overdue?.count ?? 0)} · ${formatCurrency(deadlines?.overdue?.amount)}`} />
            </div>
          </div>
          <div className="rounded-md border border-slate-200 bg-white p-5">
            <p className="text-sm font-medium text-slate-500">Quick Actions</p>
            <div className="mt-4 grid gap-2">
              <Button className="justify-start bg-slate-950 text-white hover:bg-slate-800" onClick={() => onGoTo('offline')}><Banknote className="mr-2 h-4 w-4" />Record offline payment</Button>
              <Button variant="outline" className="justify-start" onClick={() => onGoTo('reminders')}><Send className="mr-2 h-4 w-4" />Send reminders</Button>
              <Button variant="outline" className="justify-start" onClick={() => onGoTo('reports')}><FileText className="mr-2 h-4 w-4" />Generate reports</Button>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}

function OfflineSection({
  students,
  studentSearch,
  selectedStudentId,
  selectedStudent,
  fees,
  selectedFeeId,
  selectedFee,
  offlineResponse,
  onSearch,
  onSelectStudent,
  onSelectFee,
  onOpenRecord,
}: {
  students: StudentSummary[];
  studentSearch: string;
  selectedStudentId: string;
  selectedStudent?: StudentSummary;
  fees: StudentFee[];
  selectedFeeId: string;
  selectedFee?: StudentFee;
  offlineResponse: OfflinePaymentResponse | null;
  onSearch: (value: string) => void;
  onSelectStudent: (id: string) => void;
  onSelectFee: (id: string) => void;
  onOpenRecord: () => void;
}) {
  return (
    <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
      <section className="rounded-md border border-slate-200 bg-white p-4">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <Input className="pl-9" value={studentSearch} onChange={(event) => onSearch(event.target.value)} placeholder="Search name or admission no." />
        </div>
        <div className="mt-4 max-h-[640px] space-y-2 overflow-y-auto">
          {students.map((student) => (
            <button
              key={student.studentId}
              type="button"
              onClick={() => onSelectStudent(student.studentId)}
              className={`w-full rounded-md border px-3 py-3 text-left text-sm transition ${
                selectedStudentId === student.studentId ? 'border-slate-950 bg-slate-50' : 'border-slate-200 hover:bg-slate-50'
              }`}
            >
              <p className="font-medium text-slate-900">{student.firstName} {student.lastName}</p>
              <p className="mt-1 text-slate-500">{student.admissionNumber} · {student.currentClass?.name ?? 'No class'}</p>
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-md border border-slate-200 bg-white">
        <div className="flex flex-col gap-3 border-b border-slate-200 p-5 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-950">{selectedStudent ? `${selectedStudent.firstName} ${selectedStudent.lastName}` : 'Select a student'}</h2>
            <p className="text-sm text-slate-500">{selectedStudent?.parentName ?? 'Guardian'} · {selectedStudent?.parentPhone ?? 'No phone'}</p>
          </div>
          <Button disabled={!selectedFee || Number(selectedFee.balance || 0) <= 0} onClick={onOpenRecord} className="bg-slate-950 text-white hover:bg-slate-800">
            Record Payment
          </Button>
        </div>
        <FeeTable fees={fees} selectedFeeId={selectedFeeId} onSelectFee={onSelectFee} />
        {offlineResponse && (
          <div className="border-t border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
            Payment {offlineResponse.paymentId} recorded as {offlineResponse.status}
            {offlineResponse.receiptNumber ? ` with receipt ${offlineResponse.receiptNumber}.` : '.'}
          </div>
        )}
      </section>
    </div>
  );
}

function RemindersSection({
  selectedStudent,
  fees,
  selectedFeeIds,
  channel,
  reminderTotal,
  isSaving,
  onToggleFee,
  onChannel,
  onSend,
  balance,
  onLoadGlobalTargets,
}: {
  selectedStudent?: StudentSummary;
  fees: StudentFee[];
  selectedFeeIds: string[];
  channel: 'SMS' | 'WHATSAPP' | 'BOTH';
  reminderTotal: number;
  isSaving: boolean;
  onToggleFee: (feeId: string) => void;
  onChannel: (channel: 'SMS' | 'WHATSAPP' | 'BOTH') => void;
  onSend: () => void;
  balance: any | null;
  onLoadGlobalTargets: (filter: string) => void;
}) {
  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
      <section className="rounded-md border border-slate-200 bg-white">
        <div className="border-b border-slate-200 p-5">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-slate-950">Send Fee Reminders</h2>
              <p className="text-sm text-slate-500">{selectedStudent ? `${selectedStudent.firstName} ${selectedStudent.lastName}` : 'Select a student in Offline Payment first, or use Global Target Selection below'}</p>
            </div>
            {balance && (
              <Badge variant="outline" className="self-start border-emerald-200 bg-emerald-50 text-emerald-800">
                SMS Balance: {formatNumber(balance.balance)} SMS
              </Badge>
            )}
          </div>
          <div className="mt-5 border-t border-slate-100 pt-4">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Global Target Selection</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Button variant="outline" size="sm" onClick={() => onLoadGlobalTargets('OVERDUE')}>
                Select All Overdue
              </Button>
              <Button variant="outline" size="sm" onClick={() => onLoadGlobalTargets('DUE_TODAY')}>
                Select All Due Today
              </Button>
              <Button variant="outline" size="sm" onClick={() => onLoadGlobalTargets('DUE_IN_3_DAYS')}>
                Select All Due in 3 Days
              </Button>
            </div>
          </div>
        </div>
        <div className="divide-y divide-slate-100">
          {fees.map((fee) => (
            <label key={fee.studentFeeId} className="flex cursor-pointer items-start gap-3 p-5">
              <input className="mt-1 h-4 w-4" type="checkbox" checked={selectedFeeIds.includes(fee.studentFeeId)} onChange={() => onToggleFee(fee.studentFeeId)} />
              <span className="flex-1">
                <span className="block font-semibold text-slate-950">{fee.structureName}</span>
                <span className="mt-1 block text-sm text-slate-500">{fee.termName} · balance {formatCurrency(fee.balance)}</span>
              </span>
              <StatusBadge status={fee.status} />
            </label>
          ))}
          {!fees.length && <EmptyBlock message="No outstanding fees loaded. Use Global Target Selection above to batch-load targets." />}
        </div>
      </section>
      <section className="rounded-md border border-slate-200 bg-white p-5">
        <p className="text-sm font-medium text-slate-500">Reminder Batch</p>
        <div className="mt-4">
          <Label>Channel</Label>
          <Select value={channel} onValueChange={(value) => onChannel(value as 'SMS' | 'WHATSAPP' | 'BOTH')}>
            <SelectTrigger className="mt-2 bg-white">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="SMS">SMS</SelectItem>
              <SelectItem value="WHATSAPP">WhatsApp</SelectItem>
              <SelectItem value="BOTH">Both</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="mt-5 space-y-3">
          <MetricRow label="Selected fees" value={formatNumber(selectedFeeIds.length)} />
          <MetricRow label="Outstanding" value={formatCurrency(reminderTotal)} />
          <MetricRow label="Template" value="FEE_REMINDER" />
        </div>
        {balance && (
          <div className="mt-4 space-y-2 text-xs text-slate-500">
            <p>Cost per SMS: {formatCurrency(balance.costPerSms)} · Provider: {balance.provider}</p>
            <p>Estimated Remaining: {balance.estimatedRemainingDays} days</p>
          </div>
        )}
        <Button className="mt-5 w-full bg-slate-950 text-white hover:bg-slate-800" disabled={isSaving || !selectedFeeIds.length} onClick={onSend}>
          {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Send className="mr-2 h-4 w-4" />}
          Send Reminders
        </Button>
      </section>
    </div>
  );
}

function ReportsSection({ dailySummary, isSaving, onDownloadReport }: { dailySummary: DailySummary | null; isSaving: boolean; onDownloadReport: (format: 'PDF' | 'CSV') => void }) {
  const methods = Object.entries(dailySummary?.byPaymentMethod ?? {});
  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
      <section className="rounded-md border border-slate-200 bg-white">
        <div className="border-b border-slate-200 p-5">
          <h2 className="text-lg font-semibold text-slate-950">Daily Collection Summary</h2>
          <p className="text-sm text-slate-500">{dailySummary?.period ? `${dailySummary.period.startDate} to ${dailySummary.period.endDate}` : 'Today'}</p>
        </div>
        <div className="grid gap-4 p-5 md:grid-cols-2">
          <MetricRow label="Total collected" value={formatCurrency(dailySummary?.totalCollected)} />
          <MetricRow label="Transactions" value={formatNumber(dailySummary?.totalTransactions ?? 0)} />
        </div>
        <div className="px-5 pb-5">
          <p className="text-sm font-medium text-slate-500">By Method</p>
          <div className="mt-3 space-y-3">
            {methods.map(([method, value]) => (
              <MetricRow key={method} label={method} value={`${formatCurrency(value.amount)} · ${formatNumber(value.count)}`} />
            ))}
            {!methods.length && <EmptyBlock message="No daily payment methods returned yet." />}
          </div>
        </div>
      </section>
      <section className="rounded-md border border-slate-200 bg-white p-5">
        <p className="text-sm font-medium text-slate-500">Fee Collection Report</p>
        <p className="mt-2 text-sm text-slate-600">Download management-ready collection reports for the current term.</p>
        <div className="mt-5 grid gap-2">
          <Button disabled={isSaving} className="bg-slate-950 text-white hover:bg-slate-800" onClick={() => onDownloadReport('PDF')}>
            <Download className="mr-2 h-4 w-4" />
            Download PDF
          </Button>
          <Button disabled={isSaving} variant="outline" onClick={() => onDownloadReport('CSV')}>
            <Download className="mr-2 h-4 w-4" />
            Export CSV
          </Button>
        </div>
      </section>
    </div>
  );
}

function ReceiptsSection({
  paymentId,
  paymentStatus,
  receiptNumber,
  receipt,
  isSaving,
  onPaymentId,
  onReceiptNumber,
  onCheckPayment,
  onLoadReceipt,
  onDownloadReceipt,
}: {
  paymentId: string;
  paymentStatus: PaymentStatus | null;
  receiptNumber: string;
  receipt: ReceiptDetail | null;
  isSaving: boolean;
  onPaymentId: (value: string) => void;
  onReceiptNumber: (value: string) => void;
  onCheckPayment: () => void;
  onLoadReceipt: () => void;
  onDownloadReceipt: () => void;
}) {
  return (
    <div className="grid gap-6 xl:grid-cols-[380px_1fr]">
      <section className="space-y-6">
        <div className="rounded-md border border-slate-200 bg-white p-5">
          <Label htmlFor="payment-id">Payment ID</Label>
          <Input id="payment-id" className="mt-2" value={paymentId} onChange={(event) => onPaymentId(event.target.value)} placeholder="UUID" />
          <Button className="mt-3 w-full" disabled={isSaving || !paymentId.trim()} onClick={onCheckPayment}>Check Payment</Button>
          {paymentStatus && (
            <div className="mt-4 space-y-2 text-sm">
              <MetricRow label="Status" value={paymentStatus.status} />
              <MetricRow label="Amount" value={formatCurrency(paymentStatus.amount)} />
              <MetricRow label="Method" value={paymentStatus.paymentMethod} />
            </div>
          )}
        </div>
        <div className="rounded-md border border-slate-200 bg-white p-5">
          <Label htmlFor="receipt-number">Receipt number</Label>
          <Input id="receipt-number" className="mt-2" value={receiptNumber} onChange={(event) => onReceiptNumber(event.target.value)} placeholder="RCP-2026-..." />
          <div className="mt-3 grid gap-2">
            <Button disabled={isSaving || !receiptNumber.trim()} onClick={onLoadReceipt}>Open Receipt</Button>
            <Button disabled={isSaving || !receiptNumber.trim()} variant="outline" onClick={onDownloadReceipt}>Download PDF</Button>
          </div>
        </div>
      </section>
      <section className="rounded-md border border-slate-200 bg-white">
        {receipt ? (
          <div>
            <div className="border-b border-slate-200 p-5">
              <h2 className="text-lg font-semibold text-slate-950">Receipt {receipt.receiptNumber}</h2>
              <p className="text-sm text-slate-500">{receipt.schoolName} · {formatDateTime(receipt.paymentDate)}</p>
            </div>
            <div className="space-y-4 p-5">
              <MetricRow label="Paid by" value={receipt.paidBy} />
              <MetricRow label="Amount" value={formatCurrency(receipt.amount)} />
              <MetricRow label="Method" value={receipt.paymentMethod} />
              <div className="rounded-md border border-slate-200">
                {receipt.breakdown.map((item) => (
                  <div key={`${item.admissionNumber}-${item.amount}`} className="flex items-center justify-between border-b border-slate-100 px-4 py-3 last:border-b-0">
                    <div>
                      <p className="text-sm font-medium text-slate-900">{item.studentName}</p>
                      <p className="text-xs text-slate-500">{item.className ?? 'Class'} · {item.term ?? 'Term'}</p>
                    </div>
                    <p className="text-sm font-semibold text-slate-950">{formatCurrency(item.amount)}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <EmptyBlock message="Open a payment or receipt to view details." />
        )}
      </section>
    </div>
  );
}

function ReferenceSection({
  feeStructures,
  students,
  onSearch,
  onSelectStudent,
  onGoTo,
  refTab,
  onTabChange,
  templates,
  schedules,
  onEditTemplate,
}: {
  feeStructures: FeeStructure[];
  students: StudentSummary[];
  onSearch: (value: string) => void;
  onSelectStudent: (id: string) => void;
  onGoTo: (section: Section) => void;
  refTab: 'structures' | 'lookup' | 'templates' | 'schedules';
  onTabChange: (tab: 'structures' | 'lookup' | 'templates' | 'schedules') => void;
  templates: any[];
  schedules: any[];
  onEditTemplate: (template: any) => void;
}) {
  return (
    <div className="space-y-6">
      <div className="flex border-b border-slate-200">
        {(['structures', 'lookup', 'templates', 'schedules'] as const).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => onTabChange(tab)}
            className={`border-b-2 px-5 py-3 text-sm font-medium transition ${
              refTab === tab
                ? 'border-slate-950 text-slate-950'
                : 'border-transparent text-slate-500 hover:text-slate-950'
            }`}
          >
            {tab === 'structures' && 'Fee Structures'}
            {tab === 'lookup' && 'Student Lookup'}
            {tab === 'templates' && 'SMS Templates'}
            {tab === 'schedules' && 'Reminder Schedules'}
          </button>
        ))}
      </div>

      {refTab === 'structures' && (
        <div className="rounded-md border border-slate-200 bg-white">
          <div className="border-b border-slate-200 p-5">
            <h2 className="text-lg font-semibold text-slate-950">Fee Structures</h2>
            <p className="text-sm text-slate-500">Active fee structures for the current session/term.</p>
          </div>
          <div className="divide-y divide-slate-100">
            {feeStructures.map((structure) => (
              <div key={structure.structureId} className="p-5 flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold text-slate-950">{structure.name}</p>
                  <p className="mt-1 text-sm text-slate-500">{structure.termName} · {formatNumber(structure.studentCount ?? 0)} students</p>
                </div>
                <p className="font-semibold text-slate-950">{formatCurrency(structure.totalAmount)}</p>
              </div>
            ))}
            {!feeStructures.length && <EmptyBlock message="No active fee structures returned." />}
          </div>
        </div>
      )}

      {refTab === 'lookup' && (
        <div className="rounded-md border border-slate-200 bg-white">
          <div className="border-b border-slate-200 p-5">
            <h2 className="text-lg font-semibold text-slate-950">Student Lookup</h2>
            <p className="text-sm text-slate-500">Quickly find a student's profile and record offline payments.</p>
            <Input className="mt-3" onChange={(event) => onSearch(event.target.value)} placeholder="Search name or admission number..." />
          </div>
          <div className="divide-y divide-slate-100 max-h-[500px] overflow-y-auto">
            {students.map((student) => (
              <button
                key={student.studentId}
                type="button"
                className="flex w-full items-center justify-between p-5 text-left hover:bg-slate-50"
                onClick={() => {
                  onSelectStudent(student.studentId);
                  onGoTo('offline');
                }}
              >
                <span>
                  <span className="block font-semibold text-slate-950">{student.firstName} {student.lastName}</span>
                  <span className="mt-1 block text-sm text-slate-500">{student.admissionNumber} · {student.currentClass?.name ?? 'No class'}</span>
                </span>
                <Users className="h-4 w-4 text-slate-400" />
              </button>
            ))}
            {!students.length && <EmptyBlock message="No students found." />}
          </div>
        </div>
      )}

      {refTab === 'templates' && (
        <div className="rounded-md border border-slate-200 bg-white">
          <div className="border-b border-slate-200 p-5">
            <h2 className="text-lg font-semibold text-slate-950">SMS Templates</h2>
            <p className="text-sm text-slate-500">Manage SMS messaging formats for fee reminders.</p>
          </div>
          <div className="divide-y divide-slate-100">
            {templates.map((template) => (
              <div key={template.templateId} className="p-5 flex items-start justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <p className="font-semibold text-slate-950">{template.name}</p>
                    <Badge variant="outline" className="font-mono text-xs">{template.code}</Badge>
                    {template.isDefault && <Badge className="bg-emerald-100 text-emerald-800">Default</Badge>}
                  </div>
                  <p className="text-sm text-slate-600 bg-slate-50 p-3 rounded-md font-mono mt-2">{template.body}</p>
                  <p className="text-xs text-slate-400">Variables: {template.variables?.join(', ') || 'None'}</p>
                </div>
                <Button variant="outline" size="sm" onClick={() => onEditTemplate(template)}>
                  Edit
                </Button>
              </div>
            ))}
            {!templates.length && <EmptyBlock message="No templates found." />}
          </div>
        </div>
      )}

      {refTab === 'schedules' && (
        <div className="rounded-md border border-slate-200 bg-white">
          <div className="border-b border-slate-200 p-5">
            <h2 className="text-lg font-semibold text-slate-950">Reminder Schedules</h2>
            <p className="text-sm text-slate-500">Automated reminder rules defined by the school.</p>
          </div>
          <div className="divide-y divide-slate-100">
            {schedules.map((schedule) => (
              <div key={schedule.scheduleId} className="p-5 flex items-center justify-between">
                <div>
                  <p className="font-semibold text-slate-950">{schedule.name}</p>
                  <p className="text-sm text-slate-500">
                    Triggers {schedule.daysOffset} days {schedule.triggerType?.toLowerCase().replace('_', ' ')} at {schedule.sendTime}
                  </p>
                  <p className="text-xs text-slate-400 font-mono mt-1">Template: {schedule.templateCode}</p>
                </div>
                <StatusBadge status={schedule.isActive ? 'ACTIVE' : 'INACTIVE'} />
              </div>
            ))}
            {!schedules.length && <EmptyBlock message="No reminder schedules found." />}
          </div>
        </div>
      )}
    </div>
  );
}

function HistorySection({
  history,
  page,
  total,
  size,
  onPageChange,
  onSelectPayment,
  onGoTo,
}: {
  history: PaymentHistoryItem[];
  page: number;
  total: number;
  size: number;
  onPageChange: (page: number) => void;
  onSelectPayment: (paymentId: string) => void;
  onGoTo: (section: Section) => void;
}) {
  const totalPages = Math.ceil(total / size);
  return (
    <div className="rounded-md border border-slate-200 bg-white">
      <div className="border-b border-slate-200 p-5">
        <h2 className="text-lg font-semibold text-slate-950">Payment History</h2>
        <p className="text-sm text-slate-500">All student fee collections and offline entries in the school.</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-5 py-3">Receipt / ID</th>
              <th className="px-5 py-3">Description</th>
              <th className="px-5 py-3">Amount</th>
              <th className="px-5 py-3">Method</th>
              <th className="px-5 py-3">Status</th>
              <th className="px-5 py-3">Date</th>
              <th className="px-5 py-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody>
            {history.map((item) => (
              <tr key={item.paymentId} className="border-b border-slate-100">
                <td className="px-5 py-4">
                  <p className="font-medium text-slate-900">{item.receiptNumber || 'No receipt'}</p>
                  <p className="text-xs text-slate-400 font-mono">{item.paymentId}</p>
                </td>
                <td className="px-5 py-4 text-slate-600">{item.description}</td>
                <td className="px-5 py-4 font-semibold text-slate-950">{formatCurrency(item.amount)}</td>
                <td className="px-5 py-4 text-slate-600">{item.paymentMethod}</td>
                <td className="px-5 py-4">
                  <StatusBadge status={item.status} />
                </td>
                <td className="px-5 py-4 text-slate-500">{formatDateTime(item.date)}</td>
                <td className="px-5 py-4 text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      onSelectPayment(item.paymentId);
                      onGoTo('receipts');
                    }}
                  >
                    View
                  </Button>
                </td>
              </tr>
            ))}
            {!history.length && (
              <tr>
                <td colSpan={7} className="px-5 py-10 text-center text-sm text-slate-500">No payment history found.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-slate-200 px-5 py-4">
          <span className="text-sm text-slate-500">
            Showing page {page + 1} of {totalPages} ({total} payments)
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page <= 0}
              onClick={() => onPageChange(page - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => onPageChange(page + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

function FeeTable({ fees, selectedFeeId, onSelectFee }: { fees: StudentFee[]; selectedFeeId: string; onSelectFee: (id: string) => void }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-5 py-3">Fee</th>
            <th className="px-5 py-3">Total</th>
            <th className="px-5 py-3">Paid</th>
            <th className="px-5 py-3">Balance</th>
            <th className="px-5 py-3">Status</th>
          </tr>
        </thead>
        <tbody>
          {fees.map((fee) => (
            <tr
              key={fee.studentFeeId}
              className={`cursor-pointer border-b border-slate-100 ${selectedFeeId === fee.studentFeeId ? 'bg-slate-50' : ''}`}
              onClick={() => onSelectFee(fee.studentFeeId)}
            >
              <td className="px-5 py-4">
                <p className="font-medium text-slate-900">{fee.structureName}</p>
                <p className="text-xs text-slate-500">{fee.termName} · {fee.dueDate ? formatDate(fee.dueDate) : 'No due date'}</p>
              </td>
              <td className="px-5 py-4 text-slate-600">{formatCurrency(fee.totalAmount)}</td>
              <td className="px-5 py-4 text-emerald-700">{formatCurrency(fee.amountPaid)}</td>
              <td className="px-5 py-4 font-semibold text-slate-950">{formatCurrency(fee.balance)}</td>
              <td className="px-5 py-4"><StatusBadge status={fee.status} /></td>
            </tr>
          ))}
          {!fees.length && (
            <tr>
              <td colSpan={5} className="px-5 py-10 text-center text-sm text-slate-500">No fees returned for this student.</td>
            </tr>
          )}
        </tbody>
      </table>
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

function MetricRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2">
      <span className="text-sm text-slate-600">{label}</span>
      <span className="text-sm font-semibold text-slate-900">{value}</span>
    </div>
  );
}

function StatusBadge({ status }: { status?: string }) {
  const normalized = status ?? 'UNKNOWN';
  const className = normalized === 'PAID' || normalized === 'COMPLETED'
    ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-100'
    : normalized === 'OVERDUE' || normalized === 'FAILED'
      ? 'bg-red-100 text-red-700 hover:bg-red-100'
      : 'bg-amber-100 text-amber-700 hover:bg-amber-100';
  return <Badge className={className}>{normalized}</Badge>;
}

function Field({ label, value, onChange, ...props }: { label: string; value: string; onChange: (value: string) => void } & Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'>) {
  const id = label.replace(/\s+/g, '-').toLowerCase();
  return (
    <div>
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} value={value} onChange={(event) => onChange(event.target.value)} className="mt-2" {...props} />
    </div>
  );
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

function formatPercent(value?: number) {
  return `${Number(value || 0).toFixed(1)}%`;
}

function formatDate(value?: string) {
  if (!value) return 'Not set';
  return new Intl.DateTimeFormat('en-NG', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value));
}

function formatDateTime(value?: string) {
  if (!value) return 'Not set';
  return new Intl.DateTimeFormat('en-NG', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function readError(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { errors?: Array<{ message?: string }>; message?: string } } }).response;
    return response?.data?.errors?.[0]?.message || response?.data?.message || fallback;
  }
  return fallback;
}

export default AccountantDashboard;
