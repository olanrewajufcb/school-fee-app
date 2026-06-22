import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  CalendarCheck,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Loader2,
  RefreshCw,
  Search,
  UserCheck,
  UserRoundX,
  Users,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import type {
  AttendanceResponse,
  AttendanceSummaryResponse,
  ClassRoom,
  StudentSummary,
  TodayAttendanceResponse,
} from '@/services/schoolAdminService';

interface AttendanceMonitoringSectionProps {
  classes: ClassRoom[];
  students: StudentSummary[];
  attendanceByClass: Record<string, TodayAttendanceResponse>;
  selectedClassId: string;
  selectedStudentId: string;
  history: AttendanceResponse[];
  summary: AttendanceSummaryResponse | null;
  currentTermName?: string;
  currentSessionName?: string;
  isLoading: boolean;
  isOverviewLoading: boolean;
  onSelectClass: (classId: string) => void;
  onSelectStudent: (studentId: string) => void;
  onRefresh: () => void;
}

interface DailyStudentAttendance {
  studentId: string;
  studentName: string;
  admissionNumber?: string;
  morning?: AttendanceResponse;
  afternoon?: AttendanceResponse;
}

interface AttendanceDay {
  date: string;
  morning?: AttendanceResponse;
  afternoon?: AttendanceResponse;
}

