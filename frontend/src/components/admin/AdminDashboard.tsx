import React, { useEffect, useMemo, useState } from 'react';
import {
  Banknote,
  BellRing,
  BookOpen,
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
} from 'lucide-react';
import { useAuth } from '@/components/auth/AuthProvider';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
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
  type ClassRoom,
  type CreateFeeStructurePayload,
  type DailySummary,
  type FeeDashboard,
  type FeeStructure,
  type GradeLevel,
  type NotificationBalance,
  type NotificationTemplate,
  type SchoolProfile,
  type StudentSummary,
  type UserSummary,
  schoolAdminService,
} from '@/services/schoolAdminService';
import { StudentDetailsView } from '@/components/admin/students/StudentDetailsView';

type Section = 'overview' | 'setup' | 'classes' | 'people' | 'students' | 'fees' | 'notifications';
type StaffRole = 'TEACHER' | 'ACCOUNTANT' | 'SCHOOL_ADMIN';

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
  { id: 'fees', label: 'Fees', icon: Banknote },
  { id: 'notifications', label: 'Notifications', icon: BellRing },
];

export const AdminDashboard: React.FC = () => {
  const { user, logout, isSuperAdmin } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const { id: routeSchoolId } = useParams();
  const { clearSchool } = useSchoolStore();

  const isImpersonating = useMemo(() => {
    return !!(isSuperAdmin && location.pathname.startsWith('/super-admin/schools/'));
  }, [isSuperAdmin, location.pathname]);

  const currentSectionFromUrl = useMemo(() => {
    if (!isImpersonating) return null;
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
      let suffix = 'dashboard';
      if (sectionId === 'people') suffix = 'users';
      else if (sectionId === 'fees') suffix = 'fees';
      else if (sectionId === 'overview') suffix = 'dashboard';
      navigate(`/super-admin/schools/${routeSchoolId}/${suffix}`);
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

  const currentSession = useMemo(
    () => sessions.find((session) => session.isCurrent) ?? sessions[0],
    [sessions],
  );
  const currentTerm = useMemo(
    () => currentSession?.terms?.find((term) => term.isCurrent) ?? currentSession?.terms?.[0],
    [currentSession],
  );

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
      { label: 'Fee structures', done: feeStructures.length > 0, target: 'fees' as Section },
      { label: 'Assigned fees', done: Number(feeSummary?.totalExpected ?? 0) > 0, target: 'fees' as Section },
    ],
    [activeStudentCount, classes.length, enabledLevels.length, feeStructures.length, feeSummary?.totalExpected, school, sessions.length, staffCount],
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
    ]);

    if (schoolResult.status === 'fulfilled') setSchool(schoolResult.value);
    if (availableLevelsResult.status === 'fulfilled') setAvailableLevels(availableLevelsResult.value);
    if (enabledLevelsResult.status === 'fulfilled') {
      setEnabledLevels(enabledLevelsResult.value);
      setSelectedLevelCodes(enabledLevelsResult.value.map((level) => level.code));
    }
    if (sessionsResult.status === 'fulfilled') setSessions(sessionsResult.value);
    if (classesResult.status === 'fulfilled') setClasses(classesResult.value);
    if (usersResult.status === 'fulfilled') setUsers(usersResult.value.content ?? []);
    if (studentsResult.status === 'fulfilled') setStudents(studentsResult.value.content ?? []);
    if (feeDashboardResult.status === 'fulfilled') setFeeDashboard(feeDashboardResult.value);
    if (feeStructuresResult.status === 'fulfilled') setFeeStructures(feeStructuresResult.value);
    if (dailySummaryResult.status === 'fulfilled') setDailySummary(dailySummaryResult.value);
    if (balanceResult.status === 'fulfilled') setNotificationBalance(balanceResult.value);
    if (templatesResult.status === 'fulfilled') setTemplates(templatesResult.value);

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
    ].some((result) => result.status === 'rejected');

    if (failed) {
      setNotice('Some panels could not refresh. Available school data is shown.');
    }
    setIsLoading(false);
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
              <span>Impersonating: <strong className="text-slate-950">{school?.name || 'Loading school...'}</strong></span>
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
                  {isImpersonating ? 'SUPER ADMIN (IMPERSONATING)' : 'SCHOOL ADMIN'}
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
              <Button onClick={() => setActiveSection('setup')} className="bg-slate-950 text-white hover:bg-slate-800">
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
                />
              )}

              {activeSection === 'classes' && (
                <ClassesSection
                  classes={classes}
                  onCreate={() => setCreateDialog('class')}
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

              {activeSection === 'fees' && (
                <FeesSection
                  feeDashboard={feeDashboard}
                  feeStructures={feeStructures}
                  classes={classes}
                  onCreate={() => setCreateDialog('fee')}
                  onAssign={(structure) => void assignFeeStructure(structure)}
                  isSaving={isSaving}
                />
              )}

              {activeSection === 'notifications' && (
                <NotificationsSection
                  balance={notificationBalance}
                  templates={templates}
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
}) {
  const grouped = groupGradeLevels(availableLevels);
  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
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

function ClassesSection({ classes, onCreate }: { classes: ClassRoom[]; onCreate: () => void }) {
  return (
    <section className="rounded-md border border-slate-200 bg-white">
      <SectionHeader title="Classes" description={`${classes.length} active classes`} actionLabel="New class" onAction={onCreate} icon={Plus} />
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-y border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
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
}: {
  feeDashboard: FeeDashboard | null;
  feeStructures: FeeStructure[];
  classes: ClassRoom[];
  onCreate: () => void;
  onAssign: (structure: FeeStructure) => void;
  isSaving: boolean;
}) {
  return (
    <div className="space-y-6">
      <section className="grid gap-4 md:grid-cols-3">
        <Metric icon={CircleDollarSign} label="Expected" value={formatCurrency(Number(feeDashboard?.summary?.totalExpected ?? 0))} detail={feeDashboard?.termName ?? 'Current term'} />
        <Metric icon={Banknote} label="Collected" value={formatCurrency(Number(feeDashboard?.summary?.totalCollected ?? 0))} detail={`${Number(feeDashboard?.summary?.collectionRate ?? 0).toFixed(1)}% collection rate`} />
        <Metric icon={BellRing} label="Overdue" value={formatNumber(Number(feeDashboard?.upcomingDeadlines?.overdue?.count ?? 0))} detail={formatCurrency(Number(feeDashboard?.upcomingDeadlines?.overdue?.amount ?? 0))} />
      </section>

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
    </div>
  );
}

function NotificationsSection({ balance, templates }: { balance: NotificationBalance | null; templates: NotificationTemplate[] }) {
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
            <div key={item.templateId} className="rounded-md border border-slate-200 p-4">
              <div className="flex items-center justify-between gap-3">
                <p className="font-medium text-slate-900">{item.name}</p>
                <Badge variant="outline" className="border-slate-200 text-slate-600">{item.channel}</Badge>
              </div>
              <p className="mt-2 line-clamp-3 text-sm text-slate-500">{item.body}</p>
            </div>
          ))}
          {!templates.length && <EmptyPanel message="No templates returned." />}
        </div>
      </section>
    </div>
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

export default AdminDashboard;
