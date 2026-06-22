import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Banknote,
  BellRing,
  BookMarked,
  BookOpen,
  CalendarCheck,
  CheckCircle2,
  ChevronRight,
  CircleDollarSign,
  GraduationCap,
  LayoutDashboard,
  Loader2,
  LogOut,
  Plus,
  RefreshCw,
  Search,
  Settings,
  ShieldCheck,
  UserPlus,
  Users,
  XCircle,
  FileText,
} from 'lucide-react';
import { useAuth } from '@/components/auth/AuthProvider';
import { useLocation, useNavigate } from 'react-router-dom';
import { useSchoolStore } from '@/store/schoolStore';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Progress } from '@/components/ui/progress';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import {
  type AcademicSession,
  type AttendanceResponse,
  type AttendanceSummaryResponse,
  type ClassRoom,
  type CreateFeeStructurePayload,
  type DailySummary,
  type ExamLookupResponse,
  type FeeDashboard,
  type FeeStructure,
  type GradeLevel,
  type GradingRulesResponse,
  type NotificationBalance,
  type NotificationTemplate,
  type SchoolProfile,
  type StudentSummary,
  type TodayAttendanceResponse,
  type UserSummary,
  schoolAdminService,
  type SubjectResponse,
  type ClassSubjectResponse,
} from '@/services/schoolAdminService';
import { StudentDetailsView } from '@/components/admin/students/StudentDetailsView';
import { AttendanceMonitoringSection } from '@/components/admin/AttendanceMonitoringSection';

type Section = 'overview' | 'setup' | 'classes' | 'people' | 'students' | 'attendance' | 'fees' | 'notifications' | 'subjects' | 'results';
type StaffRole = 'TEACHER' | 'ACCOUNTANT' | 'SCHOOL_ADMIN';

interface SubjectForm {
  name: string;
  code: string;
  category: string;
}

interface ClassForm {
  name: string;
  gradeLevel: string;
  section: string;
  academicSessionId: string;
  capacity: string;
}

interface StaffForm {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  userType: StaffRole;
}

interface StudentForm {
  firstName: string;
  lastName: string;
  middleName: string;
  gender: 'MALE' | 'FEMALE';
  dateOfBirth: string;
  classId: string;
  guardianFirstName: string;
  guardianLastName: string;
  guardianPhone: string;
  guardianEmail: string;
  guardianRelationship: string;
  medicalNotes: string;
}

interface FeeItemForm {
  description: string;
  amount: string;
  isMandatory: boolean;
}

interface FeeForm {
  name: string;
  sessionId: string;
  termId: string;
  classIds: string[];
  dueDate: string;
  applyAfterDays: string;
  percentageAmount: string;
  items: FeeItemForm[];
}

const emptyClassForm: ClassForm = {
  name: '',
  gradeLevel: '',
  section: 'A',
  academicSessionId: '',
  capacity: '40',
};

const emptyStaffForm: StaffForm = {
  firstName: '',
  lastName: '',
  email: '',
  phoneNumber: '',
  userType: 'TEACHER',
};

const emptyStudentForm: StudentForm = {
  firstName: '',
  lastName: '',
  middleName: '',
  gender: 'MALE',
  dateOfBirth: '',
  classId: '',
  guardianFirstName: '',
  guardianLastName: '',
  guardianPhone: '',
  guardianEmail: '',
  guardianRelationship: 'MOTHER',
  medicalNotes: '',
};

const emptyFeeForm: FeeForm = {
  name: '',
  sessionId: '',
  termId: '',
  classIds: [],
  dueDate: '',
  applyAfterDays: '14',
  percentageAmount: '5',
  items: [
    { description: 'Tuition', amount: '', isMandatory: true },
    { description: 'Books', amount: '', isMandatory: true },
    { description: 'PTA Levy', amount: '', isMandatory: true },
  ],
};

const sections: Array<{ id: Section; label: string; icon: React.ComponentType<{ className?: string }> }> = [
  { id: 'overview', label: 'Overview', icon: LayoutDashboard },
  { id: 'setup', label: 'Setup', icon: Settings },
  { id: 'classes', label: 'Classes', icon: GraduationCap },
  { id: 'people', label: 'People', icon: Users },
  { id: 'students', label: 'Students', icon: BookOpen },
  { id: 'attendance', label: 'Attendance', icon: CalendarCheck },
  { id: 'subjects', label: 'Subjects', icon: BookMarked },
  { id: 'results', label: 'Results', icon: ShieldCheck },
  { id: 'fees', label: 'Fees', icon: Banknote },
  { id: 'notifications', label: 'Notifications', icon: BellRing },
];

