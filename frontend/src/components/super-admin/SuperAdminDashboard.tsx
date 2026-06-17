import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  ArrowRight,
  Banknote,
  BellRing,
  Building2,
  CalendarDays,
  CheckCircle2,
  CircleDollarSign,
  Landmark,
  Loader2,
  LogOut,
  Mail,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Users,
  XCircle,
} from 'lucide-react';
import { useAuth } from '@/components/auth/AuthProvider';
import { Routes, Route, useNavigate, useParams, useLocation, Navigate } from 'react-router-dom';
import AdminDashboard from '@/components/admin/AdminDashboard';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Progress } from '@/components/ui/progress';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useSchoolStore } from '@/store/schoolStore';
import {
  type AcademicSession,
  type CreateSchoolPayload,
  type DailySummary,
  type FeeDashboard,
  type NotificationBalance,
  type NotificationTemplate,
  type ReminderSchedule,
  type SchoolDetail,
  type SchoolSummary,
  type UserSummary,
  superAdminService,
} from '@/services/superAdminService';

type StatusFilter = 'ACTIVE' | 'INACTIVE';

interface CreateSchoolForm {
  name: string;
  code: string;
  email: string;
  phone: string;
  address: string;
  city: string;
  state: string;
  country: string;
  adminEmail: string;
  adminFirstName: string;
  adminLastName: string;
  adminPhoneNumber: string;
  paystackPublicKey: string;
  paystackSubaccountCode: string;
  smsProvider: string;
  smsApiKey: string;
  smsUsername: string;
  smsSenderId: string;
  academicYearStart: string;
}

const emptyForm: CreateSchoolForm = {
  name: '',
  code: '',
  email: '',
  phone: '',
  address: '',
  city: '',
  state: '',
  country: 'Nigeria',
  adminEmail: '',
  adminFirstName: '',
  adminLastName: '',
  adminPhoneNumber: '',
  paystackPublicKey: '',
  paystackSubaccountCode: '',
  smsProvider: 'AFRICAS_TALKING',
  smsApiKey: '',
  smsUsername: '',
  smsSenderId: '',
  academicYearStart: '09-08',
};

const navItems = [
  { id: 'overview', label: 'Overview', icon: Landmark },
  { id: 'schools', label: 'Schools', icon: Building2 },
  { id: 'finance', label: 'Finance', icon: CircleDollarSign },
  { id: 'notifications', label: 'Notifications', icon: BellRing },
] as const;