export function AttendanceMonitoringSection({
  classes,
  students,
  attendanceByClass,
  selectedClassId,
  selectedStudentId,
  history,
  summary,
  currentTermName,
  currentSessionName,
  isLoading,
  isOverviewLoading,
  onSelectClass,
  onSelectStudent,
  onRefresh,
}: AttendanceMonitoringSectionProps) {
  const [studentQuery, setStudentQuery] = useState('');

  const attendanceRows = useMemo(
    () => classes.map((classRoom) => ({
      classRoom,
      attendance: attendanceByClass[classRoom.classId] ?? emptyClassAttendance(classRoom),
    })),
    [attendanceByClass, classes],
  );

  const schoolTotals = useMemo(
    () => attendanceRows.reduce(
      (totals, row) => ({
        total: totals.total + row.attendance.totalStudents,
        present: totals.present + row.attendance.present,
        absent: totals.absent + row.attendance.absent,
        late: totals.late + row.attendance.late,
        notMarked: totals.notMarked + row.attendance.notMarked,
      }),
      { total: 0, present: 0, absent: 0, late: 0, notMarked: 0 },
    ),
    [attendanceRows],
  );

  const attendanceRate = schoolTotals.total > 0
    ? ((schoolTotals.present / schoolTotals.total) * 100)
    : 0;
  const classesPending = attendanceRows.filter((row) => row.attendance.notMarked > 0).length;
  const selectedClass = classes.find((classRoom) => classRoom.classId === selectedClassId) ?? classes[0];
  const selectedClassAttendance = selectedClass
    ? attendanceByClass[selectedClass.classId] ?? emptyClassAttendance(selectedClass)
    : null;
  const selectedClassStudents = useMemo(
    () => mergeDailyStudentAttendance(selectedClassAttendance?.students ?? []),
    [selectedClassAttendance],
  );

  const matchingStudents = useMemo(() => {
    const query = studentQuery.trim().toLowerCase();
    const ranked = query
      ? students.filter((student) => {
          const name = `${student.firstName} ${student.middleName ?? ''} ${student.lastName}`.toLowerCase();
          return name.includes(query) || student.admissionNumber.toLowerCase().includes(query);
        })
      : students;
    return ranked.slice(0, 6);
  }, [studentQuery, students]);

  const selectedStudent = students.find((student) => student.studentId === selectedStudentId);
  const historyDays = useMemo(() => groupAttendanceHistory(history), [history]);
  const overviewDate = attendanceRows.find((row) => row.attendance.date)?.attendance.date
    ?? new Date().toISOString().slice(0, 10);

  return (
    <div className="space-y-6">
      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 bg-gradient-to-r from-slate-950 via-slate-900 to-blue-950 px-5 py-5 text-white sm:px-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="flex items-center gap-2 text-sm font-medium text-blue-200">
                <CalendarCheck className="h-4 w-4" />
                Daily school register
              </div>
              <h2 className="mt-1 text-2xl font-semibold">Today&apos;s Attendance</h2>
              <p className="mt-1 text-sm text-slate-300">{formatLongDate(overviewDate)}</p>
            </div>
            <Button
              variant="outline"
              onClick={onRefresh}
              disabled={isOverviewLoading}
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white"
            >
              {isOverviewLoading
                ? <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                : <RefreshCw className="mr-2 h-4 w-4" />}
              Refresh
            </Button>
          </div>
        </div>

        <div className="grid gap-px bg-slate-200 sm:grid-cols-2 xl:grid-cols-4">
          <AttendanceMetric
            icon={UserCheck}
            label="Present today"
            value={schoolTotals.present}
            detail={`${attendanceRate.toFixed(1)}% attendance rate`}
            tone="emerald"
          />
          <AttendanceMetric
            icon={UserRoundX}
            label="Absent"
            value={schoolTotals.absent}
            detail={`${schoolTotals.total} students expected`}
            tone="rose"
          />
          <AttendanceMetric
            icon={Clock3}
            label="Late arrivals"
            value={schoolTotals.late}
            detail="Included in present"
            tone="amber"
          />
          <AttendanceMetric
            icon={AlertTriangle}
            label="Not marked"
            value={schoolTotals.notMarked}
            detail={classesPending ? `${classesPending} ${pluralize(classesPending, 'class', 'classes')} pending` : 'All classes submitted'}
            tone={schoolTotals.notMarked > 0 ? 'amber' : 'slate'}
          />
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-2 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="font-semibold text-slate-950">Class overview</h3>
            <p className="mt-0.5 text-sm text-slate-500">Select a class to inspect each student&apos;s arrival and departure.</p>
          </div>
          {classesPending > 0 && (
            <Badge className="w-fit bg-amber-100 text-amber-800 hover:bg-amber-100">
              <AlertTriangle className="mr-1 h-3.5 w-3.5" />
              {classesPending} awaiting completion
            </Badge>
          )}
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3 font-semibold">Class</th>
                <th className="px-4 py-3 text-center font-semibold">Total</th>
                <th className="px-4 py-3 text-center font-semibold">Present</th>
                <th className="px-4 py-3 text-center font-semibold">Absent</th>
                <th className="px-4 py-3 text-center font-semibold">Late</th>
                <th className="px-4 py-3 text-center font-semibold">Not marked</th>
                <th className="px-5 py-3 text-right font-semibold">Completion</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {attendanceRows.map(({ classRoom, attendance }) => {
                const completion = attendance.totalStudents > 0
                  ? ((attendance.totalStudents - attendance.notMarked) / attendance.totalStudents) * 100
                  : 0;
                const isSelected = selectedClass?.classId === classRoom.classId;
                return (
                  <tr
                    key={classRoom.classId}
                    onClick={() => onSelectClass(classRoom.classId)}
                    className={`cursor-pointer transition-colors ${isSelected ? 'bg-blue-50/70' : 'hover:bg-slate-50'}`}
                  >
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-3">
                        <span className={`flex h-9 w-9 items-center justify-center rounded-lg text-xs font-bold ${
                          isSelected ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600'
                        }`}>
                          {initials(classRoom.name)}
                        </span>
                        <div>
                          <p className="font-semibold text-slate-900">{classRoom.name}</p>
                          <p className="text-xs text-slate-500">{classRoom.classTeacher?.name ?? 'No teacher assigned'}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3.5 text-center font-medium text-slate-700">{attendance.totalStudents}</td>
                    <td className="px-4 py-3.5 text-center font-semibold text-emerald-700">{attendance.present}</td>
                    <td className="px-4 py-3.5 text-center font-semibold text-rose-700">{attendance.absent}</td>
                    <td className="px-4 py-3.5 text-center font-semibold text-amber-700">{attendance.late}</td>
                    <td className="px-4 py-3.5 text-center">
                      <span className={attendance.notMarked > 0 ? 'font-semibold text-amber-700' : 'text-slate-400'}>
                        {attendance.notMarked}
                        {attendance.notMarked > 0 && <AlertTriangle className="ml-1 inline h-3.5 w-3.5" />}
                      </span>
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="ml-auto flex w-36 items-center gap-3">
                        <Progress value={completion} className="h-1.5 flex-1 bg-slate-100" />
                        <span className="w-9 text-right text-xs font-medium text-slate-500">{completion.toFixed(0)}%</span>
                        <ChevronRight className="h-4 w-4 text-slate-400" />
                      </div>
                    </td>
                  </tr>
                );
              })}
              {!attendanceRows.length && (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-500">
                    No active classes are available for attendance monitoring.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {selectedClass && selectedClassAttendance && (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-semibold text-slate-950">{selectedClass.name} register</h3>
                <AttendanceCompletionBadge attendance={selectedClassAttendance} />
              </div>
              <p className="mt-1 text-sm text-slate-500">
                Class teacher: <span className="font-medium text-slate-700">{selectedClass.classTeacher?.name ?? 'Not assigned'}</span>
              </p>
            </div>
            {selectedClassAttendance.notMarked > 0 && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                <span className="font-semibold">{selectedClassAttendance.notMarked} students</span> still need a morning mark.
                {selectedClass.classTeacher?.name && <> Follow up with {selectedClass.classTeacher.name}.</>}
              </div>
            )}
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-5 py-3 font-semibold">Student</th>
                  <th className="px-4 py-3 font-semibold">Morning</th>
                  <th className="px-4 py-3 font-semibold">Brought by</th>
                  <th className="px-4 py-3 font-semibold">Afternoon</th>
                  <th className="px-4 py-3 font-semibold">Picked up by</th>
                  <th className="px-5 py-3 text-right font-semibold">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {selectedClassStudents.map((student) => (
                  <tr key={student.studentId} className="hover:bg-slate-50/70">
                    <td className="px-5 py-3.5">
                      <button
                        type="button"
                        onClick={() => onSelectStudent(student.studentId)}
                        className="text-left"
                      >
                        <p className="font-semibold text-slate-900 hover:text-blue-700">{student.studentName}</p>
                        <p className="text-xs text-slate-500">{student.admissionNumber ?? 'No admission number'}</p>
                      </button>
                    </td>
                    <td className="px-4 py-3.5">
                      <SessionCell record={student.morning} session="morning" />
                    </td>
                    <td className="px-4 py-3.5 text-slate-600">{student.morning?.broughtBy ?? '—'}</td>
                    <td className="px-4 py-3.5">
                      <SessionCell record={student.afternoon} session="afternoon" />
                    </td>
                    <td className="px-4 py-3.5 text-slate-600">
                      {student.afternoon?.pickUpPersonName ?? student.afternoon?.pickedUpBy ?? '—'}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <AttendanceStatusBadge status={student.morning?.status ?? 'NOT_MARKED'} />
                    </td>
                  </tr>
                ))}
                {!selectedClassStudents.length && (
                  <tr>
                    <td colSpan={6} className="px-5 py-10 text-center text-slate-500">
                      No student attendance details are available for this class yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section className="rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 px-5 py-4">
          <h3 className="font-semibold text-slate-950">Student attendance history</h3>
          <p className="mt-0.5 text-sm text-slate-500">
            Search a student to review their term record, arrival details, and attendance percentage.
          </p>
        </div>

        <div className="grid xl:grid-cols-[320px_1fr]">
          <aside className="border-b border-slate-200 p-5 xl:border-b-0 xl:border-r">
            <label htmlFor="attendance-student-search" className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Find a student
            </label>
            <div className="relative mt-2">
              <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
              <Input
                id="attendance-student-search"
                value={studentQuery}
                onChange={(event) => setStudentQuery(event.target.value)}
                placeholder="Name or admission number"
                className="pl-9"
              />
            </div>
            <div className="mt-3 space-y-1">
              {matchingStudents.map((student) => {
                const isSelected = student.studentId === selectedStudentId;
                return (
                  <button
                    key={student.studentId}
                    type="button"
                    onClick={() => onSelectStudent(student.studentId)}
                    className={`w-full rounded-lg px-3 py-2.5 text-left transition-colors ${
                      isSelected ? 'bg-blue-50 ring-1 ring-blue-200' : 'hover:bg-slate-50'
                    }`}
                  >
                    <p className={`text-sm font-semibold ${isSelected ? 'text-blue-800' : 'text-slate-800'}`}>
                      {student.firstName} {student.lastName}
                    </p>
                    <p className="mt-0.5 text-xs text-slate-500">
                      {student.admissionNumber} · {student.currentClass?.name ?? 'Unassigned'}
                    </p>
                  </button>
                );
              })}
              {!matchingStudents.length && (
                <p className="rounded-lg bg-slate-50 px-3 py-6 text-center text-sm text-slate-500">
                  No matching students.
                </p>
              )}
            </div>
          </aside>

          <div className="min-w-0 p-5 sm:p-6">
            {!selectedStudentId ? (
              <div className="flex min-h-64 flex-col items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50/60 p-8 text-center">
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-white shadow-sm">
                  <Users className="h-5 w-5 text-slate-500" />
                </span>
                <h4 className="mt-4 font-semibold text-slate-900">Choose a student</h4>
                <p className="mt-1 max-w-sm text-sm text-slate-500">
                  Their attendance history and term summary will appear here.
                </p>
              </div>
            ) : isLoading ? (
              <div className="flex min-h-64 items-center justify-center gap-3 text-sm text-slate-500">
                <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
                Loading attendance history…
              </div>
            ) : (
              <div className="space-y-5">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">Attendance history</p>
                    <h4 className="mt-1 text-xl font-semibold text-slate-950">
                      {selectedStudent
                        ? `${selectedStudent.firstName} ${selectedStudent.lastName}`
                        : history[0]?.studentName ?? 'Selected student'}
                    </h4>
                    <p className="mt-1 text-sm text-slate-500">
                      {[currentTermName, currentSessionName].filter(Boolean).join(' · ') || 'Current term'}
                    </p>
                  </div>
                  {summary && (
                    <Badge className={summary.attendancePercentage >= 80
                      ? 'w-fit bg-emerald-100 text-emerald-800 hover:bg-emerald-100'
                      : 'w-fit bg-amber-100 text-amber-800 hover:bg-amber-100'}
                    >
                      {summary.attendancePercentage.toFixed(1)}% attendance
                    </Badge>
                  )}
                </div>

                {summary && (
                  <div className="grid gap-3 sm:grid-cols-4">
                    <SummaryStat label="Present" value={`${summary.daysPresent}/${summary.totalSchoolDays}`} tone="emerald" />
                    <SummaryStat label="Absent" value={String(summary.daysAbsent)} tone="rose" />
                    <SummaryStat label="Late" value={String(summary.daysLate)} tone="amber" />
                    <SummaryStat label="Early pickups" value={String(summary.earlyPickups)} tone="blue" />
                  </div>
                )}

                <div className="overflow-x-auto rounded-xl border border-slate-200">
                  <table className="w-full min-w-[670px] text-left text-sm">
                    <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                      <tr>
                        <th className="px-4 py-3 font-semibold">Date</th>
                        <th className="px-4 py-3 font-semibold">Morning</th>
                        <th className="px-4 py-3 font-semibold">Afternoon</th>
                        <th className="px-4 py-3 text-right font-semibold">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {historyDays.map((day) => (
                        <tr key={day.date}>
                          <td className="whitespace-nowrap px-4 py-3.5 font-medium text-slate-800">{formatShortDate(day.date)}</td>
                          <td className="px-4 py-3.5">
                            <HistorySession record={day.morning} session="morning" />
                          </td>
                          <td className="px-4 py-3.5">
                            <HistorySession record={day.afternoon} session="afternoon" />
                          </td>
                          <td className="px-4 py-3.5 text-right">
                            <AttendanceStatusBadge status={day.morning?.status ?? 'NOT_MARKED'} />
                          </td>
                        </tr>
                      ))}
                      {!historyDays.length && (
                        <tr>
                          <td colSpan={4} className="px-4 py-10 text-center text-slate-500">
                            No attendance has been recorded for this student in the selected term.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}

function AttendanceMetric({
  icon: Icon,
  label,
  value,
  detail,
  tone,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number;
  detail: string;
  tone: 'emerald' | 'rose' | 'amber' | 'slate';
}) {
  const colors = {
    emerald: 'bg-emerald-50 text-emerald-700',
    rose: 'bg-rose-50 text-rose-700',
    amber: 'bg-amber-50 text-amber-700',
    slate: 'bg-slate-100 text-slate-600',
  };
  return (
    <div className="bg-white p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-slate-500">{label}</p>
          <p className="mt-1 text-2xl font-semibold text-slate-950">{value.toLocaleString()}</p>
          <p className="mt-1 text-xs text-slate-500">{detail}</p>
        </div>
        <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${colors[tone]}`}>
          <Icon className="h-5 w-5" />
        </span>
      </div>
    </div>
  );
}

function AttendanceCompletionBadge({ attendance }: { attendance: TodayAttendanceResponse }) {
  if (attendance.notMarked === 0 && attendance.totalStudents > 0) {
    return (
      <Badge className="bg-emerald-100 text-emerald-800 hover:bg-emerald-100">
        <CheckCircle2 className="mr-1 h-3.5 w-3.5" />
        Morning complete
      </Badge>
    );
  }
  return (
    <Badge className="bg-amber-100 text-amber-800 hover:bg-amber-100">
      <AlertTriangle className="mr-1 h-3.5 w-3.5" />
      Morning incomplete
    </Badge>
  );
}

function AttendanceStatusBadge({ status }: { status: string }) {
  const normalized = status.toUpperCase();
  const styles: Record<string, string> = {
    PRESENT: 'bg-emerald-100 text-emerald-800',
    LATE: 'bg-amber-100 text-amber-800',
    ABSENT: 'bg-rose-100 text-rose-800',
    EXCUSED: 'bg-blue-100 text-blue-800',
    PICKED_UP_EARLY: 'bg-violet-100 text-violet-800',
    NOT_MARKED: 'bg-slate-100 text-slate-600',
  };
  return (
    <Badge className={styles[normalized] ?? styles.NOT_MARKED}>
      {formatStatus(normalized)}
    </Badge>
  );
}

function SessionCell({ record, session }: { record?: AttendanceResponse; session: 'morning' | 'afternoon' }) {
  if (!record || record.status === 'NOT_MARKED') {
    return <span className="text-slate-400">Not marked</span>;
  }
  if (record.status === 'ABSENT') {
    return <span className="font-medium text-rose-700">Absent</span>;
  }
  const time = session === 'morning' ? record.arrivalTime : record.departureTime;
  return (
    <div>
      <p className="font-medium text-slate-800">{time ? formatTime(time) : formatStatus(record.status)}</p>
      {record.status === 'LATE' && <p className="text-xs font-medium text-amber-700">Late arrival</p>}
      {record.status === 'PICKED_UP_EARLY' && <p className="text-xs font-medium text-violet-700">Picked up early</p>}
    </div>
  );
}

function HistorySession({ record, session }: { record?: AttendanceResponse; session: 'morning' | 'afternoon' }) {
  if (!record) return <span className="text-slate-400">Not marked</span>;
  if (record.status === 'ABSENT') return <span className="font-medium text-rose-700">Absent</span>;

  const time = session === 'morning' ? record.arrivalTime : record.departureTime;
  const person = session === 'morning'
    ? record.broughtBy
    : record.pickUpPersonName ?? record.pickedUpBy;
  return (
    <div>
      <p className="font-medium text-slate-800">{time ? formatTime(time) : formatStatus(record.status)}</p>
      {person && <p className="mt-0.5 text-xs text-slate-500">{person}</p>}
    </div>
  );
}

function SummaryStat({ label, value, tone }: { label: string; value: string; tone: 'emerald' | 'rose' | 'amber' | 'blue' }) {
  const colors = {
    emerald: 'border-emerald-100 bg-emerald-50/70 text-emerald-800',
    rose: 'border-rose-100 bg-rose-50/70 text-rose-800',
    amber: 'border-amber-100 bg-amber-50/70 text-amber-800',
    blue: 'border-blue-100 bg-blue-50/70 text-blue-800',
  };
  return (
    <div className={`rounded-lg border px-3 py-3 ${colors[tone]}`}>
      <p className="text-xs font-medium opacity-75">{label}</p>
      <p className="mt-1 text-lg font-semibold">{value}</p>
    </div>
  );
}

function mergeDailyStudentAttendance(records: AttendanceResponse[]): DailyStudentAttendance[] {
  const students = new Map<string, DailyStudentAttendance>();
  records.forEach((record) => {
    const existing = students.get(record.studentId) ?? {
      studentId: record.studentId,
      studentName: record.studentName ?? 'Student',
      admissionNumber: record.admissionNumber,
    };
    if (record.sessionType === 'MORNING_ARRIVAL') existing.morning = record;
    if (record.sessionType === 'AFTERNOON_DEPARTURE') existing.afternoon = record;
    students.set(record.studentId, existing);
  });
  return Array.from(students.values()).sort((left, right) => left.studentName.localeCompare(right.studentName));
}

function groupAttendanceHistory(records: AttendanceResponse[]): AttendanceDay[] {
  const days = new Map<string, AttendanceDay>();
  records.forEach((record) => {
    const day = days.get(record.date) ?? { date: record.date };
    if (record.sessionType === 'MORNING_ARRIVAL') day.morning = record;
    if (record.sessionType === 'AFTERNOON_DEPARTURE') day.afternoon = record;
    days.set(record.date, day);
  });
  return Array.from(days.values()).sort((left, right) => right.date.localeCompare(left.date));
}

function emptyClassAttendance(classRoom: ClassRoom): TodayAttendanceResponse {
  return {
    classId: classRoom.classId,
    className: classRoom.name,
    date: new Date().toISOString().slice(0, 10),
    totalStudents: classRoom.currentEnrollment ?? 0,
    present: 0,
    absent: 0,
    late: 0,
    notMarked: classRoom.currentEnrollment ?? 0,
    students: [],
  };
}

function formatLongDate(value: string) {
  return parseDate(value).toLocaleDateString('en-NG', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });
}

function formatShortDate(value: string) {
  return parseDate(value).toLocaleDateString('en-NG', {
    month: 'short',
    day: 'numeric',
  });
}

function parseDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, (month || 1) - 1, day || 1);
}

function formatTime(value: string) {
  const [hourText, minuteText] = value.split(':');
  const hour = Number(hourText);
  const minute = Number(minuteText);
  if (Number.isNaN(hour) || Number.isNaN(minute)) return value;
  const suffix = hour >= 12 ? 'PM' : 'AM';
  const displayHour = hour % 12 || 12;
  return `${displayHour}:${String(minute).padStart(2, '0')} ${suffix}`;
}

function formatStatus(status: string) {
  return status
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

function pluralize(value: number, singular: string, plural: string) {
  return value === 1 ? singular : plural;
}