export const AdminDashboard: React.FC = () => {
  const { user, logout, isSuperAdmin } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const { clearSchool } = useSchoolStore();
  const routeSchoolId = useMemo(
    () => location.pathname.match(/^\/super-admin\/schools\/([^/]+)\//)?.[1] ?? null,
    [location.pathname],
  );

  const isImpersonating = useMemo(() => {
    return !!(isSuperAdmin && location.pathname.startsWith('/super-admin/schools/'));
  }, [isSuperAdmin, location.pathname]);

  const currentSectionFromUrl = useMemo(() => {
    if (!isImpersonating) return null;
    const managedSection = location.pathname.match(/\/manage\/([^/?#]+)/)?.[1] as Section | undefined;
    if (managedSection && sections.some((section) => section.id === managedSection)) {
      return managedSection;
    }
    if (location.pathname.endsWith('/dashboard')) return 'overview';
    if (location.pathname.endsWith('/users')) return 'people';
    if (location.pathname.endsWith('/fees')) return 'fees';
    if (location.pathname.endsWith('/reports')) return 'overview';
    return null;
  }, [location.pathname, isImpersonating]);

  const [activeSection, setActiveSection] = useState<Section>('overview');

  useEffect(() => {
    if (currentSectionFromUrl) {
      setActiveSection(currentSectionFromUrl as Section);
    }
  }, [currentSectionFromUrl]);

  const handleSectionChange = (sectionId: Section) => {
    if (isImpersonating && routeSchoolId) {
      navigate(`/super-admin/schools/${routeSchoolId}/manage/${sectionId}`);
    } else {
      setActiveSection(sectionId);
    }
  };
  const [school, setSchool] = useState<SchoolProfile | null>(null);
  const [availableLevels, setAvailableLevels] = useState<GradeLevel[]>([]);
  const [enabledLevels, setEnabledLevels] = useState<GradeLevel[]>([]);
  const [selectedLevelCodes, setSelectedLevelCodes] = useState<string[]>([]);
  const [sessions, setSessions] = useState<AcademicSession[]>([]);
  const [classes, setClasses] = useState<ClassRoom[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [students, setStudents] = useState<StudentSummary[]>([]);
  const [feeDashboard, setFeeDashboard] = useState<FeeDashboard | null>(null);
  const [feeStructures, setFeeStructures] = useState<FeeStructure[]>([]);
  const [dailySummary, setDailySummary] = useState<DailySummary | null>(null);
  const [notificationBalance, setNotificationBalance] = useState<NotificationBalance | null>(null);
  const [templates, setTemplates] = useState<NotificationTemplate[]>([]);
  const [studentSearch, setStudentSearch] = useState('');
  const [selectedClassFilter, setSelectedClassFilter] = useState<string>('ALL');
  const [classForm, setClassForm] = useState<ClassForm>(emptyClassForm);
  const [staffForm, setStaffForm] = useState<StaffForm>(emptyStaffForm);
  const [studentForm, setStudentForm] = useState<StudentForm>(emptyStudentForm);
  const [feeForm, setFeeForm] = useState<FeeForm>(emptyFeeForm);
  const [createDialog, setCreateDialog] = useState<'class' | 'staff' | 'student' | 'fee' | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [selectedStudentId, setSelectedStudentId] = useState<string | null>(null);

  // Attendance monitoring state
  const [attendanceByClass, setAttendanceByClass] = useState<Record<string, TodayAttendanceResponse>>({});
  const [selectedAttendanceClassId, setSelectedAttendanceClassId] = useState('');
  const [selectedAttendanceStudentId, setSelectedAttendanceStudentId] = useState('');
  const [attendanceHistory, setAttendanceHistory] = useState<AttendanceResponse[]>([]);
  const [attendanceSummary, setAttendanceSummary] = useState<AttendanceSummaryResponse | null>(null);
  const [isAttendanceLoading, setIsAttendanceLoading] = useState(false);
  const [isAttendanceOverviewLoading, setIsAttendanceOverviewLoading] = useState(false);

  // Subjects state
  const [subjects, setSubjects] = useState<SubjectResponse[]>([]);
  const [classSubjects, setClassSubjects] = useState<ClassSubjectResponse[]>([]);
  const [selectedClassForSubjects, setSelectedClassForSubjects] = useState<string>('');
  const [subjectDialogOpen, setSubjectDialogOpen] = useState(false);
  const [subjectForm, setSubjectForm] = useState<SubjectForm>({ name: '', code: '', category: '' });
  const [selectedSubject, setSelectedSubject] = useState<SubjectResponse | null>(null);
  const [assignSubjectDialogOpen, setAssignSubjectDialogOpen] = useState(false);
  const [assignForm, setAssignForm] = useState({ subjectId: '', teacherId: '' });

  // Results / CA Configuration state
  const [caComponents, setCaComponents] = useState<Array<{ id?: string; name: string; maxScore: number; weightPercentage: number; sortOrder: number }>>([]);
  const [examWeight, setExamWeight] = useState<number>(60);

  // Grading rules state
  const [gradingRules, setGradingRules] = useState<GradingRulesResponse | null>(null);

  // Exam sessions state
  const [termExams, setTermExams] = useState<ExamLookupResponse[]>([]);

  // Results management state
  const [selectedClassForResults, setSelectedClassForResults] = useState<string>('');
  const [selectedTermForResults, setSelectedTermForResults] = useState<string>('');
  const [classResults, setClassResults] = useState<any>(null);
  const [isResultsLoading, setIsResultsLoading] = useState(false);
  const [commentDialogOpen, setCommentDialogOpen] = useState(false);
  const [selectedStudentForComment, setSelectedStudentForComment] = useState<{ id: string; name: string; termId: string; comment: string } | null>(null);
  const [reportCardJob, setReportCardJob] = useState<{ id: string; status: string; progress?: number } | null>(null);

  // Payments / offline payments state
  const [payments, setPayments] = useState<any[]>([]);
  const [recordPaymentDialogOpen, setRecordPaymentDialogOpen] = useState(false);
  const [offlinePaymentForm, setOfflinePaymentForm] = useState({ studentId: '', studentFeeId: '', balance: 0, amount: '', paymentMethod: 'CASH', receivedBy: '', feeName: '' });

  // Student Promotion state
  const [promotionDialogOpen, setPromotionDialogOpen] = useState(false);
  const [promotionForm, setPromotionForm] = useState({ fromClassId: '', toClassId: '', targetSessionId: '', selectedStudentIds: [] as string[] });

  // Template editing state
  const [editTemplateDialogOpen, setEditTemplateDialogOpen] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<{ templateId: string; name: string; body: string; isActive: boolean } | null>(null);

  const currentSession = useMemo(
    () => sessions.find((session) => session.isCurrent) ?? sessions[0],
    [sessions],
  );
  const currentTerm = useMemo(
    () => currentSession?.terms?.find((term) => term.isCurrent) ?? currentSession?.terms?.[0],
    [currentSession],
  );

  const terms = useMemo(() => currentSession?.terms ?? [], [currentSession]);

  const staffCount = users.filter((item) => item.userType !== 'PARENT').length;
  const activeStudentCount = students.length;
  const feeSummary = feeDashboard?.summary;
  const setupItems = useMemo(
    () => [
      { label: 'School profile', done: Boolean(school), target: 'overview' as Section },
      { label: 'Grade levels', done: enabledLevels.length > 0, target: 'setup' as Section },
      { label: 'Academic session', done: sessions.length > 0, target: 'setup' as Section },
      { label: 'Classes', done: classes.length > 0, target: 'classes' as Section },
      { label: 'Staff accounts', done: staffCount > 0, target: 'people' as Section },
      { label: 'Students', done: activeStudentCount > 0, target: 'students' as Section },
      { label: 'Subjects', done: subjects.length > 0, target: 'subjects' as Section },
      { label: 'Grading rules', done: (gradingRules?.gradesCount ?? 0) > 0, target: 'setup' as Section },
      { label: 'CA / Exam config', done: caComponents.length > 0, target: 'setup' as Section },
      { label: 'Fee structures', done: feeStructures.length > 0, target: 'fees' as Section },
      { label: 'Assigned fees', done: Number(feeSummary?.totalExpected ?? 0) > 0, target: 'fees' as Section },
    ],
    [activeStudentCount, caComponents.length, classes.length, enabledLevels.length, feeStructures.length, feeSummary?.totalExpected, gradingRules?.gradesCount, school, sessions.length, staffCount, subjects.length],
  );
  const setupComplete = setupItems.filter((item) => item.done).length;
  const setupProgress = Math.round((setupComplete / setupItems.length) * 100);

  const visibleStudents = useMemo(() => {
    const normalizedSearch = studentSearch.trim().toLowerCase();
    return students.filter((student) => {
      const classMatches = selectedClassFilter === 'ALL' || student.currentClass?.classId === selectedClassFilter;
      if (!classMatches) return false;
      if (!normalizedSearch) return true;
      return [
        student.firstName,
        student.lastName,
        student.middleName,
        student.admissionNumber,
        student.parentName,
        student.parentPhone,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedSearch));
    });
  }, [selectedClassFilter, studentSearch, students]);

  useEffect(() => {
    void loadDashboard();
  }, []);

  useEffect(() => {
    const currentSessionId = currentSession?.sessionId ?? '';
    const currentTermId = currentTerm?.termId ?? '';
    setClassForm((form) => ({ ...form, academicSessionId: form.academicSessionId || currentSessionId }));
    setFeeForm((form) => ({
      ...form,
      sessionId: form.sessionId || currentSessionId,
      termId: form.termId || currentTermId,
    }));
  }, [currentSession?.sessionId, currentTerm?.termId]);

  const loadDashboard = async () => {
    setIsLoading(true);
    setError(null);

    const endDate = toDateInputValue(new Date());
    const startDate = toDateInputValue(daysAgo(6));
    const [
      schoolResult,
      availableLevelsResult,
      enabledLevelsResult,
      sessionsResult,
      classesResult,
      usersResult,
      studentsResult,
      feeDashboardResult,
      feeStructuresResult,
      dailySummaryResult,
      balanceResult,
      templatesResult,
      subjectsResult,
      caConfigResult,
      caComponentsResult,
      paymentsResult,
      gradingRulesResult,
    ] = await Promise.allSettled([
      schoolAdminService.getCurrentSchool(),
      schoolAdminService.getAvailableGradeLevels(),
      schoolAdminService.getGradeLevels(),
      schoolAdminService.getSessions(),
      schoolAdminService.listClasses(),
      schoolAdminService.listUsers(),
      schoolAdminService.listStudents(),
      schoolAdminService.getFeeDashboard(),
      schoolAdminService.getFeeStructures(),
      schoolAdminService.getDailySummary(startDate, endDate),
      schoolAdminService.getNotificationBalance(),
      schoolAdminService.getNotificationTemplates(),
      schoolAdminService.listSubjects(),
      schoolAdminService.getCaConfig(),
      schoolAdminService.getCaComponents(),
      schoolAdminService.getPaymentHistory(),
      schoolAdminService.getGradingRules(),
    ]);

    if (schoolResult.status === 'fulfilled') setSchool(schoolResult.value);
    if (availableLevelsResult.status === 'fulfilled') setAvailableLevels(availableLevelsResult.value);
    if (enabledLevelsResult.status === 'fulfilled') {
      setEnabledLevels(enabledLevelsResult.value);
      setSelectedLevelCodes(enabledLevelsResult.value.map((level) => level.code));
    }
    if (sessionsResult.status === 'fulfilled') setSessions(sessionsResult.value);
    if (classesResult.status === 'fulfilled') {
      setClasses(classesResult.value);
      setSelectedAttendanceClassId((current) => current || classesResult.value[0]?.classId || '');
      void loadAttendanceOverview(classesResult.value);
    }
    if (usersResult.status === 'fulfilled') setUsers(usersResult.value.content ?? []);
    if (studentsResult.status === 'fulfilled') setStudents(studentsResult.value.content ?? []);
    if (feeDashboardResult.status === 'fulfilled') setFeeDashboard(feeDashboardResult.value);
    if (feeStructuresResult.status === 'fulfilled') setFeeStructures(feeStructuresResult.value);
    if (dailySummaryResult.status === 'fulfilled') setDailySummary(dailySummaryResult.value);
    if (balanceResult.status === 'fulfilled') setNotificationBalance(balanceResult.value);
    if (templatesResult.status === 'fulfilled') setTemplates(templatesResult.value);
    if (subjectsResult.status === 'fulfilled') setSubjects(subjectsResult.value);
    if (caConfigResult.status === 'fulfilled') {
      setExamWeight(caConfigResult.value.examWeightPercentage ?? 60);
    }
    if (caComponentsResult.status === 'fulfilled') {
      setCaComponents(
        caComponentsResult.value.map((comp) => ({
          id: comp.id,
          name: comp.name,
          maxScore: comp.maxScore,
          weightPercentage: comp.weightPercentage ?? 0,
          sortOrder: comp.sortOrder ?? 0,
        }))
      );
    }
    if (paymentsResult.status === 'fulfilled') {
      setPayments(paymentsResult.value.content ?? []);
    }
    if (gradingRulesResult.status === 'fulfilled') {
      setGradingRules(gradingRulesResult.value);
    }

    const failed = [
      schoolResult,
      availableLevelsResult,
      enabledLevelsResult,
      sessionsResult,
      classesResult,
      usersResult,
      studentsResult,
      feeDashboardResult,
      feeStructuresResult,
      dailySummaryResult,
      balanceResult,
      templatesResult,
      paymentsResult,
    ].some((result) => result.status === 'rejected');

    if (failed) {
      setNotice('Some panels could not refresh. Available school data is shown.');
    }
    setIsLoading(false);
  };

  const loadAttendanceOverview = async (classList = classes) => {
    if (!classList.length) {
      setAttendanceByClass({});
      return;
    }
    setIsAttendanceOverviewLoading(true);
    try {
      const entries = await Promise.all(
        classList.map(async (classRoom) => {
          try {
            return [classRoom.classId, await schoolAdminService.getTodayClassAttendance(classRoom.classId)] as const;
          } catch {
            return [classRoom.classId, {
              classId: classRoom.classId,
              className: classRoom.name,
              date: toDateInputValue(new Date()),
              totalStudents: classRoom.currentEnrollment ?? 0,
              present: 0,
              absent: 0,
              late: 0,
              notMarked: classRoom.currentEnrollment ?? 0,
              students: [],
            }] as const;
          }
        }),
      );
      setAttendanceByClass(Object.fromEntries(entries));
    } finally {
      setIsAttendanceOverviewLoading(false);
    }
  };

  const loadStudentAttendance = async (studentId: string) => {
    if (!currentTerm?.termId) {
      setError('A current term is required to view student attendance history.');
      return;
    }
    setSelectedAttendanceStudentId(studentId);
    setIsAttendanceLoading(true);
    setError(null);
    try {
      const [history, summary] = await Promise.all([
        schoolAdminService.getStudentAttendance(studentId, currentTerm.termId),
        schoolAdminService.getStudentAttendanceSummary(studentId, currentTerm.termId),
      ]);
      setAttendanceHistory(history);
      setAttendanceSummary(summary);
    } catch (err) {
      setError(readError(err, 'Unable to load student attendance history.'));
      setAttendanceHistory([]);
      setAttendanceSummary(null);
    } finally {
      setIsAttendanceLoading(false);
    }
  };

  const saveGradeLevels = async () => {
    if (selectedLevelCodes.length === 0) {
      setError('Select at least one grade level.');
      return;
    }
    await runAction(async () => {
      await schoolAdminService.configureGradeLevels(selectedLevelCodes);
      setNotice('Grade levels saved.');
      await loadDashboard();
    });
  };

  const handleSaveCaConfig = async (
    components: Array<{ name: string; maxScore: number; weightPercentage: number; sortOrder: number }>,
    examWeightPercentage: number
  ) => {
    await runAction(async () => {
      await schoolAdminService.configureCaComponents({
        components,
        examWeightPercentage,
      });
      setNotice('Results CA components and exam configuration saved.');
      await loadDashboard();
    });
  };

  const handleSaveGradingRules = async (
    grades: Array<{ grade: string; minScore: number; maxScore: number; remark: string }>,
    passMark: number
  ) => {
    await runAction(async () => {
      await schoolAdminService.configureGradingRules({
        config: { grades, passMark },
      });
      setNotice('Grading rules saved successfully.');
      await loadDashboard();
    });
  };

  // Load exam sessions for current term
  useEffect(() => {
    if (!currentTerm?.termId) return;
    schoolAdminService
      .getExamsForTerm(currentTerm.termId)
      .then((exams) => setTermExams(exams))
      .catch(() => setTermExams([]));
  }, [currentTerm?.termId]);

  // Set default filters for results sheet
  useEffect(() => {
    if (classes.length > 0 && !selectedClassForResults) {
      setSelectedClassForResults(classes[0].classId);
    }
  }, [classes, selectedClassForResults]);

  useEffect(() => {
    if (currentTerm && !selectedTermForResults) {
      setSelectedTermForResults(currentTerm.termId);
    }
  }, [currentTerm, selectedTermForResults]);

  const fetchResults = useCallback(async () => {
    if (!selectedClassForResults || !selectedTermForResults) return;
    setIsResultsLoading(true);
    try {
      const res = await schoolAdminService.getClassResults(selectedClassForResults, selectedTermForResults);
      setClassResults(res);
    } catch (err) {
      console.error(err);
    } finally {
      setIsResultsLoading(false);
    }
  }, [selectedClassForResults, selectedTermForResults]);

  useEffect(() => {
    void fetchResults();
  }, [fetchResults]);

  // Poll for report card job status
  useEffect(() => {
    if (!reportCardJob || reportCardJob.status === 'COMPLETED' || reportCardJob.status === 'FAILED') {
      if (reportCardJob) {
        setIsSaving(false);
      }
      return;
    }

    const interval = setInterval(async () => {
      try {
        const statusRes = (await schoolAdminService.checkReportCardJob(reportCardJob.id)) as any;
        setReportCardJob({
          id: reportCardJob.id,
          status: statusRes.status,
          progress: statusRes.completedStudents && statusRes.totalStudents
            ? Math.round((statusRes.completedStudents / statusRes.totalStudents) * 100)
            : 0
        });

        if (statusRes.status === 'COMPLETED') {
          setNotice('Report cards generated successfully!');
          if (statusRes.downloadUrl) {
            const data = await schoolAdminService.downloadReportCardPdf(statusRes.downloadUrl);
            const blob = new Blob([data], { type: 'application/pdf' });
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `report-cards-${selectedClassForResults}.pdf`);
            document.body.appendChild(link);
            link.click();
            link.parentNode?.removeChild(link);
          } else {
            setNotice('Report cards generated successfully! (Note: PDF rendering completed on server)');
          }
          clearInterval(interval);
        } else if (statusRes.status === 'FAILED') {
          setNotice(`Report card generation job failed: ${statusRes.message || 'Unknown error'}`);
          clearInterval(interval);
        }
      } catch (err) {
        console.error('Error polling report card job status', err);
        clearInterval(interval);
        setReportCardJob(null);
        setIsSaving(false);
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [reportCardJob, selectedClassForResults]);

  const handleOpenCommentDialog = async (studentId: string, studentName: string) => {
    setSelectedStudentForComment({ id: studentId, name: studentName, termId: selectedTermForResults, comment: '' });
    setCommentDialogOpen(true);
    try {
      const resultData = await schoolAdminService.getStudentResult(studentId, selectedTermForResults);
      setSelectedStudentForComment({
        id: studentId,
        name: studentName,
        termId: selectedTermForResults,
        comment: resultData.principalComment || '',
      });
    } catch (err) {
      console.error('Failed to load existing principal comment', err);
    }
  };

  const handleSaveComment = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedStudentForComment) return;
    setIsSaving(true);
    try {
      await schoolAdminService.addPrincipalComment(
        selectedStudentForComment.id,
        selectedStudentForComment.termId,
        selectedStudentForComment.comment
      );
      setNotice(`Principal comment saved for student.`);
      setCommentDialogOpen(false);
      await fetchResults();
    } catch (err) {
      console.error(err);
      setNotice(err instanceof Error ? err.message : 'Failed to save principal comment.');
    } finally {
      setIsSaving(false);
    }
  };

  const handlePublishResults = async () => {
    if (!selectedTermForResults) return;
    setIsSaving(true);
    try {
      await schoolAdminService.publishResults(selectedTermForResults);
      setNotice('Term results published successfully! Parents can now view results.');
      await fetchResults();
    } catch (err) {
      console.error(err);
      setNotice(err instanceof Error ? err.message : 'Failed to publish term results.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleUnpublishResults = async () => {
    if (!selectedTermForResults) return;
    setIsSaving(true);
    try {
      await schoolAdminService.unpublishResults(selectedTermForResults);
      setNotice('Term results unpublished successfully. Teachers can now edit grades.');
      await fetchResults();
    } catch (err) {
      console.error(err);
      setNotice(err instanceof Error ? err.message : 'Failed to unpublish term results.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleRecomputeRankings = async () => {
    if (!selectedClassForResults || !selectedTermForResults) return;
    setIsSaving(true);
    try {
      const res = await schoolAdminService.recomputeRankings(selectedClassForResults, selectedTermForResults) as any;
      setNotice(res?.message || 'Rankings recomputed successfully!');
      await fetchResults();
    } catch (err) {
      console.error(err);
      setNotice(err instanceof Error ? err.message : 'Failed to recompute rankings.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleGenerateReportCards = async (studentIds?: string[]) => {
    if (!selectedClassForResults || !selectedTermForResults) return;
    setIsSaving(true);
    try {
      const res = await schoolAdminService.generateReportCards({
        classId: selectedClassForResults,
        termId: selectedTermForResults,
        studentIds: studentIds ?? classResults?.students?.map((s: any) => s.studentId),
        format: 'PDF',
      });
      setReportCardJob({ id: res.jobId, status: 'PENDING', progress: 0 });
      setNotice('Report card generation job started.');
    } catch (err) {
      console.error(err);
      setNotice(err instanceof Error ? err.message : 'Failed to generate report cards.');
      setIsSaving(false);
    }
  };

  const handleDownloadReceipt = async (receiptNumber: string) => {
    try {
      const blob = await schoolAdminService.downloadReceiptPdf(receiptNumber);
      const url = window.URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `receipt-${receiptNumber}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.parentNode?.removeChild(link);
    } catch (err) {
      console.error('Failed to download receipt', err);
      setNotice('Error downloading receipt PDF.');
    }
  };

  const handleDownloadFeeCollectionReport = async () => {
    try {
      const blob = await schoolAdminService.getFeeCollectionReport({ termId: 'current', format: 'PDF' });
      const url = window.URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `fee-collection-report.pdf`);
      document.body.appendChild(link);
      link.click();
      link.parentNode?.removeChild(link);
      setNotice('Fee collection report downloaded successfully.');
    } catch (err) {
      console.error('Failed to download fee collection report', err);
      setNotice('Error downloading fee collection report PDF.');
    }
  };

  const handleSendOverdueReminders = async () => {
    setIsSaving(true);
    try {
      const overdueIds = await schoolAdminService.getOutstandingFeeIds('current', 'overdue');
      if (overdueIds.length === 0) {
        setNotice('No overdue student fee accounts found.');
        setIsSaving(false);
        return;
      }
      await schoolAdminService.sendBulkNotifications({
        studentFeeIds: overdueIds,
        templateCode: 'FEE_REMINDER',
        channel: 'SMS',
      });
      setNotice(`Bulk overdue fee reminders sent successfully to ${overdueIds.length} parents!`);
    } catch (err) {
      console.error(err);
      setNotice(err instanceof Error ? err.message : 'Failed to send bulk reminders.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleRecordOfflinePayment = (studentId: string, studentFeeId: string, balance: number, feeName: string) => {
    setOfflinePaymentForm({
      studentId,
      studentFeeId,
      balance,
      amount: balance.toString(),
      paymentMethod: 'CASH',
      receivedBy: school?.name || 'Administrator',
      feeName,
    });
    setRecordPaymentDialogOpen(true);
  };

  const submitOfflinePayment = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSaving(true);
    try {
      const result = await schoolAdminService.recordOfflinePayment({
        studentFeeId: offlinePaymentForm.studentFeeId,
        amount: Number(offlinePaymentForm.amount),
        paymentMethod: offlinePaymentForm.paymentMethod as any,
        receivedBy: offlinePaymentForm.receivedBy,
        generateReceipt: true,
      }) as any;

      setNotice('Offline payment recorded successfully!');
      setRecordPaymentDialogOpen(false);

      if (result && result.receiptNumber) {
        await handleDownloadReceipt(result.receiptNumber);
      }

      await loadDashboard();

      if (selectedStudentId) {
        const prevId = selectedStudentId;
        setSelectedStudentId(null);
        setTimeout(() => setSelectedStudentId(prevId), 50);
      }
    } catch (err) {
      console.error(err);
      setNotice(err instanceof Error ? err.message : 'Failed to record offline payment.');
    } finally {
      setIsSaving(false);
    }
  };

  const handlePromoteStudents = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSaving(true);
    try {
      await schoolAdminService.promoteStudents({
        fromClassId: promotionForm.fromClassId,
        toClassId: promotionForm.toClassId,
        newSessionId: promotionForm.targetSessionId,
        studentIds: promotionForm.selectedStudentIds,
      });
      setNotice(`Successfully promoted ${promotionForm.selectedStudentIds.length} students!`);
      setPromotionDialogOpen(false);
      await loadDashboard();
    } catch (err) {
      console.error(err);
      setNotice(err instanceof Error ? err.message : 'Failed to promote students.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleUpdateTemplate = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedTemplate) return;
    setIsSaving(true);
    try {
      await schoolAdminService.updateNotificationTemplate(selectedTemplate.templateId, {
        name: selectedTemplate.name,
        body: selectedTemplate.body,
        isActive: selectedTemplate.isActive,
      });
      setNotice('Notification template updated successfully.');
      setEditTemplateDialogOpen(false);
      const updatedTemplates = await schoolAdminService.getNotificationTemplates();
      setTemplates(updatedTemplates);
    } catch (err) {
      console.error(err);
      setNotice(err instanceof Error ? err.message : 'Failed to update notification template.');
    } finally {
      setIsSaving(false);
    }
  };

  const createClass = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await runAction(async () => {
      await schoolAdminService.createClass({
        name: classForm.name.trim(),
        gradeLevel: classForm.gradeLevel,
        section: trimOptional(classForm.section),
        academicSessionId: classForm.academicSessionId,
        capacity: Number(classForm.capacity),
      });
      setCreateDialog(null);
      setClassForm({ ...emptyClassForm, academicSessionId: currentSession?.sessionId ?? '' });
      setNotice('Class created.');
      await loadDashboard();
    });
  };

  const createStaff = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await runAction(async () => {
      await schoolAdminService.createStaff({
        firstName: staffForm.firstName.trim(),
        lastName: staffForm.lastName.trim(),
        email: staffForm.email.trim(),
        phoneNumber: staffForm.phoneNumber.trim(),
        userType: staffForm.userType,
        roles: [staffForm.userType],
      });
      setCreateDialog(null);
      setStaffForm(emptyStaffForm);
      setNotice('Staff account created.');
      await loadDashboard();
    });
  };

  const enrollStudent = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await runAction(async () => {
      await schoolAdminService.enrollStudent({
        firstName: studentForm.firstName.trim(),
        lastName: studentForm.lastName.trim(),
        middleName: trimOptional(studentForm.middleName),
        gender: studentForm.gender,
        dateOfBirth: trimOptional(studentForm.dateOfBirth),
        classId: studentForm.classId,
        medicalNotes: trimOptional(studentForm.medicalNotes),
        guardians: [
          {
            firstName: studentForm.guardianFirstName.trim(),
            lastName: studentForm.guardianLastName.trim(),
            phone: studentForm.guardianPhone.trim(),
            email: trimOptional(studentForm.guardianEmail),
            relationship: studentForm.guardianRelationship.trim() || 'GUARDIAN',
            isPrimaryContact: true,
            canPickUpChild: true,
            canViewFees: true,
            canViewResults: true,
            canViewAttendance: true,
            canReceiveSms: true,
            contactPriority: 1,
          },
        ],
      });
      setCreateDialog(null);
      setStudentForm(emptyStudentForm);
      setNotice('Student enrolled.');
      await loadDashboard();
    });
  };

  const createFeeStructure = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const payload: CreateFeeStructurePayload = {
      name: feeForm.name.trim(),
      sessionId: feeForm.sessionId,
      termId: feeForm.termId,
      applicableToClassIds: feeForm.classIds,
      dueDate: feeForm.dueDate,
      items: feeForm.items
        .filter((item) => item.description.trim() && Number(item.amount) > 0)
        .map((item, index) => ({
          description: item.description.trim(),
          amount: Number(item.amount),
          isMandatory: item.isMandatory,
          sortOrder: index + 1,
        })),
      lateFeeConfig: {
        applyAfterDays: Number(feeForm.applyAfterDays),
        percentageAmount: Number(feeForm.percentageAmount),
      },
    };

    if (payload.items.length === 0) {
      setError('Add at least one fee item.');
      return;
    }

    await runAction(async () => {
      await schoolAdminService.createFeeStructure(payload);
      setCreateDialog(null);
      setFeeForm({
        ...emptyFeeForm,
        sessionId: currentSession?.sessionId ?? '',
        termId: currentTerm?.termId ?? '',
      });
      setNotice('Fee structure created.');
      await loadDashboard();
    });
  };

  const assignFeeStructure = async (structure: FeeStructure) => {
    await runAction(async () => {
      await schoolAdminService.assignFeeStructure(structure.structureId);
      setNotice(`${structure.name} assignment started.`);
      await loadDashboard();
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

  const handleClassForSubjectsChange = async (classId: string) => {
    setSelectedClassForSubjects(classId);
    if (classId && classId !== 'NONE') {
      setIsLoading(true);
      setError(null);
      try {
        const data = await schoolAdminService.getSubjectsForClass(classId);
        setClassSubjects(data);
      } catch (err) {
        setError(readError(err, 'Failed to load class subjects.'));
      } finally {
        setIsLoading(false);
      }
    } else {
      setClassSubjects([]);
    }
  };

  const handleCreateSubject = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    await runAction(async () => {
      await schoolAdminService.createSubject({
        name: subjectForm.name,
        code: trimOptional(subjectForm.code),
        category: trimOptional(subjectForm.category),
      });
      setSubjectDialogOpen(false);
      setSubjectForm({ name: '', code: '', category: '' });
      setNotice('Subject created successfully.');
      await loadDashboard();
    });
  };

  const handleUpdateSubject = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selectedSubject) return;
    await runAction(async () => {
      await schoolAdminService.updateSubject(selectedSubject.subjectId, {
        name: subjectForm.name,
        code: trimOptional(subjectForm.code),
        category: trimOptional(subjectForm.category),
      });
      setSubjectDialogOpen(false);
      setSelectedSubject(null);
      setSubjectForm({ name: '', code: '', category: '' });
      setNotice('Subject updated successfully.');
      await loadDashboard();
    });
  };

  const handleDeactivateSubject = async (subjectId: string) => {
    if (!confirm('Are you sure you want to deactivate this subject?')) return;
    await runAction(async () => {
      await schoolAdminService.deactivateSubject(subjectId);
      setNotice('Subject deactivated successfully.');
      await loadDashboard();
    });
  };

  const handleAssignSubjectToClass = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selectedClassForSubjects || selectedClassForSubjects === 'NONE') return;
    await runAction(async () => {
      await schoolAdminService.assignSubjectToClass(selectedClassForSubjects, {
        subjectId: assignForm.subjectId,
        teacherId: assignForm.teacherId && assignForm.teacherId !== 'NONE' ? assignForm.teacherId : undefined,
      });
      setAssignSubjectDialogOpen(false);
      setAssignForm({ subjectId: '', teacherId: '' });
      setNotice('Subject assigned to class successfully.');
      const data = await schoolAdminService.getSubjectsForClass(selectedClassForSubjects);
      setClassSubjects(data);
    });
  };

  const handleRemoveSubjectFromClass = async (subjectId: string) => {
    if (!selectedClassForSubjects || selectedClassForSubjects === 'NONE') return;
    if (!confirm('Are you sure you want to remove this subject from the class?')) return;
    await runAction(async () => {
      await schoolAdminService.removeSubjectFromClass(selectedClassForSubjects, subjectId);
      setNotice('Subject removed from class successfully.');
      const data = await schoolAdminService.getSubjectsForClass(selectedClassForSubjects);
      setClassSubjects(data);
    });
  };

  const toggleGradeLevel = (code: string, checked: boolean) => {
    setSelectedLevelCodes((current) =>
      checked ? Array.from(new Set([...current, code])) : current.filter((item) => item !== code),
    );
  };

  const toggleFeeClass = (classId: string, checked: boolean) => {
    setFeeForm((form) => ({
      ...form,
      classIds: checked ? Array.from(new Set([...form.classIds, classId])) : form.classIds.filter((id) => id !== classId),
    }));
  };

  const updateFeeItem = (index: number, patch: Partial<FeeItemForm>) => {
    setFeeForm((form) => ({
      ...form,
      items: form.items.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)),
    }));
  };

  const addFeeItem = () => {
    setFeeForm((form) => ({
      ...form,
      items: [...form.items, { description: '', amount: '', isMandatory: true }],
    }));
  };

  const removeFeeItem = (index: number) => {
    setFeeForm((form) => ({
      ...form,
      items: form.items.filter((_, itemIndex) => itemIndex !== index),
    }));
  };

  return (
    <div className="min-h-screen bg-slate-100 text-slate-950">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-72 border-r border-slate-200 bg-white lg:flex lg:flex-col">
        <div className="px-6 py-6">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-md bg-slate-950 text-white">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-slate-950">{school?.name ?? user?.schoolName ?? 'School Admin'}</p>
              <p className="text-xs text-slate-500">{school?.code ?? 'Operations'}</p>
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
                onClick={() => handleSectionChange(section.id)}
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
          {isImpersonating && (
            <Button
              variant="outline"
              className="mb-3 w-full justify-start border-slate-200 text-slate-700 hover:bg-slate-50"
              onClick={() => {
                clearSchool();
                navigate('/super-admin/dashboard');
              }}
            >
              <ShieldCheck className="mr-2 h-4 w-4 text-emerald-650" />
              Super Admin Panel
            </Button>
          )}
          <Button variant="outline" className="w-full justify-start border-slate-200 text-red-600 hover:bg-red-50 hover:text-red-700" onClick={logout}>
            <LogOut className="mr-2 h-4 w-4" />
            Sign Out
          </Button>
        </div>
      </aside>

      <main className="lg:pl-72">
        {isImpersonating && (
          <div className="flex items-center justify-between bg-amber-500 px-4 py-2.5 text-xs font-semibold text-slate-950 md:px-8">
            <div className="flex items-center gap-2">
              <span className="inline-block h-2 w-2 animate-pulse rounded-full bg-red-600" />
              <span>Managing as Super Admin: <strong className="text-slate-950">{school?.name || 'Loading school...'}</strong></span>
            </div>
            <div className="flex gap-4">
              <button
                type="button"
                onClick={() => {
                  clearSchool();
                  navigate('/super-admin');
                }}
                className="rounded bg-slate-950 px-2.5 py-1 text-[11px] font-bold text-white hover:bg-slate-800 transition"
              >
                Switch School
              </button>
              <button
                type="button"
                onClick={() => {
                  clearSchool();
                  navigate('/super-admin/dashboard');
                }}
                className="rounded bg-white px-2.5 py-1 text-[11px] font-bold text-slate-950 border border-slate-200 hover:bg-slate-50 transition"
              >
                Back to Platform
              </button>
            </div>
          </div>
        )}
        <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/95 px-4 py-4 backdrop-blur md:px-8">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="outline" className="rounded-full border-slate-300 bg-white text-slate-700">
                  {isImpersonating ? 'SUPER ADMIN · SCHOOL WORKSPACE' : 'SCHOOL ADMIN'}
                </Badge>
                {school?.status && <Badge className="rounded-full bg-emerald-100 text-emerald-700 hover:bg-emerald-100">{school.status}</Badge>}
              </div>
              <h1 className="mt-2 text-2xl font-semibold tracking-tight text-slate-950 md:text-3xl">
                {school?.name ?? user?.schoolName ?? 'School Operations'}
              </h1>
              <p className="mt-1 text-sm text-slate-500">
                {currentTerm ? `${currentTerm.name} · ${currentSession?.name}` : 'School setup and daily operations'}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => void loadDashboard()} disabled={isLoading || isSaving}>
                {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
                Refresh
              </Button>
              <Button onClick={() => handleSectionChange('setup')} className="bg-slate-950 text-white hover:bg-slate-800">
                <Settings className="mr-2 h-4 w-4" />
                Setup
              </Button>
            </div>
          </div>
        </header>

        <div className="px-4 py-6 md:px-8">
          {error && (
            <div className="mb-5 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}
          {notice && (
            <div className="mb-5 rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-700">
              {notice}
            </div>
          )}

          {isLoading ? (
            <div className="flex min-h-[420px] items-center justify-center rounded-md border border-dashed border-slate-300 bg-white">
              <div className="text-center">
                <Loader2 className="mx-auto h-8 w-8 animate-spin text-slate-500" />
                <p className="mt-3 text-sm text-slate-500">Loading school operations...</p>
              </div>
            </div>
          ) : (
            <>
              {activeSection === 'overview' && (
                <OverviewSection
                  setupComplete={setupComplete}
                  setupItems={setupItems}
                  setupProgress={setupProgress}
                  onGoTo={setActiveSection}
                  feeDashboard={feeDashboard}
                  studentCount={activeStudentCount}
                  staffCount={staffCount}
                  classCount={classes.length}
                  dailySummary={dailySummary}
                  school={school}
                />
              )}

              {activeSection === 'setup' && (
                <SetupSection
                  availableLevels={availableLevels}
                  selectedLevelCodes={selectedLevelCodes}
                  setupItems={setupItems}
                  setupProgress={setupProgress}
                  sessions={sessions}
                  currentSession={currentSession}
                  currentTerm={currentTerm}
                  onToggleGrade={toggleGradeLevel}
                  onSaveGrades={() => void saveGradeLevels()}
                  isSaving={isSaving}
                  caComponents={caComponents}
                  examWeight={examWeight}
                  onSaveCaConfig={handleSaveCaConfig}
                  gradingRules={gradingRules}
                  onSaveGradingRules={handleSaveGradingRules}
                  termExams={termExams}
                />
              )}

              {activeSection === 'classes' && (
                <ClassesSection
                  classes={classes}
                  onCreate={() => setCreateDialog('class')}
                  onPromote={() => {
                    setPromotionForm({
                      fromClassId: '',
                      toClassId: '',
                      targetSessionId: '',
                      selectedStudentIds: [],
                    });
                    setPromotionDialogOpen(true);
                  }}
                />
              )}

              {activeSection === 'people' && (
                <PeopleSection
                  users={users}
                  onCreate={() => setCreateDialog('staff')}
                />
              )}

              {activeSection === 'students' && (
                selectedStudentId ? (
                  <StudentDetailsView
                    studentId={selectedStudentId}
                    onBack={() => setSelectedStudentId(null)}
                    onRecordOfflinePayment={handleRecordOfflinePayment}
                  />
                ) : (
                  <StudentsSection
                    students={visibleStudents}
                    allClasses={classes}
                    search={studentSearch}
                    classFilter={selectedClassFilter}
                    onSearch={setStudentSearch}
                    onClassFilter={setSelectedClassFilter}
                    onCreate={() => setCreateDialog('student')}
                    onStudentClick={(id) => setSelectedStudentId(id)}
                  />
                )
              )}

              {activeSection === 'attendance' && (
                <AttendanceMonitoringSection
                  classes={classes}
                  students={students}
                  attendanceByClass={attendanceByClass}
                  selectedClassId={selectedAttendanceClassId}
                  selectedStudentId={selectedAttendanceStudentId}
                  history={attendanceHistory}
                  summary={attendanceSummary}
                  currentTermName={currentTerm?.name}
                  currentSessionName={currentSession?.name}
                  isLoading={isAttendanceLoading}
                  isOverviewLoading={isAttendanceOverviewLoading}
                  onSelectClass={setSelectedAttendanceClassId}
                  onSelectStudent={(studentId) => void loadStudentAttendance(studentId)}
                  onRefresh={() => void loadAttendanceOverview()}
                />
              )}

              {activeSection === 'fees' && (
                <FeesSection
                  feeDashboard={feeDashboard}
                  feeStructures={feeStructures}
                  classes={classes}
                  onCreate={() => setCreateDialog('fee')}
                  onAssign={(structure) => void assignFeeStructure(structure)}
                  isSaving={isSaving}
                  payments={payments}
                  onDownloadReceipt={handleDownloadReceipt}
                  onSendBulkReminders={handleSendOverdueReminders}
                  onDownloadCollectionReport={handleDownloadFeeCollectionReport}
                />
              )}

              {activeSection === 'notifications' && (
                <NotificationsSection
                  balance={notificationBalance}
                  templates={templates}
                  onEditTemplate={(tpl) => {
                    setSelectedTemplate({
                      templateId: tpl.templateId,
                      name: tpl.name,
                      body: tpl.body,
                      isActive: !!tpl.isActive,
                    });
                    setEditTemplateDialogOpen(true);
                  }}
                />
              )}

              {activeSection === 'subjects' && (
                <SubjectsSection
                  subjects={subjects}
                  classes={classes}
                  classSubjects={classSubjects}
                  selectedClassForSubjects={selectedClassForSubjects}
                  onClassChange={handleClassForSubjectsChange}
                  onCreateSubject={() => {
                    setSelectedSubject(null);
                    setSubjectForm({ name: '', code: '', category: '' });
                    setSubjectDialogOpen(true);
                  }}
                  onEditSubject={(subject) => {
                    setSelectedSubject(subject);
                    setSubjectForm({ name: subject.name, code: subject.code || '', category: subject.category || '' });
                    setSubjectDialogOpen(true);
                  }}
                  onDeactivateSubject={handleDeactivateSubject}
                  onAssignSubject={() => {
                    setAssignForm({ subjectId: '', teacherId: '' });
                    setAssignSubjectDialogOpen(true);
                  }}
                  onRemoveSubject={handleRemoveSubjectFromClass}
                />
              )}

              {activeSection === 'results' && (
                <ResultsSection
                  classes={classes}
                  terms={terms}
                  selectedClassId={selectedClassForResults}
                  selectedTermId={selectedTermForResults}
                  classResults={classResults}
                  isLoading={isResultsLoading}
                  onClassChange={setSelectedClassForResults}
                  onTermChange={setSelectedTermForResults}
                  onAddPrincipalComment={handleOpenCommentDialog}
                  onRecomputeRankings={handleRecomputeRankings}
                  onPublish={handlePublishResults}
                  onUnpublish={handleUnpublishResults}
                  onGenerateReportCards={handleGenerateReportCards}
                  reportCardJob={reportCardJob}
                  isSaving={isSaving}
                />
              )}
            </>
          )}
        </div>
      </main>

      <ClassDialog
        open={createDialog === 'class'}
        form={classForm}
        gradeLevels={enabledLevels.length ? enabledLevels : availableLevels}
        sessions={sessions}
        isSaving={isSaving}
        onOpenChange={(open) => setCreateDialog(open ? 'class' : null)}
        onChange={setClassForm}
        onSubmit={createClass}
      />

      <StaffDialog
        open={createDialog === 'staff'}
        form={staffForm}
        isSaving={isSaving}
        onOpenChange={(open) => setCreateDialog(open ? 'staff' : null)}
        onChange={setStaffForm}
        onSubmit={createStaff}
      />

      <StudentDialog
        open={createDialog === 'student'}
        form={studentForm}
        classes={classes}
        isSaving={isSaving}
        onOpenChange={(open) => setCreateDialog(open ? 'student' : null)}
        onChange={setStudentForm}
        onSubmit={enrollStudent}
      />

      <FeeDialog
        open={createDialog === 'fee'}
        form={feeForm}
        sessions={sessions}
        classes={classes}
        isSaving={isSaving}
        onOpenChange={(open) => setCreateDialog(open ? 'fee' : null)}
        onChange={setFeeForm}
        onToggleClass={toggleFeeClass}
        onUpdateItem={updateFeeItem}
        onAddItem={addFeeItem}
        onRemoveItem={removeFeeItem}
        onSubmit={createFeeStructure}
      />

      <SubjectDialog
        open={subjectDialogOpen}
        form={subjectForm}
        isSaving={isSaving}
        onOpenChange={setSubjectDialogOpen}
        onChange={setSubjectForm}
        onSubmit={selectedSubject ? handleUpdateSubject : handleCreateSubject}
        isEdit={!!selectedSubject}
      />

      <AssignSubjectDialog
        open={assignSubjectDialogOpen}
        form={assignForm}
        isSaving={isSaving}
        onOpenChange={setAssignSubjectDialogOpen}
        onChange={setAssignForm}
        onSubmit={handleAssignSubjectToClass}
        subjects={subjects.filter(s => s.isActive)}
        teachers={users.filter(u => u.userType === 'TEACHER')}
      />

      <CommentDialog
        open={commentDialogOpen}
        form={selectedStudentForComment}
        isSaving={isSaving}
        onOpenChange={setCommentDialogOpen}
        onChange={setSelectedStudentForComment}
        onSubmit={handleSaveComment}
      />

      <OfflinePaymentDialog
        open={recordPaymentDialogOpen}
        form={offlinePaymentForm}
        isSaving={isSaving}
        onOpenChange={setRecordPaymentDialogOpen}
        onChange={setOfflinePaymentForm}
        onSubmit={submitOfflinePayment}
      />

      <PromoteStudentsDialog
        open={promotionDialogOpen}
        form={promotionForm}
        isSaving={isSaving}
        onOpenChange={setPromotionDialogOpen}
        onChange={setPromotionForm}
        onSubmit={handlePromoteStudents}
        classes={classes}
        sessions={sessions}
        students={students}
      />

      <EditTemplateDialog
        open={editTemplateDialogOpen}
        form={selectedTemplate}
        isSaving={isSaving}
        onOpenChange={setEditTemplateDialogOpen}
        onChange={setSelectedTemplate}
        onSubmit={handleUpdateTemplate}
      />
    </div>
  );
};

function OverviewSection({
  setupComplete,
  setupItems,
  setupProgress,
  onGoTo,
  feeDashboard,
  studentCount,
  staffCount,
  classCount,
  dailySummary,
  school,
}: {
  setupComplete: number;
  setupItems: Array<{ label: string; done: boolean; target: Section }>;
  setupProgress: number;
  onGoTo: (section: Section) => void;
  feeDashboard: FeeDashboard | null;
  studentCount: number;
  staffCount: number;
  classCount: number;
  dailySummary: DailySummary | null;
  school: SchoolProfile | null;
}) {
  const collected = Number(feeDashboard?.summary?.totalCollected ?? 0);
  const expected = Number(feeDashboard?.summary?.totalExpected ?? 0);
  const rate = Number(feeDashboard?.summary?.collectionRate ?? 0);

  return (
    <div className="space-y-6">
      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <Metric icon={CircleDollarSign} label="Collected" value={formatCurrency(collected)} detail={expected ? `${rate.toFixed(1)}% of ${formatCurrency(expected)}` : 'No assigned fees'} />
        <Metric icon={BookOpen} label="Students" value={formatNumber(studentCount)} detail="Active enrollment" />
        <Metric icon={Users} label="Staff" value={formatNumber(staffCount)} detail="Active staff accounts" />
        <Metric icon={GraduationCap} label="Classes" value={formatNumber(classCount)} detail="Current session" />
      </section>

      <section className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <div className="rounded-md border border-slate-200 bg-white p-5">
          <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">Setup Progress</p>
              <h2 className="mt-1 text-xl font-semibold text-slate-950">{setupComplete} of {setupItems.length} steps complete</h2>
              <p className="mt-1 text-sm text-slate-500">{school?.name ?? 'Your school'} is ready as each operation moves from setup to daily use.</p>
            </div>
            <Button onClick={() => onGoTo('setup')} className="bg-slate-950 text-white hover:bg-slate-800">
              Continue
              <ChevronRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
          <Progress value={setupProgress} className="mt-5 h-2 bg-slate-100" />
          <div className="mt-5 grid gap-2 md:grid-cols-2">
            {setupItems.map((item) => (
              <button
                key={item.label}
                type="button"
                onClick={() => onGoTo(item.target)}
                className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2 text-left text-sm hover:bg-slate-50"
              >
                <span className="font-medium text-slate-700">{item.label}</span>
                {item.done ? <CheckCircle2 className="h-4 w-4 text-emerald-600" /> : <XCircle className="h-4 w-4 text-slate-300" />}
              </button>
            ))}
          </div>
        </div>

        <div className="rounded-md border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">Today</p>
          <h2 className="mt-1 text-xl font-semibold text-slate-950">{formatCurrency(Number(dailySummary?.totalCollected ?? 0))}</h2>
          <p className="mt-1 text-sm text-slate-500">{formatNumber(Number(dailySummary?.totalTransactions ?? 0))} payment transactions in the selected period</p>
          <div className="mt-5 space-y-3">
            <DeadlineRow label="Due in 3 days" value={feeDashboard?.upcomingDeadlines?.dueIn3Days?.count ?? 0} />
            <DeadlineRow label="Due today" value={feeDashboard?.upcomingDeadlines?.dueToday?.count ?? 0} />
            <DeadlineRow label="Overdue" value={feeDashboard?.upcomingDeadlines?.overdue?.count ?? 0} danger />
          </div>
        </div>
      </section>

      <section className="rounded-md border border-slate-200 bg-white p-5">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-slate-500">Class Collection</p>
            <h2 className="mt-1 text-lg font-semibold text-slate-950">{feeDashboard?.termName ?? 'Current term'}</h2>
          </div>
          <Button variant="outline" onClick={() => onGoTo('fees')}>View Fees</Button>
        </div>
        <div className="mt-5 space-y-3">
          {(feeDashboard?.byClass ?? []).slice(0, 5).map((item) => (
            <div key={item.classId} className="grid gap-2 md:grid-cols-[160px_1fr_120px] md:items-center">
              <p className="text-sm font-medium text-slate-700">{item.className}</p>
              <Progress value={Number(item.collectionRate ?? 0)} className="h-2 bg-slate-100" />
              <p className="text-sm text-slate-500 md:text-right">{Number(item.collectionRate ?? 0).toFixed(1)}%</p>
            </div>
          ))}
          {!(feeDashboard?.byClass?.length) && (
            <p className="rounded-md bg-slate-50 p-4 text-sm text-slate-500">No class collection data yet.</p>
          )}
        </div>
      </section>
    </div>
  );
}

function SetupSection({
  availableLevels,
  selectedLevelCodes,
  setupItems,
  setupProgress,
  sessions,
  currentSession,
  currentTerm,
  onToggleGrade,
  onSaveGrades,
  isSaving,
  caComponents,
  examWeight,
  onSaveCaConfig,
  gradingRules,
  onSaveGradingRules,
  termExams,
}: {
  availableLevels: GradeLevel[];
  selectedLevelCodes: string[];
  setupItems: Array<{ label: string; done: boolean }>;
  setupProgress: number;
  sessions: AcademicSession[];
  currentSession?: AcademicSession;
  currentTerm?: AcademicSession['terms'][number];
  onToggleGrade: (code: string, checked: boolean) => void;
  onSaveGrades: () => void;
  isSaving: boolean;
  caComponents: Array<{ id?: string; name: string; maxScore: number; weightPercentage: number; sortOrder: number }>;
  examWeight: number;
  onSaveCaConfig: (
    components: Array<{ name: string; maxScore: number; weightPercentage: number; sortOrder: number }>,
    examWeightPercentage: number
  ) => Promise<void>;
  gradingRules: GradingRulesResponse | null;
  onSaveGradingRules: (
    grades: Array<{ grade: string; minScore: number; maxScore: number; remark: string }>,
    passMark: number
  ) => Promise<void>;
  termExams: ExamLookupResponse[];
}) {
  const WAEC_DEFAULTS = useMemo(() => [
    { grade: 'A1', minScore: 75, maxScore: 100, remark: 'Excellent' },
    { grade: 'B2', minScore: 70, maxScore: 74, remark: 'Very Good' },
    { grade: 'B3', minScore: 65, maxScore: 69, remark: 'Good' },
    { grade: 'C4', minScore: 60, maxScore: 64, remark: 'Credit' },
    { grade: 'C5', minScore: 55, maxScore: 59, remark: 'Credit' },
    { grade: 'C6', minScore: 50, maxScore: 54, remark: 'Credit' },
    { grade: 'D7', minScore: 45, maxScore: 49, remark: 'Pass' },
    { grade: 'E8', minScore: 40, maxScore: 44, remark: 'Pass' },
    { grade: 'F9', minScore: 0, maxScore: 39, remark: 'Fail' },
  ], []);

  const grouped = groupGradeLevels(availableLevels);

  const [localComps, setLocalComps] = useState<Array<{ name: string; maxScore: number; weightPercentage: number }>>([]);
  const [localExamWeight, setLocalExamWeight] = useState<number>(60);

  // Grading Rules local state
  const [localGrades, setLocalGrades] = useState<Array<{ grade: string; minScore: number; maxScore: number; remark: string }>>([]);
  const [localPassMark, setLocalPassMark] = useState<number>(40);

  useEffect(() => {
    if (gradingRules?.config?.grades && gradingRules.config.grades.length > 0) {
      setLocalGrades(
        gradingRules.config.grades.map((g) => ({
          grade: g.grade,
          minScore: g.minScore,
          maxScore: g.maxScore,
          remark: g.remark,
        }))
      );
      setLocalPassMark(gradingRules.config.passMark);
    } else {
      setLocalGrades(
        WAEC_DEFAULTS.map((g) => ({
          grade: g.grade,
          minScore: g.minScore,
          maxScore: g.maxScore,
          remark: g.remark,
        }))
      );
      setLocalPassMark(40);
    }
  }, [gradingRules, WAEC_DEFAULTS]);

  const handleAddGradeRow = () => {
    setLocalGrades((prev) => [
      ...prev,
      { grade: '', minScore: 0, maxScore: 0, remark: '' },
    ]);
  };

  const handleRemoveGradeRow = (index: number) => {
    setLocalGrades((prev) => prev.filter((_, i) => i !== index));
  };

  const handleUpdateGradeRow = (
    index: number,
    field: 'grade' | 'minScore' | 'maxScore' | 'remark',
    value: any
  ) => {
    setLocalGrades((prev) =>
      prev.map((g, i) => {
        if (i !== index) return g;
        if (field === 'grade' || field === 'remark') return { ...g, [field]: value };
        return { ...g, [field]: Number(value) || 0 };
      })
    );
  };

  const handleResetToWaec = () => {
    setLocalGrades(
      WAEC_DEFAULTS.map((g) => ({
        grade: g.grade,
        minScore: g.minScore,
        maxScore: g.maxScore,
        remark: g.remark,
      }))
    );
    setLocalPassMark(40);
  };

  const gradingRulesError = useMemo(() => {
    if (localGrades.length === 0) {
      return 'At least one grade boundary is required.';
    }
    for (let i = 0; i < localGrades.length; i++) {
      const g = localGrades[i];
      if (!g.grade.trim()) {
        return `Grade label at row ${i + 1} cannot be empty.`;
      }
      if (g.minScore < 0 || g.minScore > 100 || g.maxScore < 0 || g.maxScore > 100) {
        return `Score range for grade ${g.grade} must be between 0 and 100.`;
      }
      if (g.minScore > g.maxScore) {
        return `Min score (${g.minScore}) cannot be greater than Max score (${g.maxScore}) for grade ${g.grade}.`;
      }
    }

    // Check overlap
    for (let i = 0; i < localGrades.length; i++) {
      for (let j = i + 1; j < localGrades.length; j++) {
        const g1 = localGrades[i];
        const g2 = localGrades[j];
        const overlap = Math.max(g1.minScore, g2.minScore) <= Math.min(g1.maxScore, g2.maxScore);
        if (overlap) {
          return `Overlap detected between grade ${g1.grade} (${g1.minScore}-${g1.maxScore}) and grade ${g2.grade} (${g2.minScore}-${g2.maxScore}).`;
        }
      }
    }

    if (localPassMark < 0 || localPassMark > 100) {
      return 'Pass mark must be between 0 and 100.';
    }

    return null;
  }, [localGrades, localPassMark]);

  const handleSaveGradingRulesClick = async () => {
    if (gradingRulesError) return;
    const payload = localGrades.map((g) => ({
      grade: g.grade.trim().toUpperCase(),
      minScore: g.minScore,
      maxScore: g.maxScore,
      remark: g.remark.trim(),
    }));
    await onSaveGradingRules(payload, localPassMark);
  };

  useEffect(() => {
    if (caComponents) {
      setLocalComps(
        caComponents.map((c) => ({
          name: c.name,
          maxScore: c.maxScore,
          weightPercentage: c.weightPercentage,
        }))
      );
    }
  }, [caComponents]);

  useEffect(() => {
    if (examWeight !== undefined) {
      setLocalExamWeight(examWeight);
    }
  }, [examWeight]);

  const handleAddComponent = () => {
    setLocalComps((prev) => [
      ...prev,
      { name: '', maxScore: 20, weightPercentage: 20 },
    ]);
  };

  const handleRemoveComponent = (index: number) => {
    setLocalComps((prev) => prev.filter((_, i) => i !== index));
  };

  const handleUpdateComponent = (index: number, field: 'name' | 'maxScore' | 'weightPercentage', value: any) => {
    setLocalComps((prev) =>
      prev.map((c, i) => {
        if (i !== index) return c;
        if (field === 'name') return { ...c, name: value };
        return { ...c, [field]: Number(value) || 0 };
      })
    );
  };

  const caSum = localComps.reduce((sum, c) => sum + c.weightPercentage, 0);
  const totalSum = caSum + localExamWeight;

  const validationError = useMemo(() => {
    if (localComps.length === 0) {
      return 'At least one CA component is required.';
    }
    for (let i = 0; i < localComps.length; i++) {
      const c = localComps[i];
      if (!c.name.trim()) {
        return `Component #${i + 1} has a blank name.`;
      }
      if (c.maxScore <= 0) {
        return `Max score for "${c.name}" must be greater than 0.`;
      }
      if (c.weightPercentage <= 0) {
        return `Weight percentage for "${c.name}" must be greater than 0%.`;
      }
    }
    const names = localComps.map((c) => c.name.trim().toLowerCase());
    if (names.length !== new Set(names).size) {
      return 'Component names must be unique.';
    }
    if (totalSum !== 100) {
      return `Total weights must equal exactly 100%. Currently: ${totalSum}% (CA: ${caSum}%, Exam: ${localExamWeight}%).`;
    }
    return null;
  }, [localComps, localExamWeight, totalSum, caSum]);

  const isValid = !validationError;

  const handleSave = async () => {
    if (!isValid) return;
    const payload = localComps.map((c, idx) => ({
      name: c.name.trim(),
      maxScore: c.maxScore,
      weightPercentage: c.weightPercentage,
      sortOrder: idx + 1,
    }));
    await onSaveCaConfig(payload, localExamWeight);
  };

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
      <div className="space-y-6">
        <section className="rounded-md border border-slate-200 bg-white p-5">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">Grade Levels</p>
              <h2 className="mt-1 text-xl font-semibold text-slate-950">School configuration</h2>
            </div>
            <Button onClick={onSaveGrades} disabled={isSaving} className="bg-slate-950 text-white hover:bg-slate-800">
              {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <CheckCircle2 className="mr-2 h-4 w-4" />}
              Save
            </Button>
          </div>
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            {Object.entries(grouped).map(([category, levels]) => (
              <div key={category} className="rounded-md border border-slate-200 p-4">
                <h3 className="text-sm font-semibold text-slate-800">{formatCategory(category)}</h3>
                <div className="mt-3 grid gap-2">
                  {levels.map((level) => (
                    <label key={level.code} className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-1.5 hover:bg-slate-50">
                      <Checkbox
                        checked={selectedLevelCodes.includes(level.code)}
                        onCheckedChange={(checked) => onToggleGrade(level.code, checked === true)}
                      />
                      <span className="text-sm text-slate-700">{level.name}</span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="rounded-md border border-slate-200 bg-white p-5">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">CA & Exam Setup</p>
              <h2 className="mt-1 text-xl font-semibold text-slate-950">Results Configuration</h2>
            </div>
            <Button onClick={handleSave} disabled={isSaving || !isValid} className="bg-slate-950 text-white hover:bg-slate-800">
              {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <CheckCircle2 className="mr-2 h-4 w-4" />}
              Save Config
            </Button>
          </div>
          <p className="mt-2 text-sm text-slate-500">
            Define Continuous Assessment (CA) components and the final exam weight. All CA component weights plus the exam weight must sum to exactly 100%.
          </p>

          <div className="mt-6 space-y-4">
            <h3 className="text-sm font-semibold text-slate-800">CA Components</h3>
            <div className="space-y-3">
              {localComps.map((comp, index) => (
                <div key={index} className="grid gap-3 sm:grid-cols-[2fr_1fr_1fr_auto] items-end rounded-md border border-slate-200 p-3 bg-slate-50/50">
                  <div className="w-full">
                    <Label className="text-xs font-medium text-slate-500">Component Name</Label>
                    <Input
                      placeholder="e.g. Test 1, Assignment"
                      value={comp.name}
                      onChange={(e) => handleUpdateComponent(index, 'name', e.target.value)}
                      className="mt-1 h-9 bg-white"
                    />
                  </div>
                  <div className="w-full">
                    <Label className="text-xs font-medium text-slate-500">Max Score (Marks)</Label>
                    <Input
                      type="number"
                      min="1"
                      placeholder="20"
                      value={comp.maxScore || ''}
                      onChange={(e) => handleUpdateComponent(index, 'maxScore', e.target.value)}
                      className="mt-1 h-9 bg-white"
                    />
                  </div>
                  <div className="w-full">
                    <Label className="text-xs font-medium text-slate-500">Weight (%)</Label>
                    <Input
                      type="number"
                      min="0"
                      max="100"
                      placeholder="20"
                      value={comp.weightPercentage || ''}
                      onChange={(e) => handleUpdateComponent(index, 'weightPercentage', e.target.value)}
                      className="mt-1 h-9 bg-white"
                    />
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => handleRemoveComponent(index)}
                    className="h-9 w-9 text-slate-500 hover:text-red-600 hover:bg-red-50"
                  >
                    <XCircle className="h-5 w-5" />
                  </Button>
                </div>
              ))}

              {localComps.length === 0 && (
                <p className="text-sm text-slate-500 italic p-3 border border-dashed border-slate-200 rounded-md text-center bg-slate-50">
                  No CA components added yet. Click "+ Add Component" to start.
                </p>
              )}

              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleAddComponent}
                className="mt-2 text-slate-700 hover:bg-slate-50 border-slate-200"
              >
                <Plus className="mr-2 h-4 w-4" /> Add Component
              </Button>
            </div>

            <div className="border-t border-slate-200 pt-4 mt-6">
              <h3 className="text-sm font-semibold text-slate-800 mb-3">Exam Weight</h3>
              <div className="max-w-xs">
                <Label className="text-xs font-medium text-slate-500">Final Exam Weight (%)</Label>
                <Input
                  type="number"
                  min="0"
                  max="100"
                  placeholder="60"
                  value={localExamWeight || ''}
                  onChange={(e) => setLocalExamWeight(Number(e.target.value) || 0)}
                  className="mt-1 h-9 bg-white"
                />
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-200">
              {validationError ? (
                <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 flex items-start gap-2">
                  <span className="mt-0.5">⚠️</span>
                  <span>{validationError}</span>
                </div>
              ) : (
                <div className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 flex items-start gap-2">
                  <span className="mt-0.5">✅</span>
                  <span>Weights configuration is valid. Total: <strong>100%</strong> (CA Sum: {caSum}%, Exam: {localExamWeight}%)</span>
                </div>
              )}
            </div>
          </div>
        </section>

        <section className="rounded-md border border-slate-200 bg-white p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">Grading Rules</p>
              <h2 className="mt-1 text-xl font-semibold text-slate-950">Setup Grade Boundaries</h2>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={handleResetToWaec}
                className="text-slate-700 hover:bg-slate-50 border-slate-200"
              >
                Reset to WAEC Defaults
              </Button>
              <Button
                onClick={handleSaveGradingRulesClick}
                disabled={isSaving || Boolean(gradingRulesError)}
                className="bg-slate-950 text-white hover:bg-slate-800"
              >
                {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <CheckCircle2 className="mr-2 h-4 w-4" />}
                Save Rules
              </Button>
            </div>
          </div>
          <p className="mt-2 text-sm text-slate-500">
            Configure score ranges, grade labels, and remarks for student result computations. Ensure there are no overlapping score ranges and the pass mark is set.
          </p>

          <div className="mt-6 space-y-4">
            <div className="overflow-x-auto rounded-md border border-slate-200">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-4 py-2.5">Grade</th>
                    <th className="px-4 py-2.5">Min Score</th>
                    <th className="px-4 py-2.5">Max Score</th>
                    <th className="px-4 py-2.5">Remark / Feedback</th>
                    <th className="px-4 py-2.5 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {localGrades.map((g, index) => (
                    <tr key={index} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/50">
                      <td className="px-4 py-2">
                        <Input
                          placeholder="e.g. A1"
                          value={g.grade}
                          onChange={(e) => handleUpdateGradeRow(index, 'grade', e.target.value)}
                          className="h-8 w-20 bg-white text-center font-bold"
                        />
                      </td>
                      <td className="px-4 py-2">
                        <Input
                          type="number"
                          min="0"
                          max="100"
                          placeholder="0"
                          value={g.minScore}
                          onChange={(e) => handleUpdateGradeRow(index, 'minScore', e.target.value)}
                          className="h-8 w-24 bg-white"
                        />
                      </td>
                      <td className="px-4 py-2">
                        <Input
                          type="number"
                          min="0"
                          max="100"
                          placeholder="100"
                          value={g.maxScore}
                          onChange={(e) => handleUpdateGradeRow(index, 'maxScore', e.target.value)}
                          className="h-8 w-24 bg-white"
                        />
                      </td>
                      <td className="px-4 py-2">
                        <Input
                          placeholder="e.g. Excellent"
                          value={g.remark}
                          onChange={(e) => handleUpdateGradeRow(index, 'remark', e.target.value)}
                          className="h-8 bg-white"
                        />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleRemoveGradeRow(index)}
                          className="h-8 w-8 text-slate-500 hover:text-red-600 hover:bg-red-50"
                        >
                          <XCircle className="h-4 w-4" />
                        </Button>
                      </td>
                    </tr>
                  ))}
                  {localGrades.length === 0 && (
                    <tr>
                      <td colSpan={5} className="px-4 py-6 text-center text-sm text-slate-500 italic bg-slate-50/50">
                        No grade boundaries configured. Click "+ Add Grade Row" or "Reset to WAEC Defaults" to start.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleAddGradeRow}
              className="mt-2 text-slate-700 hover:bg-slate-50 border-slate-200"
            >
              <Plus className="mr-2 h-4 w-4" /> Add Grade Row
            </Button>

            <div className="border-t border-slate-200 pt-4 mt-6">
              <h3 className="text-sm font-semibold text-slate-800 mb-3">Pass Mark Setting</h3>
              <div className="max-w-xs">
                <Label className="text-xs font-medium text-slate-500">Passing Threshold Score</Label>
                <Input
                  type="number"
                  min="0"
                  max="100"
                  placeholder="40"
                  value={localPassMark}
                  onChange={(e) => setLocalPassMark(Number(e.target.value) || 0)}
                  className="mt-1 h-9 bg-white"
                />
                <p className="mt-1 text-xs text-slate-400 font-normal">
                  Scores below this threshold are flagged as failing grades (F9).
                </p>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-slate-200">
              {gradingRulesError ? (
                <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 flex items-start gap-2">
                  <span className="mt-0.5">⚠️</span>
                  <span>{gradingRulesError}</span>
                </div>
              ) : (
                <div className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 flex items-start gap-2">
                  <span className="mt-0.5">✅</span>
                  <span>Grading rules configuration is valid and ready to save.</span>
                </div>
              )}
            </div>
          </div>
        </section>

        <section className="rounded-md border border-slate-200 bg-white p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">Exam Sessions</p>
              <h2 className="mt-1 text-xl font-semibold text-slate-950">Active Exam Setup</h2>
            </div>
          </div>
          <p className="mt-2 text-sm text-slate-500">
            View active exam sessions configuration for the current academic term. Exam sessions are proactively created and managed dynamically to let teachers record final exam scores.
          </p>

          <div className="mt-6 space-y-4">
            <div className="overflow-x-auto rounded-md border border-slate-200">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-5 py-3">Exam Session Name</th>
                    <th className="px-5 py-3">Maximum Score</th>
                    <th className="px-5 py-3">Type</th>
                    <th className="px-5 py-3">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {termExams.map((exam) => (
                    <tr key={exam.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/50">
                      <td className="px-5 py-4 font-medium text-slate-900">{exam.name}</td>
                      <td className="px-5 py-4 text-slate-600">{exam.maxScore} marks</td>
                      <td className="px-5 py-4">
                        <Badge variant="outline" className="border-slate-200 text-slate-600 bg-slate-50">
                          End of Term Exam
                        </Badge>
                      </td>
                      <td className="px-5 py-4">
                        <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-700 bg-emerald-50 px-2.5 py-0.5 rounded-full">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                          Active
                        </span>
                      </td>
                    </tr>
                  ))}
                  {termExams.length === 0 && (
                    <tr>
                      <td colSpan={4} className="px-5 py-6 text-center text-sm text-slate-500 italic bg-slate-50/20">
                        No active exam sessions found. They will be auto-created on first access by teachers or grade entry.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <div className="rounded-md border border-blue-100 bg-blue-50/50 p-4 text-xs text-blue-800">
              <p className="font-semibold mb-1">💡 Proactive Creation Information</p>
              <p className="leading-relaxed font-normal">
                The school result system is designed to automatically generate default "End of Term Exam" sessions for active classes. As an admin, you can see and review the active exam sessions here.
              </p>
            </div>
          </div>
        </section>
      </div>

      <aside className="space-y-6">
        <div className="rounded-md border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">Setup Checklist</p>
          <div className="mt-3 flex items-end justify-between">
            <h2 className="text-2xl font-semibold text-slate-950">{setupProgress}%</h2>
            <span className="text-sm text-slate-500">{setupItems.filter((item) => item.done).length}/{setupItems.length}</span>
          </div>
          <Progress value={setupProgress} className="mt-4 h-2 bg-slate-100" />
          <div className="mt-4 space-y-2">
            {setupItems.map((item) => (
              <div key={item.label} className="flex items-center justify-between text-sm">
                <span className="text-slate-600">{item.label}</span>
                {item.done ? <CheckCircle2 className="h-4 w-4 text-emerald-600" /> : <XCircle className="h-4 w-4 text-slate-300" />}
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-md border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">Academic Calendar</p>
          <h3 className="mt-1 text-base font-semibold text-slate-950">{currentSession?.name ?? 'No current session'}</h3>
          <p className="mt-1 text-sm text-slate-500">{currentTerm?.name ?? 'No current term'}</p>
          <div className="mt-4 space-y-2">
            {sessions.map((session) => (
              <div key={session.sessionId} className="rounded-md border border-slate-200 p-3">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-medium text-slate-800">{session.name}</p>
                  {session.isCurrent && <Badge className="bg-blue-100 text-blue-700 hover:bg-blue-100">Current</Badge>}
                </div>
                <p className="mt-1 text-xs text-slate-500">{formatDate(session.startDate)} - {formatDate(session.endDate)}</p>
              </div>
            ))}
          </div>
        </div>
      </aside>
    </div>
  );
}

function ClassesSection({
  classes,
  onCreate,
  onPromote,
}: {
  classes: ClassRoom[];
  onCreate: () => void;
  onPromote: () => void;
}) {
  return (
    <section className="rounded-md border border-slate-200 bg-white">
      <div className="flex flex-col gap-4 border-b border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h3 className="font-semibold text-slate-900">Classes</h3>
          <p className="text-xs text-slate-500">{classes.length} active classes</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={onPromote}>
            Promote Students
          </Button>
          <Button size="sm" onClick={onCreate} className="gap-2">
            <Plus className="h-4 w-4" />
            New class
          </Button>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-5 py-3">Class</th>
              <th className="px-5 py-3">Grade</th>
              <th className="px-5 py-3">Session</th>
              <th className="px-5 py-3">Enrollment</th>
              <th className="px-5 py-3">Teacher</th>
            </tr>
          </thead>
          <tbody>
            {classes.map((item) => (
              <tr key={item.classId} className="border-b border-slate-100">
                <td className="px-5 py-4 font-medium text-slate-900">{item.name}</td>
                <td className="px-5 py-4 text-slate-600">{formatGradeCode(item.gradeLevel)}</td>
                <td className="px-5 py-4 text-slate-600">{item.sessionName ?? 'Current'}</td>
                <td className="px-5 py-4 text-slate-600">{item.currentEnrollment}/{item.capacity}</td>
                <td className="px-5 py-4 text-slate-600">{item.classTeacher?.name ?? 'Unassigned'}</td>
              </tr>
            ))}
            {!classes.length && <EmptyTableRow colSpan={5} message="No classes created yet." />}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function PeopleSection({ users, onCreate }: { users: UserSummary[]; onCreate: () => void }) {
  return (
    <section className="rounded-md border border-slate-200 bg-white">
      <SectionHeader title="People" description={`${users.length} active user records`} actionLabel="Add staff" onAction={onCreate} icon={UserPlus} />
      <div className="grid gap-3 p-5 md:grid-cols-2 xl:grid-cols-3">
        {users.map((item) => (
          <div key={item.userId} className="rounded-md border border-slate-200 p-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-medium text-slate-900">{[item.firstName, item.lastName].filter(Boolean).join(' ') || item.email}</p>
                <p className="mt-1 text-sm text-slate-500">{item.email}</p>
              </div>
              <Badge variant="outline" className="border-slate-200 text-slate-600">{item.userType.replace('_', ' ')}</Badge>
            </div>
            <p className="mt-3 text-sm text-slate-500">{item.phoneNumber ?? 'No phone number'}</p>
          </div>
        ))}
        {!users.length && <EmptyPanel message="No staff accounts returned." />}
      </div>
    </section>
  );
}

function StudentsSection({
  students,
  allClasses,
  search,
  classFilter,
  onSearch,
  onClassFilter,
  onCreate,
  onStudentClick,
}: {
  students: StudentSummary[];
  allClasses: ClassRoom[];
  search: string;
  classFilter: string;
  onSearch: (value: string) => void;
  onClassFilter: (value: string) => void;
  onCreate: () => void;
  onStudentClick: (studentId: string) => void;
}) {
  return (
    <section className="rounded-md border border-slate-200 bg-white">
      <div className="border-b border-slate-200 p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-950">Students</h2>
            <p className="text-sm text-slate-500">{students.length} visible records</p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <Input className="w-full pl-9 sm:w-72" value={search} onChange={(event) => onSearch(event.target.value)} placeholder="Search students" />
            </div>
            <Select value={classFilter} onValueChange={onClassFilter}>
              <SelectTrigger className="w-full sm:w-52">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All classes</SelectItem>
                {allClasses.map((item) => <SelectItem key={item.classId} value={item.classId}>{item.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <Button onClick={onCreate} className="bg-slate-950 text-white hover:bg-slate-800">
              <Plus className="mr-2 h-4 w-4" />
              Enroll
            </Button>
          </div>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-5 py-3">Student</th>
              <th className="px-5 py-3">Admission</th>
              <th className="px-5 py-3">Class</th>
              <th className="px-5 py-3">Guardian</th>
              <th className="px-5 py-3">Status</th>
            </tr>
          </thead>
          <tbody>
            {students.map((item) => (
              <tr key={item.studentId} className="border-b border-slate-100 cursor-pointer hover:bg-slate-50 transition-colors" onClick={() => onStudentClick(item.studentId)}>
                <td className="px-5 py-4 font-medium text-slate-900">{[item.firstName, item.middleName, item.lastName].filter(Boolean).join(' ')}</td>
                <td className="px-5 py-4 text-slate-600">{item.admissionNumber}</td>
                <td className="px-5 py-4 text-slate-600">{item.currentClass?.name ?? 'Unassigned'}</td>
                <td className="px-5 py-4 text-slate-600">{item.parentName ?? item.parentPhone ?? 'Not linked'}</td>
                <td className="px-5 py-4"><Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">{item.status ?? 'ACTIVE'}</Badge></td>
              </tr>
            ))}
            {!students.length && <EmptyTableRow colSpan={5} message="No students match the current filters." />}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function FeesSection({
  feeDashboard,
  feeStructures,
  classes,
  onCreate,
  onAssign,
  isSaving,
  payments,
  onDownloadReceipt,
  onSendBulkReminders,
  onDownloadCollectionReport,
}: {
  feeDashboard: FeeDashboard | null;
  feeStructures: FeeStructure[];
  classes: ClassRoom[];
  onCreate: () => void;
  onAssign: (structure: FeeStructure) => void;
  isSaving: boolean;
  payments: any[];
  onDownloadReceipt: (receiptNumber: string) => void;
  onSendBulkReminders: () => void;
  onDownloadCollectionReport: () => void;
}) {
  const [activeTab, setActiveTab] = useState<'structures' | 'history'>('structures');

  return (
    <div className="space-y-6">
      <section className="grid gap-4 md:grid-cols-3">
        <Metric icon={CircleDollarSign} label="Expected" value={formatCurrency(Number(feeDashboard?.summary?.totalExpected ?? 0))} detail={feeDashboard?.termName ?? 'Current term'} />
        <div className="relative">
          <Metric icon={Banknote} label="Collected" value={formatCurrency(Number(feeDashboard?.summary?.totalCollected ?? 0))} detail={`${Number(feeDashboard?.summary?.collectionRate ?? 0).toFixed(1)}% collection rate`} />
          <Button
            size="sm"
            variant="outline"
            className="absolute bottom-3 right-3 text-[10px] py-1 px-2 border-slate-200 text-slate-600 bg-white hover:bg-slate-50"
            onClick={onDownloadCollectionReport}
          >
            Export PDF
          </Button>
        </div>
        <div className="relative">
          <Metric icon={BellRing} label="Overdue" value={formatNumber(Number(feeDashboard?.upcomingDeadlines?.overdue?.count ?? 0))} detail={formatCurrency(Number(feeDashboard?.upcomingDeadlines?.overdue?.amount ?? 0))} />
          {Number(feeDashboard?.upcomingDeadlines?.overdue?.count ?? 0) > 0 && (
            <Button
              size="sm"
              variant="outline"
              className="absolute bottom-3 right-3 text-[10px] py-1 px-2 bg-red-50 text-red-700 border-red-200 hover:bg-red-100"
              onClick={onSendBulkReminders}
              disabled={isSaving}
            >
              Send Reminders
            </Button>
          )}
        </div>
      </section>

      <div className="flex border-b border-slate-200">
        <button
          type="button"
          className={`px-4 py-2 text-sm font-semibold border-b-2 transition ${activeTab === 'structures' ? 'border-slate-900 text-slate-900' : 'border-transparent text-slate-500 hover:text-slate-900'}`}
          onClick={() => setActiveTab('structures')}
        >
          Fee Structures
        </button>
        <button
          type="button"
          className={`px-4 py-2 text-sm font-semibold border-b-2 transition ${activeTab === 'history' ? 'border-slate-900 text-slate-900' : 'border-transparent text-slate-500 hover:text-slate-900'}`}
          onClick={() => setActiveTab('history')}
        >
          Payment History
        </button>
      </div>

      {activeTab === 'structures' && (
        <section className="rounded-md border border-slate-200 bg-white">
          <SectionHeader title="Fee Structures" description={`${classes.length} classes available for assignment`} actionLabel="New fee structure" onAction={onCreate} icon={Plus} />
          <div className="grid gap-3 p-5">
            {feeStructures.map((item) => (
              <div key={item.structureId} className="flex flex-col gap-3 rounded-md border border-slate-200 p-4 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="font-medium text-slate-900">{item.name}</p>
                  <p className="mt-1 text-sm text-slate-500">{item.termName ?? 'Term'} · {formatCurrency(Number(item.totalAmount ?? 0))} · due {formatDate(item.dueDate)}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant="outline" className="border-slate-200 text-slate-600">{item.status ?? 'ACTIVE'}</Badge>
                  <Button variant="outline" disabled={isSaving} onClick={() => onAssign(item)}>Assign</Button>
                </div>
              </div>
            ))}
            {!feeStructures.length && <EmptyPanel message="No active fee structures yet." />}
          </div>
        </section>
      )}

      {activeTab === 'history' && (
        <section className="rounded-md border border-slate-200 bg-white overflow-hidden">
          <div className="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <h3 className="font-semibold text-slate-900">Payment Transactions</h3>
            <p className="text-xs text-slate-500">List of offline and online school fee payments.</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3">Date</th>
                  <th className="px-5 py-3">Student</th>
                  <th className="px-5 py-3">Amount</th>
                  <th className="px-5 py-3">Method</th>
                  <th className="px-5 py-3">Receipt / Ref</th>
                  <th className="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((p) => (
                  <tr key={p.paymentId} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/50">
                    <td className="px-5 py-4 text-slate-600">{p.paidAt || p.createdAt ? formatDate(p.paidAt || p.createdAt) : '—'}</td>
                    <td className="px-5 py-4 font-medium text-slate-900">{p.studentName || '—'}</td>
                    <td className="px-5 py-4 text-slate-900 font-semibold">{formatCurrency(p.amount)}</td>
                    <td className="px-5 py-4 text-slate-600">
                      <Badge variant="secondary" className="bg-slate-100 text-slate-700 capitalize">
                        {String(p.paymentMethod || 'Paystack').toLowerCase().replace('_', ' ')}
                      </Badge>
                    </td>
                    <td className="px-5 py-4 text-slate-600 font-mono text-xs">{p.receiptNumber || p.reference || '—'}</td>
                    <td className="px-5 py-4 text-right">
                      {p.receiptNumber ? (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onDownloadReceipt(p.receiptNumber)}
                        >
                          Download Receipt
                        </Button>
                      ) : (
                        <span className="text-xs text-slate-400">Online Payment</span>
                      )}
                    </td>
                  </tr>
                ))}
                {!payments.length && <EmptyTableRow colSpan={6} message="No payment transactions recorded yet." />}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

function NotificationsSection({
  balance,
  templates,
  onEditTemplate,
}: {
  balance: NotificationBalance | null;
  templates: NotificationTemplate[];
  onEditTemplate: (template: NotificationTemplate) => void;
}) {
  return (
    <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
      <section className="rounded-md border border-slate-200 bg-white p-5">
        <p className="text-sm font-medium text-slate-500">SMS Balance</p>
        <h2 className="mt-2 text-3xl font-semibold text-slate-950">{formatNumber(Number(balance?.balance ?? 0))}</h2>
        <p className="mt-1 text-sm text-slate-500">{balance?.provider ?? 'Provider'} · {balance?.currency ?? 'NGN'}</p>
      </section>
      <section className="rounded-md border border-slate-200 bg-white">
        <SectionHeader title="Templates" description={`${templates.length} notification templates`} />
        <div className="grid gap-3 p-5 md:grid-cols-2">
          {templates.map((item) => (
            <div key={item.templateId} className="rounded-md border border-slate-200 p-4 relative group flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between gap-3">
                  <p className="font-medium text-slate-900">{item.name}</p>
                  <div className="flex items-center gap-1.5">
                    <Badge variant={item.isActive ? 'default' : 'secondary'} className={item.isActive ? 'bg-emerald-50 text-emerald-700 border-emerald-100 hover:bg-emerald-50' : 'bg-slate-50 text-slate-500 border-slate-100'}>
                      {item.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                    <Badge variant="outline" className="border-slate-200 text-slate-600">{item.channel}</Badge>
                  </div>
                </div>
                <p className="mt-2 line-clamp-3 text-sm text-slate-500">{item.body}</p>
              </div>
              <div className="mt-4 flex justify-end">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => onEditTemplate(item)}
                >
                  Edit Template
                </Button>
              </div>
            </div>
          ))}
          {!templates.length && <EmptyPanel message="No templates returned." />}
        </div>
      </section>
    </div>
  );
}

function ResultsSection({
  classes,
  terms,
  selectedClassId,
  selectedTermId,
  classResults,
  isLoading,
  onClassChange,
  onTermChange,
  onAddPrincipalComment,
  onRecomputeRankings,
  onPublish,
  onUnpublish,
  onGenerateReportCards,
  reportCardJob,
  isSaving,
}: {
  classes: ClassRoom[];
  terms: any[];
  selectedClassId: string;
  selectedTermId: string;
  classResults: any;
  isLoading: boolean;
  onClassChange: (classId: string) => void;
  onTermChange: (termId: string) => void;
  onAddPrincipalComment: (studentId: string, studentName: string) => void;
  onRecomputeRankings: () => void;
  onPublish: () => void;
  onUnpublish: () => void;
  onGenerateReportCards: (studentIds?: string[]) => void;
  reportCardJob: { id: string; status: string; progress?: number } | null;
  isSaving: boolean;
}) {
  return (
    <div className="space-y-6">
      <section className="rounded-md border border-slate-200 bg-white p-5 flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex flex-wrap items-center gap-4">
          <div className="w-[180px]">
            <Label className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1 block">Class</Label>
            <Select value={selectedClassId} onValueChange={onClassChange}>
              <SelectTrigger className="w-full bg-white">
                <SelectValue placeholder="Select class..." />
              </SelectTrigger>
              <SelectContent className="bg-white">
                {classes.map((c) => (
                  <SelectItem key={c.classId} value={c.classId}>
                    {c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="w-[180px]">
            <Label className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1 block">Term</Label>
            <Select value={selectedTermId} onValueChange={onTermChange}>
              <SelectTrigger className="w-full bg-white">
                <SelectValue placeholder="Select term..." />
              </SelectTrigger>
              <SelectContent className="bg-white">
                {terms.map((t) => (
                  <SelectItem key={t.termId} value={t.termId}>
                    {t.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        {selectedClassId && selectedTermId && (
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={onRecomputeRankings}
              disabled={isSaving || isLoading}
              className="gap-2"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${isSaving ? 'animate-spin' : ''}`} />
              Recompute Rankings
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={onPublish}
              disabled={isSaving || isLoading}
              className="border-emerald-200 text-emerald-700 bg-emerald-50 hover:bg-emerald-100 hover:text-emerald-800"
            >
              Publish Results
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={onUnpublish}
              disabled={isSaving || isLoading}
              className="border-amber-200 text-amber-700 bg-amber-50 hover:bg-amber-100 hover:text-amber-800"
            >
              Unpublish Results
            </Button>
            <Button
              size="sm"
              onClick={() => onGenerateReportCards()}
              disabled={isSaving || isLoading || !classResults?.students?.length}
              className="gap-2"
            >
              <FileText className="h-4 w-4" />
              Generate Report Cards
            </Button>
          </div>
        )}
      </section>

      {reportCardJob && (
        <section className="rounded-md border border-slate-200 bg-slate-50 p-4 relative overflow-hidden">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin text-blue-600" />
              <span className="text-xs font-semibold text-slate-700">Asynchronous Report Cards Job: {reportCardJob.status}</span>
            </div>
            {reportCardJob.progress !== undefined && (
              <span className="text-xs font-bold text-slate-900">{reportCardJob.progress}%</span>
            )}
          </div>
          {reportCardJob.progress !== undefined && (
            <Progress value={reportCardJob.progress} className="h-2 bg-slate-200" />
          )}
        </section>
      )}

      <section className="rounded-md border border-slate-200 bg-white overflow-hidden">
        <div className="border-b border-slate-200 bg-slate-50 px-5 py-4">
          <h3 className="font-semibold text-slate-900">Class Result Sheet</h3>
          <p className="text-xs text-slate-500">Summary performance of all students in the class.</p>
        </div>

        {isLoading ? (
          <div className="flex flex-col items-center justify-center p-16 gap-3">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
            <p className="text-sm text-slate-500">Loading result sheet...</p>
          </div>
        ) : classResults && classResults.students ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm border-collapse">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3 border-r border-slate-200 min-w-[200px] sticky left-0 bg-slate-50 z-10">Student</th>
                  <th className="px-4 py-3 border-r border-slate-200">Adm No.</th>
                  <th className="px-4 py-3 border-r border-slate-200">Pos</th>
                  <th className="px-4 py-3 border-r border-slate-200">Avg</th>
                  {/*<th className="px-4 py-3 border-r border-slate-200">Grade</th>*/}
                  {classResults.subjects.map((subName: string) => (
                    <th key={subName} className="px-4 py-3 border-r border-slate-200 text-center min-w-[120px]">{subName}</th>
                  ))}
                  <th className="px-5 py-3 text-right sticky right-0 bg-slate-50 z-10 shadow-[-4px_0_6px_-2px_rgba(0,0,0,0.05)]">Actions</th>
                </tr>
              </thead>
              <tbody>
                {classResults.students.map((student: any) => (
                  <tr key={student.studentId} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/50">
                    <td className="px-5 py-4 font-medium text-slate-900 border-r border-slate-100 sticky left-0 bg-white group-hover:bg-slate-50/50 z-10">{student.name}</td>
                    <td className="px-4 py-4 text-slate-600 border-r border-slate-100">{student.admissionNumber}</td>
                    <td className="px-4 py-4 font-bold text-slate-900 border-r border-slate-100 border-collapse">
                      {student.position > 0 ? `#${student.position}` : '—'}
                    </td>
                    <td className="px-4 py-4 text-slate-700 border-r border-slate-100">
                      {student.average > 0 ? student.average.toFixed(1) : '—'}
                    </td>
                    {/*<td className="px-4 py-4 border-r border-slate-100">*/}
                    {/*  {student.overallGrade ? (*/}
                    {/*    <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-100 font-semibold">{student.overallGrade}</Badge>*/}
                    {/*  ) : '—'}*/}
                    {/*</td>*/}
                    {classResults.subjects.map((subName: string) => {
                      const score = student.subjects.find((s: any) => s.subject === subName);
                      return (
                        <td key={subName} className="px-4 py-4 border-r border-slate-100 text-center text-xs">
                          {score ? (
                            <div className="inline-block">
                              <span className="font-semibold text-slate-900">{score.finalScore.toFixed(0)}</span>
                              <span className="text-slate-400 ml-1">({score.grade})</span>
                            </div>
                          ) : (
                            <span className="text-slate-300">—</span>
                          )}
                        </td>
                      );
                    })}
                    <td className="px-5 py-4 text-right sticky right-0 bg-white group-hover:bg-slate-50/50 z-10 shadow-[-4px_0_6px_-2px_rgba(0,0,0,0.05)] space-x-1.5 flex justify-end border-collapse">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onAddPrincipalComment(student.studentId, student.name)}
                      >
                        Comment
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onGenerateReportCards([student.studentId])}
                        disabled={isSaving}
                      >
                        PDF
                      </Button>
                    </td>
                  </tr>
                ))}
                {!classResults.students.length && (
                  <EmptyTableRow colSpan={6 + classResults.subjects.length} message="No student records found on the result sheet." />
                )}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyPanel message="No result sheet records found. Select class and term to load results." />
        )}
      </section>
    </div>
  );
}

function CommentDialog({
  open,
  form,
  isSaving,
  onOpenChange,
  onChange,
  onSubmit,
}: {
  open: boolean;
  form: { id: string; name: string; termId: string; comment: string } | null;
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: any) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
}) {
  if (!form) return null;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Add Principal Comment</DialogTitle>
          <DialogDescription>
            Add or edit the official principal comment for {form.name} on their report card.
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="space-y-1">
            <Label className="text-sm font-medium text-slate-900">Principal Comment</Label>
            <Textarea
              className="min-h-[100px]"
              value={form.comment}
              onChange={(e) => onChange({ ...form, comment: e.target.value })}
              required
              placeholder="An excellent performance this term. Keep up the good work!"
            />
          </div>
          <DialogActions disabled={isSaving} submitLabel="Save Comment" />
        </form>
      </DialogContent>
    </Dialog>
  );
}

function OfflinePaymentDialog({
  open,
  form,
  isSaving,
  onOpenChange,
  onChange,
  onSubmit,
}: {
  open: boolean;
  form: { studentId: string; studentFeeId: string; balance: number; amount: string; paymentMethod: string; receivedBy: string; feeName: string };
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: any) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Record Offline Payment</DialogTitle>
          <DialogDescription>
            Enter details for manual payment received from student for {form.feeName}.
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="bg-slate-50 p-3 rounded-lg border border-slate-100 mb-2">
            <p className="text-xs text-slate-500 font-medium">Pending Balance</p>
            <p className="text-lg font-bold text-slate-900">₦{form.balance.toLocaleString()}</p>
          </div>
          <Field
            label="Amount Paid (₦)"
            value={form.amount}
            onChange={(value) => onChange({ ...form, amount: value })}
            type="number"
            min="1"
            max={form.balance.toString()}
            required
          />
          <SelectField
            label="Payment Method"
            value={form.paymentMethod}
            onChange={(value) => onChange({ ...form, paymentMethod: value })}
            options={[
              { value: 'CASH', label: 'Cash' },
              { value: 'BANK_TRANSFER', label: 'Bank Transfer' },
              { value: 'POS', label: 'POS' },
              { value: 'CHEQUE', label: 'Cheque' }
            ]}
          />
          <Field
            label="Received By"
            value={form.receivedBy}
            onChange={(value) => onChange({ ...form, receivedBy: value })}
            required
          />
          <DialogActions disabled={isSaving} submitLabel="Record Payment" />
        </form>
      </DialogContent>
    </Dialog>
  );
}

function PromoteStudentsDialog({
  open,
  form,
  isSaving,
  onOpenChange,
  onChange,
  onSubmit,
  classes,
  sessions,
  students,
}: {
  open: boolean;
  form: { fromClassId: string; toClassId: string; targetSessionId: string; selectedStudentIds: string[] };
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: any) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  classes: ClassRoom[];
  sessions: AcademicSession[];
  students: StudentSummary[];
}) {
  const classStudents = useMemo(() => {
    if (!form.fromClassId) return [];
    return students.filter(s => s.currentClass?.classId === form.fromClassId);
  }, [form.fromClassId, students]);

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      onChange({ ...form, selectedStudentIds: classStudents.map(s => s.studentId) });
    } else {
      onChange({ ...form, selectedStudentIds: [] });
    }
  };

  const handleSelectStudent = (studentId: string, checked: boolean) => {
    const nextList = checked
      ? [...form.selectedStudentIds, studentId]
      : form.selectedStudentIds.filter(id => id !== studentId);
    onChange({ ...form, selectedStudentIds: nextList });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Promote Students</DialogTitle>
          <DialogDescription>
            Select students from a source class and promote them to a destination class for a new academic session.
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <SelectField
              label="Source Class"
              value={form.fromClassId}
              onChange={(value) => onChange({ ...form, fromClassId: value, selectedStudentIds: [] })}
              options={[
                { value: '', label: 'Select class...' },
                ...classes.map(c => ({ value: c.classId, label: c.name }))
              ]}
            />
            <SelectField
              label="Target Class"
              value={form.toClassId}
              onChange={(value) => onChange({ ...form, toClassId: value })}
              options={[
                { value: '', label: 'Select class...' },
                ...classes.map(c => ({ value: c.classId, label: c.name }))
              ]}
            />
            <SelectField
              label="Target Academic Session"
              value={form.targetSessionId}
              onChange={(value) => onChange({ ...form, targetSessionId: value })}
              options={[
                { value: '', label: 'Select session...' },
                ...sessions.map(s => ({ value: s.sessionId, label: s.name }))
              ]}
            />
          </div>

          {form.fromClassId && (
            <div className="border border-slate-200 rounded-lg overflow-hidden">
              <div className="bg-slate-50 px-4 py-2 border-b border-slate-200 flex items-center justify-between">
                <span className="text-xs font-semibold text-slate-700 uppercase">Students ({classStudents.length})</span>
                {classStudents.length > 0 && (
                  <label className="flex items-center gap-2 text-xs font-medium text-slate-600 cursor-pointer">
                    <input
                      type="checkbox"
                      className="rounded border-slate-300 text-slate-900 focus:ring-slate-950"
                      checked={form.selectedStudentIds.length === classStudents.length}
                      onChange={(e) => handleSelectAll(e.target.checked)}
                    />
                    Select All
                  </label>
                )}
              </div>
              <div className="max-h-[300px] overflow-y-auto p-4 space-y-2">
                {classStudents.map(s => (
                  <label key={s.studentId} className="flex items-center gap-3 p-2 rounded hover:bg-slate-50 cursor-pointer text-sm">
                    <input
                      type="checkbox"
                      className="rounded border-slate-300 text-slate-900 focus:ring-slate-950"
                      checked={form.selectedStudentIds.includes(s.studentId)}
                      onChange={(e) => handleSelectStudent(s.studentId, e.target.checked)}
                    />
                    <div className="flex-1">
                      <p className="font-medium text-slate-900">{s.firstName} {s.lastName}</p>
                      <p className="text-xs text-slate-500">{s.admissionNumber}</p>
                    </div>
                  </label>
                ))}
                {classStudents.length === 0 && (
                  <p className="text-center py-6 text-sm text-slate-500">No active students found in this class.</p>
                )}
              </div>
            </div>
          )}

          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Selected: {form.selectedStudentIds.length} students</span>
          </div>

          <DialogActions
            disabled={isSaving || !form.fromClassId || !form.toClassId || !form.targetSessionId || form.selectedStudentIds.length === 0}
            submitLabel="Promote Students"
          />
        </form>
      </DialogContent>
    </Dialog>
  );
}

function EditTemplateDialog({
  open,
  form,
  isSaving,
  onOpenChange,
  onChange,
  onSubmit,
}: {
  open: boolean;
  form: { templateId: string; name: string; body: string; isActive: boolean } | null;
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: any) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
}) {
  if (!form) return null;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Edit Notification Template</DialogTitle>
          <DialogDescription>
            Update the message content and active status of the template.
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit}>
          <Field
            label="Template Name"
            value={form.name}
            onChange={(value) => onChange({ ...form, name: value })}
            required
          />
          <div className="space-y-1">
            <Label className="text-sm font-medium text-slate-900">Message Body</Label>
            <Textarea
              className="min-h-[120px]"
              value={form.body}
              onChange={(e) => onChange({ ...form, body: e.target.value })}
              required
            />
            <p className="text-[11px] text-slate-500">Use placeholder variables (e.g. {"{studentName}"}, {"{amount}"}) dynamically injected by the system.</p>
          </div>
          <label className="flex items-center gap-3 p-1 cursor-pointer">
            <input
              type="checkbox"
              className="rounded border-slate-300 text-slate-900 focus:ring-slate-950"
              checked={form.isActive}
              onChange={(e) => onChange({ ...form, isActive: e.target.checked })}
            />
            <span className="text-sm font-medium text-slate-900">Template Active</span>
          </label>
          <DialogActions disabled={isSaving} submitLabel="Save Template" />
        </form>
      </DialogContent>
    </Dialog>
  );
}

function ClassDialog({
  open,
  form,
  gradeLevels,
  sessions,
  isSaving,
  onOpenChange,
  onChange,
  onSubmit,
}: {
  open: boolean;
  form: ClassForm;
  gradeLevels: GradeLevel[];
  sessions: AcademicSession[];
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: ClassForm) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Create class</DialogTitle>
          <DialogDescription>Class details for the current academic session.</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit}>
          <Field label="Class name" value={form.name} onChange={(value) => onChange({ ...form, name: value })} required />
          <div className="grid gap-4 md:grid-cols-2">
            <SelectField label="Grade level" value={form.gradeLevel} onChange={(value) => onChange({ ...form, gradeLevel: value })} options={gradeLevels.map((level) => ({ value: level.code, label: level.name }))} />
            <Field label="Section" value={form.section} onChange={(value) => onChange({ ...form, section: value })} />
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <SelectField label="Session" value={form.academicSessionId} onChange={(value) => onChange({ ...form, academicSessionId: value })} options={sessions.map((session) => ({ value: session.sessionId, label: session.name }))} />
            <Field label="Capacity" type="number" min="1" value={form.capacity} onChange={(value) => onChange({ ...form, capacity: value })} required />
          </div>
          <DialogActions disabled={isSaving} submitLabel="Create class" />
        </form>
      </DialogContent>
    </Dialog>
  );
}

function StaffDialog({
  open,
  form,
  isSaving,
  onOpenChange,
  onChange,
  onSubmit,
}: {
  open: boolean;
  form: StaffForm;
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: StaffForm) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="bg-white sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Add staff</DialogTitle>
          <DialogDescription>Staff accounts receive their access from the identity provider.</DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="First name" value={form.firstName} onChange={(value) => onChange({ ...form, firstName: value })} required />
            <Field label="Last name" value={form.lastName} onChange={(value) => onChange({ ...form, lastName: value })} required />
          </div>
          <Field label="Email" type="email" value={form.email} onChange={(value) => onChange({ ...form, email: value })} required />
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="Phone" value={form.phoneNumber} onChange={(value) => onChange({ ...form, phoneNumber: value })} required />
            <SelectField
              label="Role"
              value={form.userType}
              onChange={(value) => onChange({ ...form, userType: value as StaffRole })}
              options={[
                { value: 'TEACHER', label: 'Teacher' },
                { value: 'ACCOUNTANT', label: 'Accountant' },
                { value: 'SCHOOL_ADMIN', label: 'School Admin' },
              ]}
            />
          </div>
          <DialogActions disabled={isSaving} submitLabel="Create staff" />
        </form>
      </DialogContent>
    </Dialog>
  );
}

function StudentDialog({
  open,
  form,
  classes,
  isSaving,
  onOpenChange,
  onChange,
  onSubmit,
}: {
  open: boolean;
  form: StudentForm;
  classes: ClassRoom[];
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: StudentForm) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Enroll student</DialogTitle>
          <DialogDescription>Student profile and primary guardian contact.</DialogDescription>
        </DialogHeader>
        <form className="space-y-5" onSubmit={onSubmit}>
          <div className="grid gap-4 md:grid-cols-3">
            <Field label="First name" value={form.firstName} onChange={(value) => onChange({ ...form, firstName: value })} required />
            <Field label="Middle name" value={form.middleName} onChange={(value) => onChange({ ...form, middleName: value })} />
            <Field label="Last name" value={form.lastName} onChange={(value) => onChange({ ...form, lastName: value })} required />
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            <SelectField label="Gender" value={form.gender} onChange={(value) => onChange({ ...form, gender: value as 'MALE' | 'FEMALE' })} options={[{ value: 'MALE', label: 'Male' }, { value: 'FEMALE', label: 'Female' }]} />
            <Field label="Date of birth" type="date" value={form.dateOfBirth} onChange={(value) => onChange({ ...form, dateOfBirth: value })} />
            <SelectField label="Class" value={form.classId} onChange={(value) => onChange({ ...form, classId: value })} options={classes.map((item) => ({ value: item.classId, label: item.name }))} />
          </div>
          <div className="rounded-md border border-slate-200 p-4">
            <h3 className="text-sm font-semibold text-slate-900">Primary Guardian</h3>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <Field label="First name" value={form.guardianFirstName} onChange={(value) => onChange({ ...form, guardianFirstName: value })} required />
              <Field label="Last name" value={form.guardianLastName} onChange={(value) => onChange({ ...form, guardianLastName: value })} required />
              <Field label="Phone" value={form.guardianPhone} onChange={(value) => onChange({ ...form, guardianPhone: value })} required />
              <Field label="Email" type="email" value={form.guardianEmail} onChange={(value) => onChange({ ...form, guardianEmail: value })} />
              <Field label="Relationship" value={form.guardianRelationship} onChange={(value) => onChange({ ...form, guardianRelationship: value })} required />
            </div>
          </div>
          <div>
            <Label htmlFor="medicalNotes">Medical notes</Label>
            <Textarea id="medicalNotes" value={form.medicalNotes} onChange={(event) => onChange({ ...form, medicalNotes: event.target.value })} className="mt-2" />
          </div>
          <DialogActions disabled={isSaving} submitLabel="Enroll student" />
        </form>
      </DialogContent>
    </Dialog>
  );
}

function FeeDialog({
  open,
  form,
  sessions,
  classes,
  isSaving,
  onOpenChange,
  onChange,
  onToggleClass,
  onUpdateItem,
  onAddItem,
  onRemoveItem,
  onSubmit,
}: {
  open: boolean;
  form: FeeForm;
  sessions: AcademicSession[];
  classes: ClassRoom[];
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: FeeForm) => void;
  onToggleClass: (classId: string, checked: boolean) => void;
  onUpdateItem: (index: number, patch: Partial<FeeItemForm>) => void;
  onAddItem: () => void;
  onRemoveItem: (index: number) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
}) {
  const sessionOptions = sessions.map((session) => ({ value: session.sessionId, label: session.name }));
  const termOptions = sessions
    .find((session) => session.sessionId === form.sessionId)
    ?.terms.map((term) => ({ value: term.termId, label: term.name })) ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Create fee structure</DialogTitle>
          <DialogDescription>Term fees, applicable classes, and due-date rules.</DialogDescription>
        </DialogHeader>
        <form className="space-y-5" onSubmit={onSubmit}>
          <Field label="Name" value={form.name} onChange={(value) => onChange({ ...form, name: value })} required />
          <div className="grid gap-4 md:grid-cols-3">
            <SelectField label="Session" value={form.sessionId} onChange={(value) => onChange({ ...form, sessionId: value, termId: '' })} options={sessionOptions} />
            <SelectField label="Term" value={form.termId} onChange={(value) => onChange({ ...form, termId: value })} options={termOptions} />
            <Field label="Due date" type="date" value={form.dueDate} onChange={(value) => onChange({ ...form, dueDate: value })} required />
          </div>
          <div className="rounded-md border border-slate-200 p-4">
            <h3 className="text-sm font-semibold text-slate-900">Classes</h3>
            <div className="mt-3 grid gap-2 md:grid-cols-2">
              {classes.map((item) => (
                <label key={item.classId} className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-1.5 hover:bg-slate-50">
                  <Checkbox checked={form.classIds.includes(item.classId)} onCheckedChange={(checked) => onToggleClass(item.classId, checked === true)} />
                  <span className="text-sm text-slate-700">{item.name}</span>
                </label>
              ))}
            </div>
          </div>
          <div className="rounded-md border border-slate-200 p-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-900">Fee Items</h3>
              <Button type="button" variant="outline" size="sm" onClick={onAddItem}>
                <Plus className="mr-2 h-4 w-4" />
                Item
              </Button>
            </div>
            <div className="mt-3 space-y-3">
              {form.items.map((item, index) => (
                <div key={index} className="grid gap-3 md:grid-cols-[1fr_160px_36px]">
                  <Input value={item.description} onChange={(event) => onUpdateItem(index, { description: event.target.value })} placeholder="Description" />
                  <Input type="number" min="1" value={item.amount} onChange={(event) => onUpdateItem(index, { amount: event.target.value })} placeholder="Amount" />
                  <Button type="button" variant="outline" size="icon" onClick={() => onRemoveItem(index)} disabled={form.items.length === 1}>
                    <XCircle className="h-4 w-4" />
                  </Button>
                </div>
              ))}
            </div>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="Late after days" type="number" min="0" value={form.applyAfterDays} onChange={(value) => onChange({ ...form, applyAfterDays: value })} />
            <Field label="Late fee %" type="number" min="0" value={form.percentageAmount} onChange={(value) => onChange({ ...form, percentageAmount: value })} />
          </div>
          <DialogActions disabled={isSaving} submitLabel="Create fee structure" />
        </form>
      </DialogContent>
    </Dialog>
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

function SectionHeader({ title, description, actionLabel, onAction, icon: Icon }: { title: string; description: string; actionLabel?: string; onAction?: () => void; icon?: React.ComponentType<{ className?: string }> }) {
  return (
    <div className="flex flex-col gap-3 border-b border-slate-200 p-5 md:flex-row md:items-center md:justify-between">
      <div>
        <h2 className="text-lg font-semibold text-slate-950">{title}</h2>
        <p className="text-sm text-slate-500">{description}</p>
      </div>
      {actionLabel && onAction && (
        <Button onClick={onAction} className="bg-slate-950 text-white hover:bg-slate-800">
          {Icon && <Icon className="mr-2 h-4 w-4" />}
          {actionLabel}
        </Button>
      )}
    </div>
  );
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

function SelectField({ label, value, onChange, options }: { label: string; value: string; onChange: (value: string) => void; options: Array<{ value: string; label: string }> }) {
  return (
    <div>
      <Label>{label}</Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="mt-2 w-full">
          <SelectValue placeholder={label} />
        </SelectTrigger>
        <SelectContent>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

function DialogActions({ disabled, submitLabel }: { disabled: boolean; submitLabel: string }) {
  return (
    <div className="flex justify-end border-t border-slate-200 pt-4">
      <Button type="submit" disabled={disabled} className="bg-slate-950 text-white hover:bg-slate-800">
        {disabled && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
        {submitLabel}
      </Button>
    </div>
  );
}

function DeadlineRow({ label, value, danger = false }: { label: string; value: number; danger?: boolean }) {
  return (
    <div className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2">
      <span className="text-sm text-slate-600">{label}</span>
      <span className={`text-sm font-semibold ${danger ? 'text-red-600' : 'text-slate-900'}`}>{formatNumber(value)}</span>
    </div>
  );
}

function EmptyPanel({ message }: { message: string }) {
  return <p className="rounded-md bg-slate-50 p-4 text-sm text-slate-500">{message}</p>;
}

function EmptyTableRow({ colSpan, message }: { colSpan: number; message: string }) {
  return (
    <tr>
      <td colSpan={colSpan} className="px-5 py-10 text-center text-sm text-slate-500">{message}</td>
    </tr>
  );
}

function groupGradeLevels(levels: GradeLevel[]) {
  return levels
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .reduce<Record<string, GradeLevel[]>>((groups, level) => {
      const key = level.category || 'OTHER';
      groups[key] = [...(groups[key] ?? []), level];
      return groups;
    }, {});
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('en-NG', {
    style: 'currency',
    currency: 'NGN',
    maximumFractionDigits: 0,
  }).format(value || 0);
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('en-NG').format(value || 0);
}

function formatDate(value?: string) {
  if (!value) return 'Not set';
  return new Intl.DateTimeFormat('en-NG', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value));
}

function formatCategory(value: string) {
  return value.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatGradeCode(value: string) {
  return value.replace(/_/g, ' ');
}

function trimOptional(value: string) {
  const trimmed = value.trim();
  return trimmed.length ? trimmed : undefined;
}

function readError(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { errors?: Array<{ message?: string }>; message?: string } } }).response;
    return response?.data?.errors?.[0]?.message || response?.data?.message || fallback;
  }
  return fallback;
}

function daysAgo(days: number) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date;
}

function toDateInputValue(date: Date) {
  return date.toISOString().slice(0, 10);
}

interface SubjectsSectionProps {
  subjects: SubjectResponse[];
  classes: ClassRoom[];
  classSubjects: ClassSubjectResponse[];
  selectedClassForSubjects: string;
  onClassChange: (classId: string) => void;
  onCreateSubject: () => void;
  onEditSubject: (subject: SubjectResponse) => void;
  onDeactivateSubject: (subjectId: string) => void;
  onAssignSubject: () => void;
  onRemoveSubject: (subjectId: string) => void;
}

function SubjectsSection({
  subjects,
  classes,
  classSubjects,
  selectedClassForSubjects,
  onClassChange,
  onCreateSubject,
  onEditSubject,
  onDeactivateSubject,
  onAssignSubject,
  onRemoveSubject,
}: SubjectsSectionProps) {
  const [activeTab, setActiveTab] = useState<'all' | 'classes'>('all');

  return (
    <section className="rounded-md border border-slate-200 bg-white">
      <div className="flex items-center justify-between border-b border-slate-200 px-5 pt-4">
        <div className="flex gap-4">
          <button
            onClick={() => setActiveTab('all')}
            className={`border-b-2 pb-3 text-sm font-medium transition-all ${
              activeTab === 'all'
                ? 'border-slate-950 text-slate-950 font-semibold'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            All Subjects
          </button>
          <button
            onClick={() => setActiveTab('classes')}
            className={`border-b-2 pb-3 text-sm font-medium transition-all ${
              activeTab === 'classes'
                ? 'border-slate-950 text-slate-950 font-semibold'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            Class Assignments
          </button>
        </div>
        {activeTab === 'all' ? (
          <Button onClick={onCreateSubject} className="mb-2 bg-slate-950 text-white hover:bg-slate-800">
            <Plus className="mr-2 h-4 w-4" />
            New Subject
          </Button>
        ) : (
          selectedClassForSubjects && selectedClassForSubjects !== 'NONE' && (
            <Button onClick={onAssignSubject} className="mb-2 bg-slate-950 text-white hover:bg-slate-800">
              <Plus className="mr-2 h-4 w-4" />
              Assign Subject
            </Button>
          )
        )}
      </div>

      {activeTab === 'all' && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-5 py-3">Subject Name</th>
                <th className="px-5 py-3">Code</th>
                <th className="px-5 py-3">Category</th>
                <th className="px-5 py-3">Status</th>
                <th className="px-5 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {subjects.map((s) => (
                <tr key={s.subjectId} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/50">
                  <td className="px-5 py-4 font-medium text-slate-900">{s.name}</td>
                  <td className="px-5 py-4 text-slate-600">{s.code || '—'}</td>
                  <td className="px-5 py-4 text-slate-600">{s.category ? formatCategory(s.category) : '—'}</td>
                  <td className="px-5 py-4">
                    <Badge className={s.isActive ? 'bg-green-100 text-green-700 hover:bg-green-100' : 'bg-slate-100 text-slate-700 hover:bg-slate-100'}>
                      {s.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                  </td>
                  <td className="px-5 py-4 text-right">
                    <div className="flex justify-end gap-2">
                      <Button variant="outline" size="sm" onClick={() => onEditSubject(s)}>
                        Edit
                      </Button>
                      {s.isActive && (
                        <Button variant="ghost" size="sm" className="text-red-600 hover:bg-red-50 hover:text-red-700" onClick={() => onDeactivateSubject(s.subjectId)}>
                          Deactivate
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {!subjects.length && <EmptyTableRow colSpan={5} message="No subjects created yet." />}
            </tbody>
          </table>
        </div>
      )}

      {activeTab === 'classes' && (
        <div className="space-y-4 p-5">
          <div className="w-full max-w-xs">
            <SelectField
              label="Select Class"
              value={selectedClassForSubjects}
              onChange={onClassChange}
              options={[
                { value: 'NONE', label: 'Select a class...' },
                ...classes.map((cls) => ({ value: cls.classId, label: cls.name }))
              ]}
            />
          </div>

          {selectedClassForSubjects && selectedClassForSubjects !== 'NONE' ? (
            <div className="overflow-x-auto rounded-md border border-slate-200">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-5 py-3">Subject Name</th>
                    <th className="px-5 py-3">Code</th>
                    <th className="px-5 py-3">Assigned Teacher</th>
                    <th className="px-5 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {classSubjects.map((cs) => (
                    <tr key={cs.classSubjectId} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/50">
                      <td className="px-5 py-4 font-medium text-slate-900">{cs.subjectName}</td>
                      <td className="px-5 py-4 text-slate-600">{cs.subjectCode || '—'}</td>
                      <td className="px-5 py-4 text-slate-600">{cs.teacherName || 'No Teacher Assigned'}</td>
                      <td className="px-5 py-4 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-red-600 hover:bg-red-50 hover:text-red-700"
                          onClick={() => onRemoveSubject(cs.subjectId)}
                        >
                          Remove
                        </Button>
                      </td>
                    </tr>
                  ))}
                  {!classSubjects.length && <EmptyTableRow colSpan={4} message="No subjects assigned to this class yet." />}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="text-sm text-slate-500">Please select a class to view its assigned subjects.</p>
          )}
        </div>
      )}
    </section>
  );
}

function SubjectDialog({
  open,
  form,
  isSaving,
  onOpenChange,
  onChange,
  onSubmit,
  isEdit,
}: {
  open: boolean;
  form: SubjectForm;
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: SubjectForm) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  isEdit: boolean;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Edit subject' : 'Create subject'}</DialogTitle>
          <DialogDescription>
            {isEdit ? 'Update the details of the subject.' : 'Provide the details to create a new subject.'}
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit}>
          <Field
            label="Subject name"
            value={form.name}
            onChange={(value) => onChange({ ...form, name: value })}
            required
          />
          <Field
            label="Subject code"
            value={form.code}
            onChange={(value) => onChange({ ...form, code: value })}
          />
          <Field
            label="Category"
            value={form.category}
            onChange={(value) => onChange({ ...form, category: value })}
            placeholder="e.g. SCIENCES, ARTS, COMMERCIAL"
          />
          <DialogActions disabled={isSaving} submitLabel={isEdit ? 'Save Changes' : 'Create subject'} />
        </form>
      </DialogContent>
    </Dialog>
  );
}

function AssignSubjectDialog({
  open,
  form,
  isSaving,
  onOpenChange,
  onChange,
  onSubmit,
  subjects,
  teachers,
}: {
  open: boolean;
  form: { subjectId: string; teacherId: string };
  isSaving: boolean;
  onOpenChange: (open: boolean) => void;
  onChange: (form: { subjectId: string; teacherId: string }) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  subjects: SubjectResponse[];
  teachers: UserSummary[];
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Assign subject to class</DialogTitle>
          <DialogDescription>
            Map a subject to this class and optionally assign a subject teacher.
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit}>
          <SelectField
            label="Subject"
            value={form.subjectId}
            onChange={(value) => onChange({ ...form, subjectId: value })}
            options={subjects.map((s) => ({ value: s.subjectId, label: `${s.name} (${s.code || 'N/A'})` }))}
          />
          <SelectField
            label="Teacher (Optional)"
            value={form.teacherId}
            onChange={(value) => onChange({ ...form, teacherId: value })}
            options={[
              { value: 'NONE', label: 'No Teacher Assigned' },
              ...teachers.map((t) => ({ value: t.userId, label: `${t.firstName} ${t.lastName}` }))
            ]}
          />
          <DialogActions disabled={isSaving} submitLabel="Assign subject" />
        </form>
      </DialogContent>
    </Dialog>
  );
}

export default AdminDashboard;