export const SuperAdminDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { id: routeSchoolId } = useParams();
  const { selectedSchoolId, selectedSchoolName, selectSchool, clearSchool } = useSchoolStore();

  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ACTIVE');
  const [search, setSearch] = useState('');
  const [schools, setSchools] = useState<SchoolSummary[]>([]);
  const [isLoadingSchools, setIsLoadingSchools] = useState(true);
  const [createForm, setCreateForm] = useState<CreateSchoolForm>(emptyForm);
  const [isCreating, setIsCreating] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Sync selected school state with route when impersonating
  const isImpersonatingPath = useMemo(() => {
    const p = location.pathname;
    return p.includes('/dashboard') || p.includes('/users') || p.includes('/fees') || p.includes('/reports');
  }, [location.pathname]);

  useEffect(() => {
    if (isImpersonatingPath && routeSchoolId && routeSchoolId !== selectedSchoolId) {
      const found = schools.find((s) => s.schoolId === routeSchoolId);
      selectSchool(routeSchoolId, found?.name || 'School');
    }
  }, [isImpersonatingPath, routeSchoolId, selectedSchoolId, schools, selectSchool]);

  const platformTotals = useMemo(() => {
    return schools.reduce(
      (totals, school) => ({
        schools: totals.schools + 1,
        students: totals.students + (school.studentCount ?? 0),
        users: totals.users + (school.activeUsers ?? 0),
        collectionRateTotal: totals.collectionRateTotal + (school.collectionRate ?? 0),
      }),
      { schools: 0, students: 0, users: 0, collectionRateTotal: 0 },
    );
  }, [schools]);

  const averageCollectionRate = platformTotals.schools
    ? platformTotals.collectionRateTotal / platformTotals.schools
    : 0;

  const filteredSchools = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();
    if (!normalizedSearch) return schools;
    return schools.filter((school) => {
      return [school.name, school.code, school.city, school.state]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedSearch));
    });
  }, [schools, search]);

  useEffect(() => {
    void loadSchools(statusFilter);
  }, [statusFilter]);

  const loadSchools = async (status: StatusFilter) => {
    setIsLoadingSchools(true);
    setError(null);
    try {
      const page = await superAdminService.listSchools(status, 0, 100);
      setSchools(page.content ?? []);
    } catch (err) {
      setError(readError(err, 'Unable to load schools'));
    } finally {
      setIsLoadingSchools(false);
    }
  };

  const handleSelectSchool = (school: SchoolSummary) => {
    selectSchool(school.schoolId, school.name);
    setNotice(`${school.name} is now selected`);
    navigate(`/super-admin/schools/${school.schoolId}/dashboard`);
  };

  const handleCreateSchoolSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsCreating(true);
    setError(null);
    setNotice(null);

    try {
      const created = await superAdminService.createSchool(buildCreatePayload(createForm));
      selectSchool(created.schoolId, created.name);
      setCreateForm(emptyForm);
      setStatusFilter('ACTIVE');
      setNotice(created.message || `${created.name} has been created`);
      await loadSchools('ACTIVE');
      navigate(`/super-admin/schools/${created.schoolId}/dashboard`);
    } catch (err) {
      setError(readError(err, 'Unable to create school'));
    } finally {
      setIsCreating(false);
    }
  };

  const handleDeactivate = async (school: SchoolSummary) => {
    if (!window.confirm(`Deactivate ${school.name}?`)) return;
    setError(null);
    try {
      await superAdminService.deactivateSchool(school.schoolId);
      if (selectedSchoolId === school.schoolId) clearSchool();
      await loadSchools(statusFilter);
      setNotice(`${school.name} has been deactivated`);
    } catch (err) {
      setError(readError(err, 'Unable to deactivate school'));
    }
  };

  // Determine active section for sidebar highlights
  const activeSection = useMemo(() => {
    const path = location.pathname;
    if (path.includes('/dashboard')) return 'overview';
    if (path.includes('/schools')) return 'schools';
    return 'overview';
  }, [location.pathname]);

  const handleSidebarClick = (itemId: 'overview' | 'schools' | 'finance' | 'notifications') => {
    if (itemId === 'overview') {
      navigate('/super-admin/dashboard');
    } else if (itemId === 'schools') {
      navigate('/super-admin/schools');
    } else if (itemId === 'finance') {
      if (selectedSchoolId) {
        navigate(`/super-admin/schools/${selectedSchoolId}/fees`);
      } else {
        navigate('/super-admin');
        setNotice('Please select a school to view its finance dashboard.');
      }
    } else if (itemId === 'notifications') {
      if (selectedSchoolId) {
        navigate(`/super-admin/schools/${selectedSchoolId}/dashboard`);
      } else {
        navigate('/super-admin');
        setNotice('Please select a school to view its notifications.');
      }
    }
  };

  // If the path is /super-admin/schools/:id/dashboard or /users or /fees, render AdminDashboard directly
  if (isImpersonatingPath && routeSchoolId) {
    return <AdminDashboard />;
  }

  return (
    <div className="min-h-screen bg-slate-100 text-slate-950">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-72 border-r border-slate-200 bg-white lg:flex lg:flex-col">
        <div className="px-6 py-6">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-md bg-slate-950 text-white">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-slate-950">School Fee Platform</p>
              <p className="text-xs text-slate-500">Super Admin</p>
            </div>
          </div>
        </div>

        <nav className="flex-1 space-y-1 px-4">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => handleSidebarClick(item.id)}
                className={`flex h-10 w-full items-center gap-3 rounded-md px-3 text-sm font-medium transition ${
                  activeSection === item.id || (item.id === 'overview' && location.pathname === '/super-admin/dashboard')
                    ? 'bg-slate-950 text-white'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'
                }`}
              >
                <Icon className="h-4 w-4" />
                {item.label}
              </button>
            );
          })}
        </nav>

        <div className="border-t border-slate-200 p-4">
          {selectedSchoolName && (
            <button
              type="button"
              onClick={() => {
                clearSchool();
                navigate('/super-admin');
              }}
              className="mb-3 flex w-full items-center justify-between rounded-md border border-slate-200 px-3 py-3 text-left text-sm hover:bg-slate-50"
            >
              <span>
                <span className="block font-medium text-slate-900">{selectedSchoolName}</span>
                <span className="text-xs text-slate-500">Switch school</span>
              </span>
              <ArrowRight className="h-4 w-4 text-slate-400" />
            </button>
          )}
          <Button variant="outline" className="w-full justify-start text-red-600 hover:text-red-700" onClick={logout}>
            <LogOut className="h-4 w-4" />
            Sign out
          </Button>
        </div>
      </aside>

      <main className="lg:pl-72">
        <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/95 px-4 py-4 backdrop-blur md:px-8">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="outline" className="border-slate-300 text-slate-600">
                  {user?.userType?.replace('_', ' ')}
                </Badge>
                {selectedSchoolName && (
                  <Badge className="bg-emerald-600 text-white hover:bg-emerald-650">{selectedSchoolName}</Badge>
                )}
              </div>
              <h1 className="mt-2 text-2xl font-semibold tracking-tight text-slate-950 md:text-3xl">
                Platform Command Center
              </h1>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="outline" onClick={() => void loadSchools(statusFilter)} disabled={isLoadingSchools}>
                <RefreshCw className={`h-4 w-4 ${isLoadingSchools ? 'animate-spin' : ''}`} />
                Refresh
              </Button>
              <Button onClick={() => navigate('/super-admin/schools/create')}>
                <Plus className="h-4 w-4" />
                New school
              </Button>
            </div>
          </div>
        </header>

        <div className="space-y-6 p-4 md:p-8">
          {notice && (
            <div className="flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
              <CheckCircle2 className="h-4 w-4" />
              {notice}
            </div>
          )}
          {error && (
            <div className="flex items-center gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
              <AlertCircle className="h-4 w-4" />
              {error}
            </div>
          )}

          <Routes>
            <Route
              path="/"
              element={
                <SchoolSelectorPage
                  schools={schools}
                  onSelect={handleSelectSchool}
                  onCreateNew={() => navigate('/super-admin/schools/create')}
                  isLoading={isLoadingSchools}
                  error={error}
                />
              }
            />
            <Route
              path="/dashboard"
              element={
                <>
                  <PlatformStats
                    schoolCount={platformTotals.schools}
                    studentCount={platformTotals.students}
                    activeUsers={platformTotals.users}
                    collectionRate={averageCollectionRate}
                    totalCollected={0}
                  />

                  <div className="rounded-md border border-slate-200 bg-white">
                    <div className="flex flex-col gap-4 border-b border-slate-200 p-4 md:flex-row md:items-center md:justify-between">
                      <div>
                        <h2 className="text-lg font-semibold text-slate-950">Schools</h2>
                        <p className="text-sm text-slate-500">{filteredSchools.length} visible</p>
                      </div>
                      <div className="flex flex-col gap-2 sm:flex-row">
                        <div className="relative">
                          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                          <Input
                            value={search}
                            onChange={(event) => setSearch(event.target.value)}
                            placeholder="Search"
                            className="w-full pl-9 sm:w-64"
                          />
                        </div>
                        <div className="inline-flex rounded-md border border-slate-200 bg-slate-50 p-1">
                          {(['ACTIVE', 'INACTIVE'] as StatusFilter[]).map((status) => (
                            <button
                              key={status}
                              type="button"
                              onClick={() => setStatusFilter(status)}
                              className={`rounded px-3 py-1.5 text-sm font-medium ${
                                statusFilter === status ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-500'
                              }`}
                            >
                              {status}
                            </button>
                          ))}
                        </div>
                      </div>
                    </div>
                    <SchoolTable
                      schools={filteredSchools}
                      selectedSchoolId={selectedSchoolId}
                      onSelect={(school) => navigate(`/super-admin/schools/${school.schoolId}`)}
                      onDeactivate={handleDeactivate}
                    />
                  </div>
                </>
              }
            />
            <Route
              path="/schools"
              element={
                <div className="rounded-md border border-slate-200 bg-white">
                  <div className="flex flex-col gap-4 border-b border-slate-200 p-4 md:flex-row md:items-center md:justify-between">
                    <div>
                      <h2 className="text-lg font-semibold text-slate-950">All Platform Schools</h2>
                      <p className="text-sm text-slate-500">{filteredSchools.length} registered schools</p>
                    </div>
                    <div className="flex flex-col gap-2 sm:flex-row">
                      <div className="relative">
                        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <Input
                          value={search}
                          onChange={(event) => setSearch(event.target.value)}
                          placeholder="Search"
                          className="w-full pl-9 sm:w-64"
                        />
                      </div>
                      <div className="inline-flex rounded-md border border-slate-200 bg-slate-50 p-1">
                        {(['ACTIVE', 'INACTIVE'] as StatusFilter[]).map((status) => (
                          <button
                            key={status}
                            type="button"
                            onClick={() => setStatusFilter(status)}
                            className={`rounded px-3 py-1.5 text-sm font-medium ${
                              statusFilter === status ? 'bg-white text-slate-950 shadow-sm' : 'text-slate-500'
                            }`}
                          >
                            {status}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>
                  <SchoolTable
                    schools={filteredSchools}
                    selectedSchoolId={selectedSchoolId}
                    onSelect={(school) => navigate(`/super-admin/schools/${school.schoolId}`)}
                    onDeactivate={handleDeactivate}
                  />
                </div>
              }
            />
            <Route
              path="/schools/create"
              element={
                <CreateSchoolPage
                  form={createForm}
                  onChange={setCreateForm}
                  onSubmit={handleCreateSchoolSubmit}
                  onCancel={() => navigate('/super-admin')}
                  isCreating={isCreating}
                />
              }
            />
            <Route
              path="/schools/:id"
              element={
                <SchoolDetailsRouteWrapper
                  schools={schools}
                  onDeactivate={handleDeactivate}
                  onBack={() => navigate('/super-admin/schools')}
                />
              }
            />
            <Route path="*" element={<Navigate to="/super-admin" replace />} />
          </Routes>
        </div>
      </main>
    </div>
  );
};

function SchoolDetailsRouteWrapper({
  schools,
  onDeactivate,
  onBack
}: {
  schools: SchoolSummary[];
  onDeactivate: (school: SchoolSummary) => void;
  onBack: () => void;
}) {
  const { id } = useParams();
  return (
    <SchoolDetailsPage
      schoolId={id || ''}
      schools={schools}
      onDeactivate={onDeactivate}
      onBack={onBack}
    />
  );
}

interface SchoolSelectorPageProps {
  schools: SchoolSummary[];
  onSelect: (school: SchoolSummary) => void;
  onCreateNew: () => void;
  isLoading: boolean;
  error: string | null;
}

function SchoolSelectorPage({
  schools,
  onSelect,
  onCreateNew,
  isLoading,
  error
}: SchoolSelectorPageProps) {
  const navigate = useNavigate();
  const { selectSchool } = useSchoolStore();

  useEffect(() => {
    if (!isLoading && schools.length === 1 && schools[0].status === 'ACTIVE') {
      const singleSchool = schools[0];
      selectSchool(singleSchool.schoolId, singleSchool.name);
      navigate(`/super-admin/schools/${singleSchool.schoolId}/dashboard`, { replace: true });
    }
  }, [isLoading, schools, navigate, selectSchool]);

  if (isLoading) {
    return <LoadingPanel label="Loading registered schools..." />;
  }

  if (schools.length === 0) {
    return <EmptyPlatform onCreate={onCreateNew} />;
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-950">Select a School to Manage</h2>
        <p className="text-sm text-slate-500">Choose a school to inspect its setup, users, and fee collection dashboard.</p>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          <AlertCircle className="h-4 w-4" />
          {error}
        </div>
      )}

      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {schools.map((school) => (
          <button
            key={school.schoolId}
            type="button"
            onClick={() => onSelect(school)}
            className="group flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:border-slate-400 hover:shadow-md"
          >
            <div>
              <div className="flex items-start justify-between gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-955 text-white font-bold text-sm">
                  {school.name.slice(0, 2).toUpperCase()}
                </div>
                <Badge className={school.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-50' : 'bg-slate-100 text-slate-600 hover:bg-slate-100'}>
                  {school.status}
                </Badge>
              </div>
              <h3 className="mt-4 font-semibold text-slate-950 group-hover:text-slate-900 transition">{school.name}</h3>
              <p className="text-xs text-slate-500 mt-1">{school.code} {school.city ? `· ${school.city}` : ''}</p>
            </div>

            <div className="mt-6 border-t border-slate-100 pt-4 flex items-center justify-between text-xs text-slate-500 w-full font-medium">
              <span>{school.studentCount ?? 0} Students</span>
              <span className="font-medium text-slate-900 group-hover:text-slate-950 flex items-center gap-1">
                Manage <ArrowRight className="h-3 w-3 group-hover:translate-x-0.5 transition-transform" />
              </span>
            </div>
          </button>
        ))}

        <button
          type="button"
          onClick={onCreateNew}
          className="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 bg-slate-50/50 p-6 text-center hover:bg-slate-50 hover:border-slate-400 transition min-h-[180px]"
        >
          <Plus className="h-8 w-8 text-slate-400" />
          <h3 className="mt-3 font-semibold text-slate-700">Register New School</h3>
          <p className="text-xs text-slate-500 mt-1">Configure credentials, admin account, payments, and gateway configs.</p>
        </button>
      </div>
    </div>
  );
}

