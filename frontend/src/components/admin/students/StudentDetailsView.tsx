import React, { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import {
  User,
  Phone,
  Mail,
  ChevronLeft,
  ShieldAlert,
  Loader2,
  Send,
  CheckCircle2,
  Calendar,
  BookOpen,
  CreditCard,
  AlertCircle,
} from 'lucide-react';
import { schoolAdminService, type StudentDetail } from '@/services/schoolAdminService';

interface StudentDetailsProps {
  studentId: string;
  onBack: () => void;
}

export const StudentDetailsView: React.FC<StudentDetailsProps> = ({ studentId, onBack }) => {
  const [student, setStudent] = useState<StudentDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [invitingGuardianId, setInvitingGuardianId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const fetchStudent = useCallback(async () => {
    if (!studentId) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await schoolAdminService.getStudentDetails(studentId);
      setStudent(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch student details');
    } finally {
      setIsLoading(false);
    }
  }, [studentId]);

  useEffect(() => {
    void fetchStudent();
  }, [fetchStudent]);

  // Dismiss notices after 4s
  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), 4000);
    return () => clearTimeout(t);
  }, [notice]);

  const handleInviteGuardian = async (guardianId: string, guardianName: string) => {
    setInvitingGuardianId(guardianId);
    try {
      await schoolAdminService.inviteGuardian(guardianId);
      setNotice(`Invitation sent to ${guardianName} successfully!`);
      // Reload student details to reflect updated invitation status
      await fetchStudent();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to send invitation';
      setNotice(`Error: ${msg}`);
    } finally {
      setInvitingGuardianId(null);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-16 gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
        <p className="text-sm text-slate-500">Loading student details…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" onClick={onBack} className="pl-0">
          <ChevronLeft className="w-4 h-4 mr-2" />
          Back to Students
        </Button>
        <div className="flex flex-col items-center justify-center p-12 gap-3 text-red-600">
          <AlertCircle className="h-8 w-8" />
          <p className="text-sm">{error}</p>
          <Button variant="outline" size="sm" onClick={() => void fetchStudent()}>
            Retry
          </Button>
        </div>
      </div>
    );
  }

  if (!student) return null;

  const fullName = [student.firstName, student.middleName, student.lastName].filter(Boolean).join(' ');
  const parents = student.parents ?? [];
  const currentFee = student.currentTermFeeSummary;
  const upcomingFee = student.upcomingTermFeeSummary;

  const feeStatusColor: Record<string, string> = {
    PAID: 'bg-emerald-100 text-emerald-700',
    PARTIAL: 'bg-amber-100 text-amber-700',
    PENDING: 'bg-slate-100 text-slate-600',
    OVERDUE: 'bg-red-100 text-red-700',
  };

  return (
    <div className="space-y-6">
      {/* Notice toast */}
      {notice && (
        <div
          className={`flex items-center gap-2 rounded-lg px-4 py-3 text-sm font-medium shadow-sm transition-all ${
            notice.startsWith('Error')
              ? 'bg-red-50 text-red-700 border border-red-200'
              : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
          }`}
        >
          {notice.startsWith('Error') ? (
            <AlertCircle className="h-4 w-4 shrink-0" />
          ) : (
            <CheckCircle2 className="h-4 w-4 shrink-0" />
          )}
          {notice}
        </div>
      )}

      {/* Header */}
      <div className="flex items-center justify-between">
        <Button variant="ghost" onClick={onBack} className="pl-0">
          <ChevronLeft className="w-4 h-4 mr-2" />
          Back to Students
        </Button>
        <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100 text-xs px-3 py-1">
          {student.status ?? 'ACTIVE'}
        </Badge>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* ── Main Student Info ── */}
        <Card className="lg:col-span-2">
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle className="text-2xl">{fullName}</CardTitle>
              <CardDescription>Admission: {student.admissionNumber}</CardDescription>
            </div>
            <Badge variant="outline" className="text-sm px-3 py-1 bg-blue-50">
              {student.currentClass?.name ?? 'Unassigned'}
            </Badge>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              <InfoCell icon={User} label="Gender" value={student.gender ?? 'N/A'} />
              <InfoCell icon={Calendar} label="Date of Birth" value={student.dateOfBirth ?? 'Not provided'} />
              <InfoCell icon={BookOpen} label="Grade Level" value={student.currentClass?.gradeLevel ?? 'N/A'} />
              <InfoCell icon={Calendar} label="Enrolled" value={student.enrollmentDate ?? 'N/A'} />
            </div>

            {student.medicalNotes && (
              <div>
                <Separator className="my-4" />
                <h4 className="font-medium text-red-600 flex items-center gap-2 mb-2">
                  <ShieldAlert className="w-4 h-4" /> Medical Notes
                </h4>
                <p className="text-sm bg-red-50 p-3 rounded border border-red-100">
                  {student.medicalNotes}
                </p>
              </div>
            )}

            {/* ── Fee Summaries ── */}
            <div className="space-y-4">
              <Separator className="my-4" />
              <h4 className="font-medium text-slate-900 flex items-center gap-2 mb-3">
                <CreditCard className="w-4 h-4 text-blue-600" /> Fee Summaries
              </h4>
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Current Term Card */}
                <div className="rounded-xl border border-slate-200 bg-slate-50/50 p-4 relative overflow-hidden transition-all hover:shadow-sm">
                  <div className="flex items-center justify-between mb-3">
                    <span className="inline-flex items-center rounded-md bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-700 ring-1 ring-inset ring-blue-700/10">
                      📅 CURRENT TERM
                    </span>
                    <span className="text-xs font-medium text-slate-500">
                      {currentFee?.termName || 'First Term'}
                    </span>
                  </div>
                  
                  {currentFee ? (
                    <div className="space-y-3">
                      <div className="grid grid-cols-2 gap-2">
                        <div className="bg-white rounded-lg p-2.5 border border-slate-100">
                          <p className="text-[11px] text-slate-400 font-medium uppercase tracking-wider">Total Fee</p>
                          <p className="text-sm font-bold text-slate-900 mt-0.5">₦{formatAmount(currentFee.totalFee)}</p>
                        </div>
                        <div className="bg-emerald-50/50 rounded-lg p-2.5 border border-emerald-100/50">
                          <p className="text-[11px] text-emerald-600/80 font-medium uppercase tracking-wider">Paid</p>
                          <p className="text-sm font-bold text-emerald-700 mt-0.5">₦{formatAmount(currentFee.amountPaid)}</p>
                        </div>
                        <div className="bg-amber-50/50 rounded-lg p-2.5 border border-amber-100/50">
                          <p className="text-[11px] text-amber-600/80 font-medium uppercase tracking-wider">Balance</p>
                          <p className="text-sm font-bold text-amber-700 mt-0.5">₦{formatAmount(currentFee.balance)}</p>
                        </div>
                        <div className="bg-white rounded-lg p-2.5 border border-slate-100 flex flex-col justify-between">
                          <p className="text-[11px] text-slate-400 font-medium uppercase tracking-wider">Status</p>
                          <Badge className={`text-[10px] font-semibold w-fit px-2 py-0.5 mt-0.5 ${feeStatusColor[currentFee.status ?? ''] ?? 'bg-slate-100 text-slate-600'}`}>
                            {currentFee.status ?? 'PENDING'}
                          </Badge>
                        </div>
                      </div>
                      {currentFee.dueDate && (
                        <p className="text-[11px] text-slate-500 flex items-center gap-1">
                          <span className="font-medium">Due Date:</span> {currentFee.dueDate}
                        </p>
                      )}
                    </div>
                  ) : (
                    <div className="text-center py-6 text-slate-400 bg-white/50 border border-dashed rounded-lg">
                      <p className="text-xs">No current term fees assigned</p>
                    </div>
                  )}
                </div>

                {/* Upcoming Term Card */}
                <div className="rounded-xl border border-slate-200 bg-slate-50/50 p-4 relative overflow-hidden transition-all hover:shadow-sm">
                  <div className="flex items-center justify-between mb-3">
                    <span className="inline-flex items-center rounded-md bg-purple-50 px-2 py-1 text-xs font-semibold text-purple-700 ring-1 ring-inset ring-purple-700/10">
                      🚀 UPCOMING TERM
                    </span>
                    <span className="text-xs font-medium text-slate-500">
                      {upcomingFee?.termName || 'Next Term'}
                    </span>
                  </div>
                  
                  {upcomingFee ? (
                    <div className="space-y-3">
                      <div className="grid grid-cols-2 gap-2">
                        <div className="bg-white rounded-lg p-2.5 border border-slate-100">
                          <p className="text-[11px] text-slate-400 font-medium uppercase tracking-wider">Total Fee</p>
                          <p className="text-sm font-bold text-slate-900 mt-0.5">₦{formatAmount(upcomingFee.totalFee)}</p>
                        </div>
                        <div className="bg-emerald-50/50 rounded-lg p-2.5 border border-emerald-100/50">
                          <p className="text-[11px] text-emerald-600/80 font-medium uppercase tracking-wider">Paid</p>
                          <p className="text-sm font-bold text-emerald-700 mt-0.5">₦{formatAmount(upcomingFee.amountPaid)}</p>
                        </div>
                        <div className="bg-amber-50/50 rounded-lg p-2.5 border border-amber-100/50">
                          <p className="text-[11px] text-amber-600/80 font-medium uppercase tracking-wider">Balance</p>
                          <p className="text-sm font-bold text-amber-700 mt-0.5">₦{formatAmount(upcomingFee.balance)}</p>
                        </div>
                        <div className="bg-white rounded-lg p-2.5 border border-slate-100 flex flex-col justify-between">
                          <p className="text-[11px] text-slate-400 font-medium uppercase tracking-wider">Status</p>
                          <Badge className={`text-[10px] font-semibold w-fit px-2 py-0.5 mt-0.5 ${feeStatusColor[upcomingFee.status ?? ''] ?? 'bg-slate-100 text-slate-600'}`}>
                            {upcomingFee.status ?? 'PENDING'}
                          </Badge>
                        </div>
                      </div>
                      {upcomingFee.dueDate && (
                        <p className="text-[11px] text-slate-500 flex items-center gap-1">
                          <span className="font-medium">Due Date:</span> {upcomingFee.dueDate}
                        </p>
                      )}
                    </div>
                  ) : (
                    <div className="text-center py-6 text-slate-400 bg-white/50 border border-dashed rounded-lg">
                      <p className="text-xs">Not yet available</p>
                      <p className="text-[10px] text-slate-400 mt-0.5">No upcoming term fees assigned in advance</p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* ── Guardians Panel ── */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-lg">Guardians</CardTitle>
            <Badge variant="secondary" className="text-xs">{parents.length}</Badge>
          </CardHeader>
          <CardContent>
            {parents.length === 0 ? (
              <div className="text-center p-6 text-gray-500 border border-dashed rounded-lg bg-gray-50">
                <User className="w-8 h-8 mx-auto text-gray-400 mb-2" />
                <p className="text-sm">No guardians linked yet</p>
                <p className="text-xs mt-1">Guardians are added during enrollment</p>
              </div>
            ) : (
              <div className="space-y-4">
                {parents.map((guardian, idx) => (
                  <div key={guardian.guardianId ?? idx} className="p-3 border rounded-lg bg-white shadow-sm relative">
                    <div className="flex justify-between items-start mb-2">
                      <h4 className="font-semibold text-sm">{guardian.name}</h4>
                      <Badge variant={guardian.isPrimaryContact ? 'default' : 'secondary'} className="text-xs">
                        {guardian.relationship ?? 'Guardian'}
                      </Badge>
                    </div>

                    <div className="space-y-1 text-sm text-gray-600">
                      {guardian.phoneNumber && (
                        <div className="flex items-center gap-2">
                          <Phone className="w-3 h-3" />
                          <span>{guardian.phoneNumber}</span>
                        </div>
                      )}
                    </div>

                    {/* Invitation / Account Status */}
                    {guardian.userId ? (
                      <div className="mt-3 text-xs text-emerald-600 bg-emerald-50 p-2 rounded flex items-center gap-2">
                        <CheckCircle2 className="w-3 h-3" />
                        <span>Account active — Portal access enabled</span>
                      </div>
                    ) : guardian.guardianId ? (
                      <div className="mt-3">
                        <div className="text-xs text-amber-600 bg-amber-50 p-2 rounded flex items-start gap-2 mb-2">
                          <Mail className="w-3 h-3 mt-0.5" />
                          <span>No account yet — Send invitation to create portal access</span>
                        </div>
                        <Button
                          size="sm"
                          variant="outline"
                          className="w-full text-xs"
                          disabled={invitingGuardianId === guardian.guardianId}
                          onClick={() => void handleInviteGuardian(guardian.guardianId!, guardian.name)}
                        >
                          {invitingGuardianId === guardian.guardianId ? (
                            <>
                              <Loader2 className="w-3 h-3 mr-1 animate-spin" />
                              Sending…
                            </>
                          ) : (
                            <>
                              <Send className="w-3 h-3 mr-1" />
                              Send Invitation SMS
                            </>
                          )}
                        </Button>
                      </div>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

/* ── Helpers ── */

function InfoCell({ icon: Icon, label, value }: { icon: React.ComponentType<{ className?: string }>; label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-slate-500 font-medium flex items-center gap-1 mb-1">
        <Icon className="w-3 h-3" />
        {label}
      </p>
      <p className="text-sm text-slate-900">{value}</p>
    </div>
  );
}

function formatAmount(value?: number): string {
  if (value == null) return '0';
  return value.toLocaleString('en-NG', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
}