interface CreateSchoolPageProps {
  form: CreateSchoolForm;
  onChange: React.Dispatch<React.SetStateAction<CreateSchoolForm>>;
  onSubmit: (e: React.FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
  isCreating: boolean;
}

function CreateSchoolPage({
  form,
  onChange,
  onSubmit,
  onCancel,
  isCreating
}: CreateSchoolPageProps) {
  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-950">Register a New School</h2>
        <p className="text-sm text-slate-500">Configure school details, initial administrator credentials, payment gateways, and SMS configurations.</p>
      </div>

      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <form className="space-y-6" onSubmit={onSubmit}>
          <div className="space-y-4">
            <h3 className="text-base font-semibold text-slate-900">Basic Info</h3>
            <div className="grid gap-4 md:grid-cols-2">
              <Field label="School name" value={form.name} onChange={(value) => updateForm(onChange, 'name', value)} required />
              <Field
                label="Code"
                value={form.code}
                onChange={(value) => updateForm(onChange, 'code', value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
                maxLength={10}
                required
              />
              <Field label="Email" type="email" value={form.email} onChange={(value) => updateForm(onChange, 'email', value)} required />
              <Field label="Phone" value={form.phone} onChange={(value) => updateForm(onChange, 'phone', value)} required />
              <Field label="Address" value={form.address} onChange={(value) => updateForm(onChange, 'address', value)} />
              <Field label="City" value={form.city} onChange={(value) => updateForm(onChange, 'city', value)} />
              <Field label="State" value={form.state} onChange={(value) => updateForm(onChange, 'state', value)} />
              <Field label="Country" value={form.country} onChange={(value) => updateForm(onChange, 'country', value)} />
            </div>
          </div>

          <div className="border-t border-slate-100 pt-6 space-y-4">
            <h3 className="text-base font-semibold text-slate-900">School Administrator</h3>
            <p className="text-xs text-slate-500 font-medium">An administrator account will be initialized inside Keycloak. The admin will manage configurations, staff access, and student fees.</p>
            <div className="grid gap-4 md:grid-cols-2">
              <Field label="Admin first name" value={form.adminFirstName} onChange={(value) => updateForm(onChange, 'adminFirstName', value)} required />
              <Field label="Admin last name" value={form.adminLastName} onChange={(value) => updateForm(onChange, 'adminLastName', value)} required />
              <Field label="Admin email" type="email" value={form.adminEmail} onChange={(value) => updateForm(onChange, 'adminEmail', value)} required />
              <Field label="Admin phone" value={form.adminPhoneNumber} onChange={(value) => updateForm(onChange, 'adminPhoneNumber', value)} required />
            </div>
          </div>

          <div className="border-t border-slate-100 pt-6 space-y-4">
            <h3 className="text-base font-semibold text-slate-900">Configurations & Providers (Optional)</h3>
            <div className="grid gap-4 md:grid-cols-2">
              <Field
                label="Academic year starts"
                value={form.academicYearStart}
                onChange={(value) => updateForm(onChange, 'academicYearStart', formatAcademicYearStart(value))}
                placeholder="09-08"
                pattern="^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$"
                description="Use MM-dd format, for example 09-08."
                maxLength={5}
                required
              />
              <Field label="Paystack public key" value={form.paystackPublicKey} onChange={(value) => updateForm(onChange, 'paystackPublicKey', value)} />
              <Field label="Paystack subaccount" value={form.paystackSubaccountCode} onChange={(value) => updateForm(onChange, 'paystackSubaccountCode', value)} />
              <Field label="SMS provider" value={form.smsProvider} onChange={(value) => updateForm(onChange, 'smsProvider', value)} />
              <Field label="SMS sender ID" value={form.smsSenderId} onChange={(value) => updateForm(onChange, 'smsSenderId', value)} maxLength={11} />
              <Field label="SMS username" value={form.smsUsername} onChange={(value) => updateForm(onChange, 'smsUsername', value)} />
              <Field label="SMS API key" value={form.smsApiKey} onChange={(value) => updateForm(onChange, 'smsApiKey', value)} />
            </div>
          </div>

          <div className="flex justify-end gap-3 border-t border-slate-100 pt-6">
            <Button type="button" variant="outline" onClick={onCancel} disabled={isCreating}>
              Cancel
            </Button>
            <Button type="submit" disabled={isCreating} className="bg-slate-950 text-white hover:bg-slate-800">
              {isCreating ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Plus className="mr-2 h-4 w-4" />}
              Create school
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

interface SchoolDetailsPageProps {
  schoolId: string;
  onBack: () => void;
  schools: SchoolSummary[];
  onDeactivate: (school: SchoolSummary) => void;
}

function SchoolDetailsPage({
  schoolId,
  onBack,
  schools,
  onDeactivate
}: SchoolDetailsPageProps) {
  const navigate = useNavigate();
  const [school, setSchool] = useState<SchoolDetail | null>(null);
  const [sessions, setSessions] = useState<AcademicSession[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [feeDashboard, setFeeDashboard] = useState<FeeDashboard | null>(null);
  const [dailySummary, setDailySummary] = useState<DailySummary | null>(null);
  const [notificationBalance, setNotificationBalance] = useState<NotificationBalance | null>(null);
  const [templates, setTemplates] = useState<NotificationTemplate[]>([]);
  const [schedules, setReminderSchedules] = useState<ReminderSchedule[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const matchedSchoolSummary = useMemo(() => {
    return schools.find((s) => s.schoolId === schoolId);
  }, [schools, schoolId]);

  useEffect(() => {
    async function load() {
      setIsLoading(true);
      setError(null);
      const endDate = toDateInputValue(new Date());
      const startDate = toDateInputValue(daysAgo(6));

      try {
        const [
          schoolResult,
          sessionsResult,
          usersResult,
          feeResult,
          dailyResult,
          balanceResult,
          templatesResult,
          schedulesResult
        ] = await Promise.all([
          superAdminService.getSchool(schoolId),
          superAdminService.getSessions(),
          superAdminService.listUsers('SCHOOL_ADMIN'),
          superAdminService.getFeeDashboard(),
          superAdminService.getDailySummary(startDate, endDate),
          superAdminService.getNotificationBalance(),
          superAdminService.getNotificationTemplates(),
          superAdminService.getReminderSchedules()
        ]);

        setSchool(schoolResult);
        setSessions(sessionsResult);
        setUsers(usersResult.content ?? []);
        setFeeDashboard(feeResult);
        setDailySummary(dailyResult);
        setNotificationBalance(balanceResult);
        setTemplates(templatesResult);
        setReminderSchedules(schedulesResult);
      } catch (err) {
        setError(readError(err, 'Failed to refresh school details'));
      } finally {
        setIsLoading(false);
      }
    }
    void load();
  }, [schoolId]);

  if (isLoading) {
    return <LoadingPanel label="Loading school profile details..." />;
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <button type="button" onClick={onBack} className="text-xs font-semibold text-slate-500 hover:text-slate-900 transition flex items-center gap-1">
            &larr; Back to Schools
          </button>
          <h2 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">{school?.name || 'School Profile'}</h2>
          <p className="text-xs text-slate-500 mt-0.5">{school?.code}</p>
        </div>
        <div className="flex gap-2">
          {matchedSchoolSummary && matchedSchoolSummary.status !== 'INACTIVE' && (
            <Button variant="outline" className="border-red-200 text-red-650 hover:bg-red-50 hover:text-red-700" onClick={() => onDeactivate(matchedSchoolSummary)}>
              Deactivate School
            </Button>
          )}
          <Button onClick={() => navigate(`/super-admin/schools/${schoolId}/dashboard`)} className="bg-slate-950 text-white hover:bg-slate-800">
            Impersonate School View
          </Button>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          <AlertCircle className="h-4 w-4" />
          {error}
        </div>
      )}

      <SelectedSchoolPanel
        selectedSchool={school}
        selectedSchoolName={school?.name || ''}
        isLoading={false}
        sessions={sessions}
        users={users}
        feeDashboard={feeDashboard}
        dailySummary={dailySummary}
        notificationBalance={notificationBalance}
        templates={templates}
        schedules={schedules}
      />
    </div>
  );
}

function PlatformStats({
  schoolCount,
  studentCount,
  activeUsers,
  collectionRate,
  totalCollected,
}: {
  schoolCount: number;
  studentCount: number;
  activeUsers: number;
  collectionRate: number;
  totalCollected: number;
}) {
  return (
    <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      <Metric icon={Building2} label="Active schools" value={formatNumber(schoolCount)} tone="slate" />
      <Metric icon={Users} label="Students" value={formatNumber(studentCount)} tone="blue" />
      <Metric icon={Banknote} label="Collected" value={formatCurrency(totalCollected)} tone="emerald" />
      <Metric icon={CircleDollarSign} label="Collection rate" value={`${Math.round(collectionRate)}%`} tone="amber" />
      <div className="rounded-md border border-slate-200 bg-white p-4 md:col-span-2 xl:col-span-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-sm font-medium text-slate-700">Platform utilization</p>
            <p className="text-xs text-slate-500">{formatNumber(activeUsers)} active users across schools</p>
          </div>
          <span className="text-sm font-semibold text-slate-900">{Math.round(collectionRate)}%</span>
        </div>
        <Progress className="mt-3 h-2 bg-slate-100" value={Math.min(100, Math.max(0, collectionRate))} />
      </div>
    </section>
  );
}

function Metric({
  icon: Icon,
  label,
  value,
  tone,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  tone: 'slate' | 'blue' | 'emerald' | 'amber';
}) {
  const tones = {
    slate: 'bg-slate-100 text-slate-700',
    blue: 'bg-blue-50 text-blue-700',
    emerald: 'bg-emerald-50 text-emerald-700',
    amber: 'bg-amber-50 text-amber-700',
  };
  return (
    <div className="rounded-md border border-slate-200 bg-white p-4">
      <div className={`mb-4 flex h-10 w-10 items-center justify-center rounded-md ${tones[tone]}`}>
        <Icon className="h-5 w-5" />
      </div>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-950">{value}</p>
    </div>
  );
}

function SchoolTable({
  schools,
  selectedSchoolId,
  onSelect,
  onDeactivate,
}: {
  schools: SchoolSummary[];
  selectedSchoolId: string | null;
  onSelect: (school: SchoolSummary) => void;
  onDeactivate: (school: SchoolSummary) => void;
}) {
  if (schools.length === 0) {
    return (
      <div className="flex min-h-72 flex-col items-center justify-center gap-3 p-8 text-center">
        <Building2 className="h-10 w-10 text-slate-300" />
        <p className="text-sm font-medium text-slate-700">No schools match this view</p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3 font-semibold">School</th>
            <th className="px-4 py-3 font-semibold">Students</th>
            <th className="px-4 py-3 font-semibold">Users</th>
            <th className="px-4 py-3 font-semibold">Collection</th>
            <th className="px-4 py-3 font-semibold">Status</th>
            <th className="px-4 py-3 text-right font-semibold">Actions</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {schools.map((school) => {
            const selected = selectedSchoolId === school.schoolId;
            return (
              <tr key={school.schoolId} className={selected ? 'bg-blue-50/70' : 'bg-white hover:bg-slate-50'}>
                <td className="px-4 py-4">
                  <div className="font-medium text-slate-950">{school.name}</div>
                  <div className="mt-1 text-xs text-slate-500">
                    {school.code} {school.city ? `- ${school.city}` : ''}
                  </div>
                </td>
                <td className="px-4 py-4 text-slate-700">{formatNumber(school.studentCount ?? 0)}</td>
                <td className="px-4 py-4 text-slate-700">{formatNumber(school.activeUsers ?? 0)}</td>
                <td className="px-4 py-4">
                  <div className="flex items-center gap-3">
                    <Progress className="h-2 w-24 bg-slate-100" value={school.collectionRate ?? 0} />
                    <span className="text-xs font-medium text-slate-700">{Math.round(school.collectionRate ?? 0)}%</span>
                  </div>
                </td>
                <td className="px-4 py-4">
                  <Badge
                    className={
                      school.status === 'ACTIVE'
                        ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-50'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-100'
                    }
                  >
                    {school.status ?? 'ACTIVE'}
                  </Badge>
                </td>
                <td className="px-4 py-4">
                  <div className="flex justify-end gap-2">
                    <Button variant={selected ? 'secondary' : 'outline'} size="sm" onClick={() => onSelect(school)}>
                      {selected ? 'Selected' : 'Manage'}
                    </Button>
                    {school.status !== 'INACTIVE' && (
                      <Button variant="ghost" size="sm" className="text-red-600 hover:text-red-700" onClick={() => onDeactivate(school)}>
                        Deactivate
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function SelectedSchoolPanel({
  selectedSchool,
  selectedSchoolName,
  isLoading,
  sessions,
  users,
  feeDashboard,
  dailySummary,
  notificationBalance,
  templates,
  schedules,
}: {
  selectedSchool: SchoolDetail | null;
  selectedSchoolName: string | null;
  isLoading: boolean;
  sessions: AcademicSession[];
  users: UserSummary[];
  feeDashboard: FeeDashboard | null;
  dailySummary: DailySummary | null;
  notificationBalance: NotificationBalance | null;
  templates: NotificationTemplate[];
  schedules: ReminderSchedule[];
}) {
  if (!selectedSchoolName) {
    return (
      <div className="rounded-md border border-dashed border-slate-300 bg-white p-8">
        <Building2 className="h-10 w-10 text-slate-300" />
        <h2 className="mt-4 text-lg font-semibold text-slate-950">No school selected</h2>
        <p className="mt-2 text-sm text-slate-500">Select a school to inspect sessions, users, collections, and messaging health.</p>
      </div>
    );
  }

  if (isLoading) {
    return <LoadingPanel label={`Loading ${selectedSchoolName}`} />;
  }

  return (
    <div className="rounded-md border border-slate-200 bg-white">
      <div className="border-b border-slate-200 p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold text-slate-950">{selectedSchool?.name ?? selectedSchoolName}</h2>
            <p className="mt-1 text-sm text-slate-500">
              {selectedSchool?.code} {selectedSchool?.city ? `- ${selectedSchool.city}` : ''}
            </p>
          </div>
          <Badge className="bg-blue-50 text-blue-700 hover:bg-blue-50">{selectedSchool?.status ?? 'ACTIVE'}</Badge>
        </div>
      </div>

      <Tabs defaultValue="setup" className="p-5">
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="setup">Setup</TabsTrigger>
          <TabsTrigger value="users">Users</TabsTrigger>
          <TabsTrigger value="finance">Finance</TabsTrigger>
          <TabsTrigger value="notify">Notify</TabsTrigger>
        </TabsList>

        <TabsContent value="setup" className="mt-5 space-y-4">
          <InfoLine icon={Mail} label="Email" value={selectedSchool?.email || 'Not set'} />
          <InfoLine icon={Building2} label="Address" value={formatAddress(selectedSchool)} />
          <InfoLine icon={CalendarDays} label="Current term" value={selectedSchool?.currentTerm?.name || 'Not set'} />
          <div className="rounded-md bg-slate-50 p-4">
            <p className="text-sm font-medium text-slate-800">Academic sessions</p>
            <div className="mt-3 space-y-3">
              {sessions.length ? (
                sessions.map((session) => (
                  <div key={session.sessionId} className="rounded-md border border-slate-200 bg-white p-3">
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-medium text-slate-900">{session.name}</span>
                      {session.isCurrent && <Badge className="bg-emerald-50 text-emerald-700 hover:bg-emerald-50">Current</Badge>}
                    </div>
                    <p className="mt-1 text-xs text-slate-500">{session.terms?.map((term) => term.name).join(', ')}</p>
                  </div>
                ))
              ) : (
                <p className="text-sm text-slate-500">No sessions returned.</p>
              )}
            </div>
          </div>
        </TabsContent>

        <TabsContent value="users" className="mt-5 space-y-3">
          {users.length ? (
            users.map((admin) => (
              <div key={admin.userId} className="flex items-center justify-between rounded-md border border-slate-200 p-3">
                <div>
                  <p className="font-medium text-slate-900">{[admin.firstName, admin.lastName].filter(Boolean).join(' ') || admin.email}</p>
                  <p className="text-xs text-slate-500">{admin.email}</p>
                </div>
                {admin.isActive ? <CheckCircle2 className="h-4 w-4 text-emerald-600" /> : <XCircle className="h-4 w-4 text-slate-400" />}
              </div>
            ))
          ) : (
            <p className="rounded-md bg-slate-50 p-4 text-sm text-slate-500">No school admins returned.</p>
          )}
        </TabsContent>

        <TabsContent value="finance" className="mt-5 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <MiniMetric label="Expected" value={formatCurrency(feeDashboard?.summary?.totalExpected ?? 0)} />
            <MiniMetric label="Collected" value={formatCurrency(feeDashboard?.summary?.totalCollected ?? dailySummary?.totalCollected ?? 0)} />
            <MiniMetric label="Outstanding" value={formatCurrency(feeDashboard?.summary?.totalOutstanding ?? 0)} />
            <MiniMetric label="Transactions" value={formatNumber(dailySummary?.totalTransactions ?? 0)} />
          </div>
          <div className="rounded-md bg-slate-50 p-4">
            <p className="text-sm font-medium text-slate-800">Class collection</p>
            <div className="mt-3 space-y-3">
              {(feeDashboard?.byClass ?? []).slice(0, 4).map((row) => (
                <div key={row.classId}>
                  <div className="mb-1 flex items-center justify-between text-xs">
                    <span className="font-medium text-slate-700">{row.className}</span>
                    <span className="text-slate-500">{Math.round(row.collectionRate)}%</span>
                  </div>
                  <Progress className="h-2 bg-white" value={row.collectionRate} />
                </div>
              ))}
              {!feeDashboard?.byClass?.length && <p className="text-sm text-slate-500">No class collection data returned.</p>}
            </div>
          </div>
        </TabsContent>

        <TabsContent value="notify" className="mt-5 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <MiniMetric label="Provider" value={notificationBalance?.provider ?? 'N/A'} />
            <MiniMetric label="SMS balance" value={formatNumber(notificationBalance?.balance ?? 0)} />
            <MiniMetric label="Templates" value={formatNumber(templates.length)} />
            <MiniMetric label="Schedules" value={formatNumber(schedules.length)} />
          </div>
          <div className="space-y-2">
            {templates.slice(0, 3).map((template) => (
              <div key={template.templateId} className="flex items-center justify-between rounded-md border border-slate-200 p-3 text-sm">
                <span className="font-medium text-slate-800">{template.name}</span>
                <Badge variant="outline">{template.channel}</Badge>
              </div>
            ))}
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function EmptyPlatform({ onCreate }: { onCreate: () => void }) {
  return (
    <section className="flex min-h-[70vh] items-center justify-center rounded-md border border-dashed border-slate-300 bg-white p-8 text-center">
      <div className="max-w-xl">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-md bg-slate-950 text-white">
          <Building2 className="h-8 w-8" />
        </div>
        <h2 className="mt-6 text-3xl font-semibold tracking-tight text-slate-950">Welcome. No schools yet.</h2>
        <p className="mt-3 text-slate-500">Create the first school to activate sessions, terms, admin access, payments, and notifications.</p>
        <Button className="mt-6" size="lg" onClick={onCreate}>
          <Plus className="h-4 w-4" />
          Create first school
        </Button>
      </div>
    </section>
  );
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <div className="flex min-h-72 items-center justify-center rounded-md border border-slate-200 bg-white">
      <div className="flex items-center gap-3 text-sm text-slate-600">
        <Loader2 className="h-5 w-5 animate-spin" />
        {label}
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = 'text',
  required = false,
  maxLength,
  placeholder,
  pattern,
  description,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  required?: boolean;
  maxLength?: number;
  placeholder?: string;
  pattern?: string;
  description?: string;
}) {
  const id = label.toLowerCase().replace(/\s+/g, '-');
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        value={value}
        type={type}
        required={required}
        maxLength={maxLength}
        placeholder={placeholder}
        pattern={pattern}
        onChange={(event) => onChange(event.target.value)}
      />
      {description && <p className="text-xs text-slate-500">{description}</p>}
    </div>
  );
}

function InfoLine({
  icon: Icon,
  label,
  value,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-3 rounded-md border border-slate-200 p-3">
      <Icon className="h-4 w-4 text-slate-400" />
      <div>
        <p className="text-xs text-slate-500">{label}</p>
        <p className="text-sm font-medium text-slate-900">{value}</p>
      </div>
    </div>
  );
}

function MiniMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-slate-50 p-3">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 truncate text-sm font-semibold text-slate-950">{value}</p>
    </div>
  );
}

function updateForm(
  setCreateForm: React.Dispatch<React.SetStateAction<CreateSchoolForm>>,
  key: keyof CreateSchoolForm,
  value: string,
) {
  setCreateForm((current) => ({ ...current, [key]: value }));
}

function buildCreatePayload(form: CreateSchoolForm): CreateSchoolPayload {
  return {
    name: form.name.trim(),
    code: form.code.trim(),
    email: form.email.trim(),
    phone: form.phone.trim(),
    address: trimOptional(form.address),
    city: trimOptional(form.city),
    state: trimOptional(form.state),
    country: trimOptional(form.country) ?? 'Nigeria',
    adminUser: {
      email: form.adminEmail.trim(),
      firstName: form.adminFirstName.trim(),
      lastName: form.adminLastName.trim(),
      phoneNumber: form.adminPhoneNumber.trim(),
    },
    paymentConfig: {
      paystackPublicKey: trimOptional(form.paystackPublicKey),
      paystackSubaccountCode: trimOptional(form.paystackSubaccountCode),
      acceptedPaymentMethods: ['CARD', 'BANK_TRANSFER'],
    },
    smsConfig: {
      provider: trimOptional(form.smsProvider),
      apiKey: trimOptional(form.smsApiKey),
      username: trimOptional(form.smsUsername),
      senderId: trimOptional(form.smsSenderId),
      defaultCountryCode: '+234',
    },
    termConfig: {
      termsPerYear: 3,
      termNames: ['First Term', 'Second Term', 'Third Term'],
      academicYearStart: form.academicYearStart,
    },
  };
}

function formatAcademicYearStart(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 4);
  if (digits.length <= 2) return digits;
  return `${digits.slice(0, 2)}-${digits.slice(2)}`;
}

function trimOptional(value: string) {
  const trimmed = value.trim();
  return trimmed.length ? trimmed : undefined;
}

function formatAddress(school: SchoolDetail | null) {
  if (!school) return 'Not set';
  return [school.address, school.city, school.state, school.country].filter(Boolean).join(', ') || 'Not set';
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

export default SuperAdminDashboard;
