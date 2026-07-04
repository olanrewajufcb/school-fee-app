import React, { useEffect, useMemo, useState } from 'react';
import {
  Award,
  Banknote,
  BookOpen,
  CheckCircle2,
  ClipboardList,
  GraduationCap,
  LayoutDashboard,
  Loader2,
  LogOut,
  MessageSquareText,
  RefreshCw,
  Save,
  Search,
  ShieldCheck,
  Users,
  XCircle,
  Clock,
  ArrowRight,
  UserCheck,
  Calendar,
  Sparkles,
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
  type AcademicSession,
  type AssessmentTrait,
  type CaConfig,
  type ClassDetail,
  type ClassResultSheet,
  type ClassRoom,
  type ClassStudent,
  type GradingRules,
  type StudentSubjectResult,
  teacherService,
} from '@/services/teacherService';
import {
  attendanceService,
  type AttendanceSessionResponse,
} from '@/services/attendanceService';

type Section = 'overview' | 'classes' | 'attendance' | 'scores' | 'results' | 'comments' | 'assessments' | 'reference';
type ScoreMode = 'ca' | 'exam';

interface ScoreForm {
  mode: ScoreMode;
  subjectId: string;
  assessmentId: string;
  maxScore: string;
  scores: Record<string, string>;
  scoreIds?: Record<string, string>;
  originalScores?: Record<string, string>;
  caScores?: Record<string, Record<string, string>>;
  existingSubjectResults?: Record<string, StudentSubjectResult>;
  studentIdFilter?: string;
}

interface ScorePreviewCacheEntry {
  caScores?: Record<string, Record<string, string>>;
  examScores?: Record<string, string>;
}

const sections: Array<{ id: Section; label: string; icon: React.ComponentType<{ className?: string }> }> = [
  { id: 'overview', label: 'Overview', icon: LayoutDashboard },
  { id: 'classes', label: 'My Classes', icon: GraduationCap },
  { id: 'attendance', label: 'Attendance', icon: UserCheck },
  { id: 'scores', label: 'Scores', icon: ClipboardList },
  { id: 'results', label: 'Results', icon: Award },
  { id: 'comments', label: 'Comments', icon: MessageSquareText },
  { id: 'assessments', label: 'Assessments', icon: Sparkles },
  { id: 'reference', label: 'Reference', icon: BookOpen },
];

export const TeacherDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const [activeSection, setActiveSection] = useState<Section>('overview');
  const [sessions, setSessions] = useState<AcademicSession[]>([]);
  const [classes, setClasses] = useState<ClassRoom[]>([]);
  const [selectedClassId, setSelectedClassId] = useState('');
  const [classDetail, setClassDetail] = useState<ClassDetail | null>(null);
  const [caConfig, setCaConfig] = useState<CaConfig | null>(null);
  const [gradingRules, setGradingRules] = useState<GradingRules | null>(null);
  const [resultSheet, setResultSheet] = useState<ClassResultSheet | null>(null);
  const [studentSearch, setStudentSearch] = useState('');
  const [scoreDialog, setScoreDialog] = useState<ScoreMode | null>(null);
  const [scoreForm, setScoreForm] = useState<ScoreForm>({
    mode: 'ca',
    subjectId: '',
    assessmentId: '',
    maxScore: '20',
    scores: {},
    studentIdFilter: 'ALL',
  });
  const [scorePreviewCache, setScorePreviewCache] = useState<Record<string, ScorePreviewCacheEntry>>({});
  const [lookupSubjects, setLookupSubjects] = useState<Array<{ id: string; name: string; code: string }>>([]);
  const [lookupComponents, setLookupComponents] = useState<Array<{ id: string; name: string; maxScore: number; weightPercentage?: number; sortOrder?: number }>>([]);
  const [lookupExams, setLookupExams] = useState<Array<{ id: string; name: string; maxScore: number }>>([]);
  const [assessmentTraits, setAssessmentTraits] = useState<AssessmentTrait[]>([]);
  const [traitRatings, setTraitRatings] = useState<Record<string, Record<string, { rating: string; comment: string }>>>({});
  const [isLookupLoading, setIsLookupLoading] = useState(false);
  const [commentStudentId, setCommentStudentId] = useState('');
  const [commentText, setCommentText] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isClassLoading, setIsClassLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // Attendance states
  const [todaySessions, setTodaySessions] = useState<Record<string, AttendanceSessionResponse[]>>({});
  const [selectedSession, setSelectedSession] = useState<AttendanceSessionResponse | null>(null);
  const [attendanceMarks, setAttendanceMarks] = useState<Record<string, {
    status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED' | 'NOT_MARKED';
    arrivalTime?: string;
    broughtBy?: string;
    departureTime?: string;
    pickedUpBy?: string;
    pickUpPersonName?: string;
    pickUpPersonPhone?: string;
    notes?: string;
    markId?: string;
  }>>({});
  const [isAttendanceLoading, setIsAttendanceLoading] = useState(false);
  const [editingStudentId, setEditingStudentId] = useState<string | null>(null);

  const currentSession = useMemo(
    () => sessions.find((session) => session.isCurrent) ?? sessions[0],
    [sessions],
  );
  const currentTerm = useMemo(
    () => currentSession?.terms?.find((term) => term.isCurrent) ?? currentSession?.terms?.[0],
    [currentSession],
  );

  const assignedClasses = useMemo(() => {
    const directlyAssigned = classes.filter((item) => item.classTeacher?.userId === user?.userId);
    return directlyAssigned.length ? directlyAssigned : classes;
  }, [classes, user?.userId]);

  const students = classDetail?.students ?? [];
  const visibleStudents = useMemo(() => {
    const search = studentSearch.trim().toLowerCase();
    if (!search) return students;
    return students.filter((student) =>
      [
        student.firstName,
        student.lastName,
        student.admissionNumber,
        student.parentPhone,
        student.gender,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(search)),
    );
  }, [studentSearch, students]);

  const feePaidCount = classDetail?.statistics?.fullyPaidFees ?? students.filter((student) => student.feeStatus?.status === 'PAID').length;
  const pendingFeesCount = classDetail?.statistics?.pendingFees ?? students.filter((student) => student.feeStatus?.status && student.feeStatus.status !== 'PAID').length;
  const resultRows = resultSheet?.students ?? [];
  const blankScoreMap = () => Object.fromEntries(students.map((student) => [student.studentId, '']));
  const blankCaScoreMap = (components = lookupComponents) => Object.fromEntries(
    students.map((student) => [
      student.studentId,
      Object.fromEntries(components.map((comp) => [comp.id, ''])),
    ]),
  );
  const scoreCacheKey = (subjectId: string) => `${selectedClassId}:${currentTerm?.termId ?? ''}:${subjectId}`;

  const loadExistingSubjectScores = async (
    subjectId: string,
    examId: string,
    components = lookupComponents,
  ) => {
    const nextScores: Record<string, string> = blankScoreMap();
    const scoreIds: Record<string, string> = {};
    const originalScores: Record<string, string> = {};
    const caScores: Record<string, Record<string, string>> = blankCaScoreMap(components);
    const existingSubjectResults: Record<string, StudentSubjectResult> = {};
    const cached = scorePreviewCache[scoreCacheKey(subjectId)];

    if (cached?.examScores) {
      Object.entries(cached.examScores).forEach(([studentId, score]) => {
        nextScores[studentId] = score;
        originalScores[studentId] = score;
      });
    }
    if (cached?.caScores) {
      Object.entries(cached.caScores).forEach(([studentId, studentScores]) => {
        caScores[studentId] = {
          ...(caScores[studentId] ?? {}),
          ...studentScores,
        };
      });
    }

    if (!currentTerm?.termId || !subjectId || students.length === 0) {
      return { scores: nextScores, scoreIds, originalScores, caScores, existingSubjectResults };
    }

    const results = await Promise.allSettled(
      students.map((student) => teacherService.getStudentResult(student.studentId, currentTerm.termId)),
    );

    results.forEach((result, index) => {
      if (result.status !== 'fulfilled') return;
      const student = students[index];
      const subject = result.value.subjects?.find((item) => {
        const sameSubject = item.subjectId === subjectId;
        const sameExam = !item.examId || !examId || item.examId === examId;
        return sameSubject && sameExam;
      });
      if (!student || !subject) return;

      existingSubjectResults[student.studentId] = subject;

      subject.caScores?.forEach((ca) => {
        const component = components.find((comp) => comp.name.trim().toLowerCase() === ca.component.trim().toLowerCase());
        if (!component || ca.score === undefined || ca.score === null) return;
        caScores[student.studentId] = {
          ...(caScores[student.studentId] ?? {}),
          [component.id]: String(ca.score),
        };
      });

      if (subject.examScore === undefined || subject.examScore === null) return;

      const scoreValue = String(subject.examScore);
      nextScores[student.studentId] = scoreValue;
      originalScores[student.studentId] = scoreValue;
      if (subject.scoreId) {
        scoreIds[student.studentId] = subject.scoreId;
      }
    });

    return { scores: nextScores, scoreIds, originalScores, caScores, existingSubjectResults };
  };

  useEffect(() => {
    void loadDashboard();
  }, []);

  useEffect(() => {
    if (!selectedClassId) return;
    void loadClass(selectedClassId);
    void loadTodaySessions(selectedClassId);
  }, [selectedClassId]);

  const loadTodaySessions = async (classId: string) => {
    if (!classId) return;
    const todayStr = new Date().toISOString().split('T')[0];
    try {
      const result = await attendanceService.getSessions(classId, todayStr);
      setTodaySessions((prev) => ({ ...prev, [classId]: result }));
    } catch (err) {
      console.error('Failed to load today sessions', err);
    }
  };

  const startOrOpenSession = async (sessionType: 'MORNING_ARRIVAL' | 'AFTERNOON_DEPARTURE', existingSession?: AttendanceSessionResponse) => {
    if (!selectedClassId || !currentTerm?.termId) {
      setError('Please select a class and ensure there is a current academic term.');
      return;
    }
    setIsAttendanceLoading(true);
    setError(null);
    try {
      let session = existingSession;
      const todayStr = new Date().toISOString().split('T')[0];
      if (!session) {
        session = await attendanceService.createSession({
          classId: selectedClassId,
          termId: currentTerm.termId,
          date: todayStr,
          sessionType,
        });
        await loadTodaySessions(selectedClassId);
      }

      setSelectedSession(session);

      const marksMap: Record<string, any> = {};
      const now = new Date();
      const currentHHmm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;

      if (session.isComplete) {
        const existingMarks = await attendanceService.getSessionMarks(session.sessionId);
        existingMarks.forEach((m) => {
          marksMap[m.studentId] = {
            status: m.status,
            arrivalTime: m.arrivalTime ? String(m.arrivalTime).substring(0, 5) : undefined,
            broughtBy: m.broughtBy,
            departureTime: m.departureTime ? String(m.departureTime).substring(0, 5) : undefined,
            pickedUpBy: m.pickedUpBy,
            pickUpPersonName: m.pickUpPersonName,
            pickUpPersonPhone: m.pickUpPersonPhone,
            notes: m.notes,
            markId: m.attendanceId,
          };
        });
      } else {
        students.forEach((s) => {
          marksMap[s.studentId] = {
            status: 'PRESENT',
            arrivalTime: sessionType === 'MORNING_ARRIVAL' ? currentHHmm : undefined,
            broughtBy: sessionType === 'MORNING_ARRIVAL' ? 'Mother' : undefined,
            departureTime: sessionType === 'AFTERNOON_DEPARTURE' ? currentHHmm : undefined,
            pickedUpBy: sessionType === 'AFTERNOON_DEPARTURE' ? 'Mother' : undefined,
            pickUpPersonName: '',
            pickUpPersonPhone: '',
            notes: '',
          };
        });
      }

      setAttendanceMarks(marksMap);
      setEditingStudentId(null);
      setActiveSection('attendance');
    } catch (err) {
      setError(readError(err, 'Failed to start or load attendance session.'));
    } finally {
      setIsAttendanceLoading(false);
    }
  };

  const submitAttendance = async () => {
    if (!selectedSession) return;
    setIsSaving(true);
    setError(null);
    try {
      const payloadMarks = students.map((s) => {
        const formMark = attendanceMarks[s.studentId] || { status: 'PRESENT' };
        const status = formMark.status === 'NOT_MARKED' ? 'PRESENT' : formMark.status;
        return {
          studentId: s.studentId,
          status,
          arrivalTime: status === 'PRESENT' || status === 'LATE' ? formMark.arrivalTime : undefined,
          broughtBy: status === 'PRESENT' || status === 'LATE' ? formMark.broughtBy : undefined,
          departureTime: status === 'PRESENT' ? formMark.departureTime : undefined,
          pickedUpBy: status === 'PRESENT' ? formMark.pickedUpBy : undefined,
          pickUpPersonName: status === 'PRESENT' && formMark.pickedUpBy === 'Other' ? formMark.pickUpPersonName : (status === 'PRESENT' ? formMark.pickedUpBy : undefined),
          pickUpPersonPhone: status === 'PRESENT' ? formMark.pickUpPersonPhone : undefined,
          notes: formMark.notes,
        };
      });

      await attendanceService.markAttendance(selectedSession.sessionId, { marks: payloadMarks });
      setNotice('Attendance saved successfully.');
      await loadTodaySessions(selectedClassId);
      setSelectedSession(null);
      setActiveSection('overview');
    } catch (err) {
      setError(readError(err, 'Failed to submit attendance marks.'));
    } finally {
      setIsSaving(false);
    }
  };

  const updateSingleAttendanceMark = async (studentId: string) => {
    const formMark = attendanceMarks[studentId];
    if (!formMark || !formMark.markId || formMark.status === 'NOT_MARKED') return;
    setIsSaving(true);
    setError(null);
    try {
      const status = formMark.status;
      const payload = {
        status,
        arrivalTime: status === 'PRESENT' || status === 'LATE' ? formMark.arrivalTime : undefined,
        broughtBy: status === 'PRESENT' || status === 'LATE' ? formMark.broughtBy : undefined,
        departureTime: status === 'PRESENT' ? formMark.departureTime : undefined,
        pickedUpBy: status === 'PRESENT' ? formMark.pickedUpBy : undefined,
        pickUpPersonName: status === 'PRESENT' && formMark.pickedUpBy === 'Other' ? formMark.pickUpPersonName : (status === 'PRESENT' ? formMark.pickedUpBy : undefined),
        pickUpPersonPhone: status === 'PRESENT' ? formMark.pickUpPersonPhone : undefined,
        notes: formMark.notes,
      };
      await attendanceService.updateMark(formMark.markId, payload);
      setNotice('Attendance record updated successfully.');
      setEditingStudentId(null);
      await loadTodaySessions(selectedClassId);
      if (selectedSession) {
        await startOrOpenSession(selectedSession.sessionType, selectedSession);
      }
    } catch (err) {
      setError(readError(err, 'Failed to update attendance record.'));
    } finally {
      setIsSaving(false);
    }
  };

  const loadDashboard = async () => {
    setIsLoading(true);
    setError(null);
    const [sessionsResult, classesResult, caResult, gradingResult] = await Promise.allSettled([
      teacherService.getSessions(),
      teacherService.listClasses(),
      teacherService.getCaConfig(),
      teacherService.getGradingRules(),
    ]);

    if (sessionsResult.status === 'fulfilled') setSessions(sessionsResult.value);
    if (classesResult.status === 'fulfilled') {
      setClasses(classesResult.value);
      const directlyAssignedClass = classesResult.value.find((item) => item.classTeacher?.userId === user?.userId);
      const activeClassId = selectedClassId || directlyAssignedClass?.classId || classesResult.value[0]?.classId || '';
      setSelectedClassId(activeClassId);
      if (activeClassId) {
        void loadTodaySessions(activeClassId);
      }
    }
    if (caResult.status === 'fulfilled') setCaConfig(caResult.value);
    if (gradingResult.status === 'fulfilled') setGradingRules(gradingResult.value);

    if ([sessionsResult, classesResult, caResult, gradingResult].some((result) => result.status === 'rejected')) {
      setNotice('Some teacher data could not refresh. Available classroom data is shown.');
    }
    setIsLoading(false);
  };

  const loadClass = async (classId: string) => {
    setIsClassLoading(true);
    setError(null);
    const [detailResult, resultSheetResult, traitsResult, traitValuesResult] = await Promise.allSettled([
      teacherService.getClassDetails(classId),
      currentTerm?.termId ? teacherService.getClassResultSheet(classId, currentTerm.termId) : Promise.resolve(null),
      teacherService.getAssessmentTraits(classId),
      currentTerm?.termId ? teacherService.getTraitAssessmentValues(classId, currentTerm.termId) : Promise.resolve([]),
    ]);

    if (detailResult.status === 'fulfilled') {
      setClassDetail(detailResult.value);
      setScoreForm((form) => ({
        ...form,
        scores: Object.fromEntries(detailResult.value.students.map((student) => [student.studentId, ''])),
        scoreIds: {},
        originalScores: {},
        existingSubjectResults: {},
      }));
    }
    if (resultSheetResult.status === 'fulfilled') setResultSheet(resultSheetResult.value);
    if (traitsResult.status === 'fulfilled') setAssessmentTraits(traitsResult.value);
    if (traitValuesResult.status === 'fulfilled') {
      const nextRatings: Record<string, Record<string, { rating: string; comment: string }>> = {};
      traitValuesResult.value.forEach((value) => {
        nextRatings[value.studentId] = {
          ...(nextRatings[value.studentId] ?? {}),
          [value.traitId]: {
            rating: value.rating ?? '',
            comment: value.comment ?? '',
          },
        };
      });
      setTraitRatings(nextRatings);
    }
    if (detailResult.status === 'rejected') setError(readError(detailResult.reason, 'Unable to load class details.'));
    setIsClassLoading(false);
  };

  const handleScoreSubjectChange = async (subjectId: string) => {
    setScoreForm((prev) => ({
      ...prev,
      subjectId,
      scores: blankScoreMap(),
      scoreIds: {},
      originalScores: {},
      caScores: scoreForm.mode === 'ca' ? blankCaScoreMap() : prev.caScores,
      existingSubjectResults: {},
    }));

    setIsLookupLoading(true);
    try {
      const existing = await loadExistingSubjectScores(
        subjectId,
        scoreForm.mode === 'exam' ? scoreForm.assessmentId : '',
      );
      setScoreForm((prev) => ({
        ...prev,
        subjectId,
        scores: existing.scores,
        scoreIds: existing.scoreIds,
        originalScores: existing.originalScores,
        caScores: scoreForm.mode === 'ca' ? existing.caScores : prev.caScores,
        existingSubjectResults: existing.existingSubjectResults,
      }));
    } catch (err) {
      setError(readError(err, 'Unable to load existing scores for this subject.'));
    } finally {
      setIsLookupLoading(false);
    }
  };

  const handleAssessmentChange = async (id: string, mode: ScoreMode) => {
    let maxScore = mode === 'ca' ? '20' : '100';
    if (mode === 'ca') {
      const comp = lookupComponents.find((c) => c.id === id);
      if (comp) maxScore = comp.maxScore.toString();
    } else {
      const exam = lookupExams.find((e) => e.id === id);
      if (exam) maxScore = exam.maxScore.toString();
    }
    setScoreForm((prev) => ({
      ...prev,
      assessmentId: id,
      maxScore,
      ...(mode === 'exam' ? { scores: blankScoreMap(), scoreIds: {}, originalScores: {}, existingSubjectResults: {} } : {}),
    }));

    if (mode !== 'exam') return;

    setIsLookupLoading(true);
    try {
      const existing = await loadExistingSubjectScores(scoreForm.subjectId, id);
      setScoreForm((prev) => ({
        ...prev,
        assessmentId: id,
        maxScore,
        scores: existing.scores,
        scoreIds: existing.scoreIds,
        originalScores: existing.originalScores,
        caScores: existing.caScores,
        existingSubjectResults: existing.existingSubjectResults,
      }));
    } catch (err) {
      setError(readError(err, 'Unable to load existing exam scores for this exam.'));
    } finally {
      setIsLookupLoading(false);
    }
  };

  const openScoreDialog = async (mode: ScoreMode) => {
    if (!selectedClassId) {
      setError('Please select a class first.');
      return;
    }
    setIsLookupLoading(true);
    try {
      const classSubjects = await teacherService.getSubjectsForClass(selectedClassId);
      setLookupSubjects(classSubjects);

      const defaultSubjectId = classSubjects[0]?.id ?? '';
      let defaultAssessmentId = '';
      let defaultMaxScore = mode === 'ca' ? '20' : '100';
      let loadedComponents = lookupComponents;

      if (mode === 'ca') {
        const [comps, termExams] = await Promise.all([
          teacherService.getCaComponents(),
          currentTerm?.termId ? teacherService.getExamsForTerm(currentTerm.termId) : Promise.resolve([]),
        ]);
        loadedComponents = comps;
        setLookupComponents(comps);
        setLookupExams(termExams);
        defaultAssessmentId = comps[0]?.id ?? '';
        defaultMaxScore = comps[0]?.maxScore?.toString() ?? '20';
      } else if (mode === 'exam' && currentTerm?.termId) {
        const [termExams, comps] = await Promise.all([
          teacherService.getExamsForTerm(currentTerm.termId),
          teacherService.getCaComponents(),
        ]);
        loadedComponents = comps;
        setLookupComponents(comps);
        setLookupExams(termExams);
        defaultAssessmentId = termExams[0]?.id ?? '';
        defaultMaxScore = termExams[0]?.maxScore?.toString() ?? '100';
      }

      const existingScores = await loadExistingSubjectScores(
        defaultSubjectId,
        mode === 'exam' ? defaultAssessmentId : '',
        loadedComponents,
      );

      setScoreForm({
        mode,
        subjectId: defaultSubjectId,
        assessmentId: defaultAssessmentId,
        maxScore: defaultMaxScore,
        scores: existingScores.scores,
        scoreIds: existingScores.scoreIds,
        originalScores: existingScores.originalScores,
        caScores: existingScores.caScores,
        existingSubjectResults: existingScores.existingSubjectResults,
        studentIdFilter: 'ALL',
      });
      setScoreDialog(mode);
    } catch (err) {
      setError(readError(err, 'Unable to load subjects or assessment configurations.'));
    } finally {
      setIsLookupLoading(false);
    }
  };

  const submitScores = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedClassId || !currentTerm?.termId) {
      setError('Select a class with a current term before saving scores.');
      return;
    }

    if (scoreForm.mode === 'ca') {
      const componentScoresMap: Record<string, Array<{ studentId: string; score: number }>> = {};
      lookupComponents.forEach((comp) => {
        const compScores: Array<{ studentId: string; score: number }> = [];
        students.forEach((student) => {
          const rawScore = scoreForm.caScores?.[student.studentId]?.[comp.id];
          if (rawScore !== undefined && rawScore.trim() !== '') {
            compScores.push({
              studentId: student.studentId,
              score: Number(rawScore),
            });
          }
        });
        if (compScores.length > 0) {
          componentScoresMap[comp.id] = compScores;
        }
      });

      const componentIdsToSave = Object.keys(componentScoresMap);
      if (componentIdsToSave.length === 0) {
        setError('Enter at least one score.');
        return;
      }

      await runAction(async () => {
        for (const compId of componentIdsToSave) {
          const comp = lookupComponents.find((c) => c.id === compId);
          const maxScore = comp?.maxScore ?? 20;
          await teacherService.enterCaScores({
            termId: currentTerm.termId,
            classId: selectedClassId,
            subjectId: scoreForm.subjectId.trim(),
            caComponentId: compId,
            maxScore: maxScore,
            scores: componentScoresMap[compId],
          });
        }
        setScorePreviewCache((prev) => ({
          ...prev,
          [scoreCacheKey(scoreForm.subjectId.trim())]: {
            ...(prev[scoreCacheKey(scoreForm.subjectId.trim())] ?? {}),
            caScores: scoreForm.caScores ?? {},
          },
        }));
        setScoreDialog(null);
        setNotice('CA scores saved.');
        await loadClass(selectedClassId);
      });
    } else {
      const entries = Object.entries(scoreForm.scores)
        .filter(([, score]) => score.trim() !== '')
        .map(([studentId, score]) => ({ studentId, score: Number(score) }));

      if (!entries.length) {
        setError('Enter at least one score.');
        return;
      }

      await runAction(async () => {
        const newEntries = entries.filter((entry) => !scoreForm.scoreIds?.[entry.studentId]);
        const changedExistingEntries = entries.filter((entry) => {
          const scoreId = scoreForm.scoreIds?.[entry.studentId];
          if (!scoreId) return false;
          const originalScore = scoreForm.originalScores?.[entry.studentId];
          return originalScore === undefined || Number(originalScore) !== entry.score;
        });

        if (!newEntries.length && !changedExistingEntries.length) {
          setNotice('No score changes to save.');
          return;
        }

        if (newEntries.length) {
          await teacherService.enterExamScores({
            termId: currentTerm.termId,
            classId: selectedClassId,
            subjectId: scoreForm.subjectId.trim(),
            examId: scoreForm.assessmentId.trim(),
            maxScore: Number(scoreForm.maxScore),
            scores: newEntries,
          });
        }

        for (const entry of changedExistingEntries) {
          const scoreId = scoreForm.scoreIds?.[entry.studentId];
          if (!scoreId) continue;
          await teacherService.updateScore(scoreId, {
            score: entry.score,
            reason: 'Updated from teacher score entry dashboard',
          });
        }

        setScorePreviewCache((prev) => ({
          ...prev,
          [scoreCacheKey(scoreForm.subjectId.trim())]: {
            ...(prev[scoreCacheKey(scoreForm.subjectId.trim())] ?? {}),
            caScores: scoreForm.caScores,
            examScores: Object.fromEntries(entries.map((entry) => [entry.studentId, String(entry.score)])),
          },
        }));
        setScoreDialog(null);
        setNotice(changedExistingEntries.length && newEntries.length
          ? 'Exam scores created and updated. Final scores recomputed.'
          : changedExistingEntries.length
            ? 'Exam scores updated and final scores recomputed.'
            : 'Exam scores saved and final scores recomputed.');
        await loadClass(selectedClassId);
      });
    }
  };

  const saveComment = async (studentId = commentStudentId, autoGenerate = false) => {
    if (!studentId || !currentTerm?.termId) {
      setError('Choose a student and current term before saving a comment.');
      return;
    }
    await runAction(async () => {
      await teacherService.saveTeacherComment(studentId, currentTerm.termId, autoGenerate ? '' : commentText.trim(), autoGenerate);
      setNotice(autoGenerate ? 'Teacher comment auto-generated from result.' : 'Teacher comment saved.');
      setCommentStudentId(studentId);
      if (autoGenerate) setCommentText('');
    });
  };

  const saveTraitAssessments = async () => {
    if (!selectedClassId || !currentTerm?.termId) {
      setError('Select a class with a current term before saving assessments.');
      return;
    }
    const entries = Object.entries(traitRatings).flatMap(([studentId, ratings]) =>
      Object.entries(ratings)
        .filter(([, value]) => value.rating.trim() !== '')
        .map(([traitId, value]) => ({
          studentId,
          traitId,
          rating: value.rating.trim(),
          comment: value.comment.trim() || undefined,
        })),
    );
    if (!entries.length) {
      setError('Enter at least one behavioural or psychomotor rating.');
      return;
    }
    await runAction(async () => {
      await teacherService.saveTraitAssessments({
        classId: selectedClassId,
        termId: currentTerm.termId,
        entries,
      });
      setNotice('Behavioural and psychomotor assessments saved.');
      await loadClass(selectedClassId);
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

  const selectedStudent = students.find((student) => student.studentId === commentStudentId) ?? students[0];

  useEffect(() => {
    if (!commentStudentId && selectedStudent) {
      setCommentStudentId(selectedStudent.studentId);
    }
  }, [commentStudentId, selectedStudent]);

  return (
    <div className="min-h-screen bg-slate-100 text-slate-950">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-72 border-r border-slate-200 bg-white lg:flex lg:flex-col">
        <div className="px-6 py-6">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-md bg-slate-950 text-white">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-slate-950">{user?.schoolName ?? 'SchoolFee'}</p>
              <p className="text-xs text-slate-500">Teacher Workspace</p>
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
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="outline" className="rounded-full border-slate-300 bg-white text-slate-700">TEACHER</Badge>
                {currentTerm && <Badge className="rounded-full bg-blue-100 text-blue-700 hover:bg-blue-100">{currentTerm.name}</Badge>}
              </div>
              <h1 className="mt-2 text-2xl font-semibold tracking-tight text-slate-950 md:text-3xl">
                Welcome, {[user?.firstName, user?.lastName].filter(Boolean).join(' ') || 'Teacher'}
              </h1>
              <p className="mt-1 text-sm text-slate-500">
                {currentSession ? `${currentSession.name} · ${assignedClasses.length} class${assignedClasses.length === 1 ? '' : 'es'}` : 'Classroom scores and report comments'}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Select value={activeSection} onValueChange={(value) => setActiveSection(value as Section)}>
                <SelectTrigger className="w-full bg-white lg:hidden">
                  <SelectValue placeholder="Workspace section" />
                </SelectTrigger>
                <SelectContent>
                  {sections.map((section) => (
                    <SelectItem key={section.id} value={section.id}>{section.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select value={selectedClassId} onValueChange={setSelectedClassId}>
                <SelectTrigger className="w-full bg-white md:w-64">
                  <SelectValue placeholder="Select class" />
                </SelectTrigger>
                <SelectContent>
                  {assignedClasses.map((item) => (
                    <SelectItem key={item.classId} value={item.classId}>{item.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button variant="outline" onClick={() => void loadDashboard()} disabled={isLoading || isSaving || isLookupLoading}>
                {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
                Refresh
              </Button>
            </div>
          </div>
        </header>

        <div className="px-4 py-6 md:px-8">
          {error && <Notice tone="error" message={error} />}
          {notice && <Notice tone="info" message={notice} />}

          {isLoading ? (
            <div className="flex min-h-[420px] items-center justify-center rounded-md border border-dashed border-slate-300 bg-white">
              <div className="text-center">
                <Loader2 className="mx-auto h-8 w-8 animate-spin text-slate-500" />
                <p className="mt-3 text-sm text-slate-500">Loading teacher workspace...</p>
              </div>
            </div>
          ) : (
            <>
              {activeSection === 'overview' && (
                <OverviewSection
                  classes={assignedClasses}
                  selectedClass={classDetail}
                  studentsCount={students.length}
                  feePaidCount={feePaidCount}
                  pendingFeesCount={pendingFeesCount}
                  caConfig={caConfig}
                  resultRows={resultRows.length}
                  todaySessions={todaySessions}
                  isAttendanceLoading={isAttendanceLoading}
                  onSelectClass={(classId) => { setSelectedClassId(classId); }}
                  onGoTo={setActiveSection}
                  onOpenScores={openScoreDialog}
                  onStartSession={(classId, type, existing) => { setSelectedClassId(classId); void startOrOpenSession(type, existing); }}
                />
              )}

              {activeSection === 'classes' && (
                <ClassesSection
                  classes={assignedClasses}
                  selectedClassId={selectedClassId}
                  classDetail={classDetail}
                  visibleStudents={visibleStudents}
                  studentSearch={studentSearch}
                  isClassLoading={isClassLoading}
                  onSearch={setStudentSearch}
                  onSelectClass={setSelectedClassId}
                />
              )}

              {activeSection === 'attendance' && (
                <AttendanceSection
                  session={selectedSession}
                  students={students}
                  marks={attendanceMarks}
                  isSaving={isSaving}
                  isLoading={isAttendanceLoading}
                  editingStudentId={editingStudentId}
                  onMarksChange={setAttendanceMarks}
                  onEditStudent={setEditingStudentId}
                  onSubmit={() => void submitAttendance()}
                  onUpdateMark={(studentId) => void updateSingleAttendanceMark(studentId)}
                  onCancel={() => { setSelectedSession(null); setActiveSection('overview'); }}
                />
              )}

              {activeSection === 'scores' && (
                <ScoresSection
                  classDetail={classDetail}
                  caConfig={caConfig}
                  gradingRules={gradingRules}
                  onOpenScores={openScoreDialog}
                />
              )}

              {activeSection === 'results' && (
                <ResultsSection
                  resultSheet={resultSheet}
                  classDetail={classDetail}
                  currentTermName={currentTerm?.name}
                />
              )}

              {activeSection === 'comments' && (
                <CommentsSection
                  students={students}
                  selectedStudentId={commentStudentId}
                  commentText={commentText}
                  isSaving={isSaving}
                  onSelectStudent={(studentId) => {
                    setCommentStudentId(studentId);
                    setCommentText('');
                  }}
                  onCommentChange={setCommentText}
                  onSave={() => void saveComment()}
                  onAutoGenerate={() => void saveComment(commentStudentId, true)}
                />
              )}

              {activeSection === 'assessments' && (
                <AssessmentsSection
                  students={students}
                  traits={assessmentTraits}
                  ratings={traitRatings}
                  isSaving={isSaving}
                  onRatingsChange={setTraitRatings}
                  onSave={() => void saveTraitAssessments()}
                />
              )}

              {activeSection === 'reference' && (
                <ReferenceSection
                  classDetail={classDetail}
                  caConfig={caConfig}
                  gradingRules={gradingRules}
                />
              )}
            </>
          )}
        </div>
      </main>

      <ScoreDialog
        open={scoreDialog !== null}
        mode={scoreDialog ?? 'ca'}
        form={scoreForm}
        students={students}
        isSaving={isSaving}
        caConfig={caConfig}
        gradingRules={gradingRules}
        lookupSubjects={lookupSubjects}
        lookupComponents={lookupComponents}
        lookupExams={lookupExams}
        onOpenChange={(open) => setScoreDialog(open ? scoreForm.mode : null)}
        onChange={setScoreForm}
        onSubjectChange={handleScoreSubjectChange}
        onAssessmentChange={handleAssessmentChange}
        onSubmit={submitScores}
      />
    </div>
  );
};

function OverviewSection({
  classes,
  selectedClass,
  studentsCount,
  feePaidCount,
  pendingFeesCount,
  caConfig,
  resultRows,
  todaySessions,
  isAttendanceLoading,
  onSelectClass,
  onGoTo,
  onOpenScores,
  onStartSession,
}: {
  classes: ClassRoom[];
  selectedClass: ClassDetail | null;
  studentsCount: number;
  feePaidCount: number;
  pendingFeesCount: number;
  caConfig: CaConfig | null;
  resultRows: number;
  todaySessions: Record<string, AttendanceSessionResponse[]>;
  isAttendanceLoading: boolean;
  onSelectClass: (classId: string) => void;
  onGoTo: (section: Section) => void;
  onOpenScores: (mode: ScoreMode) => void;
  onStartSession: (classId: string, type: 'MORNING_ARRIVAL' | 'AFTERNOON_DEPARTURE', existing?: AttendanceSessionResponse) => void;
}) {
  const feeRate = studentsCount ? Math.round((feePaidCount / studentsCount) * 100) : 0;
  const todayStr = new Date().toLocaleDateString('en-NG', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });

  return (
    <div className="space-y-6">
      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <Metric icon={GraduationCap} label="Classes" value={formatNumber(classes.length)} detail="Current session assignments" />
        <Metric icon={Users} label="Students" value={formatNumber(studentsCount)} detail={selectedClass?.name ?? 'Selected class'} />
        <Metric icon={ClipboardList} label="CA Components" value={formatNumber(caConfig?.componentCount ?? 0)} detail={`${caConfig?.examWeightPercentage ?? 0}% exam weight`} />
        <Metric icon={Banknote} label="Fee Cleared" value={`${feeRate}%`} detail={`${formatNumber(pendingFeesCount)} pending in class`} />
      </section>

      {/* ── Attendance Cards ─────────────────────────────────────────── */}
      <section className="rounded-md border border-slate-200 bg-white p-5">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <UserCheck className="h-5 w-5 text-blue-600" />
              <p className="text-sm font-medium text-slate-500">Today's Attendance</p>
            </div>
            <p className="mt-1 text-xs text-slate-400">{todayStr}</p>
          </div>
          <Button variant="outline" size="sm" onClick={() => onGoTo('attendance')} className="gap-1.5 text-xs">
            <Calendar className="h-3.5 w-3.5" />
            View Attendance
          </Button>
        </div>

        <div className="mt-5 grid gap-4">
          {classes.map((item) => {
            const classSessions = todaySessions[item.classId] ?? [];
            const morning = classSessions.find((s) => s.sessionType === 'MORNING_ARRIVAL');
            const afternoon = classSessions.find((s) => s.sessionType === 'AFTERNOON_DEPARTURE');

            return (
              <div
                key={item.classId}
                className={`rounded-lg border p-4 transition ${
                  selectedClass?.classId === item.classId ? 'border-blue-300 bg-blue-50/40' : 'border-slate-200 bg-white hover:border-slate-300'
                }`}
              >
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <button type="button" onClick={() => onSelectClass(item.classId)} className="text-left">
                    <p className="font-semibold text-slate-950">{item.name}</p>
                    <p className="mt-0.5 text-sm text-slate-500">{formatGradeCode(item.gradeLevel)} · {item.currentEnrollment} students</p>
                  </button>
                </div>

                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  {/* Morning Arrival */}
                  <div className="flex items-center justify-between rounded-md border border-slate-200 bg-slate-50/70 px-3 py-2.5">
                    <div className="flex items-center gap-2">
                      <div className={`flex h-7 w-7 items-center justify-center rounded-full ${
                        morning?.isComplete ? 'bg-emerald-100 text-emerald-600' : 'bg-amber-100 text-amber-600'
                      }`}>
                        {morning?.isComplete ? <CheckCircle2 className="h-3.5 w-3.5" /> : <Clock className="h-3.5 w-3.5" />}
                      </div>
                      <div>
                        <p className="text-xs font-medium text-slate-700">Morning Arrival</p>
                        {morning?.isComplete ? (
                          <p className="text-[11px] text-slate-500">
                            {morning.presentCount}P · {morning.absentCount}A · {morning.lateCount}L
                          </p>
                        ) : (
                          <p className="text-[11px] text-slate-400">7:30 – 8:30 AM</p>
                        )}
                      </div>
                    </div>
                    <Button
                      size="sm"
                      variant={morning?.isComplete ? 'outline' : 'default'}
                      disabled={isAttendanceLoading}
                      onClick={(e) => { e.stopPropagation(); onStartSession(item.classId, 'MORNING_ARRIVAL', morning ?? undefined); }}
                      className={`h-8 gap-1 text-xs ${
                        morning?.isComplete
                          ? 'border-slate-200 text-slate-700 hover:bg-slate-100'
                          : 'bg-slate-950 text-white hover:bg-slate-800'
                      }`}
                    >
                      {morning?.isComplete ? 'Review' : 'Mark Attendance'}
                      <ArrowRight className="h-3 w-3" />
                    </Button>
                  </div>

                  {/* Afternoon Departure */}
                  <div className="flex items-center justify-between rounded-md border border-slate-200 bg-slate-50/70 px-3 py-2.5">
                    <div className="flex items-center gap-2">
                      <div className={`flex h-7 w-7 items-center justify-center rounded-full ${
                        afternoon?.isComplete ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100 text-slate-400'
                      }`}>
                        {afternoon?.isComplete ? <CheckCircle2 className="h-3.5 w-3.5" /> : <Clock className="h-3.5 w-3.5" />}
                      </div>
                      <div>
                        <p className="text-xs font-medium text-slate-700">Afternoon Departure</p>
                        {afternoon?.isComplete ? (
                          <p className="text-[11px] text-slate-500">
                            {afternoon.presentCount} picked up · {afternoon.absentCount} absent
                          </p>
                        ) : (
                          <p className="text-[11px] text-slate-400">1:30 – 2:30 PM</p>
                        )}
                      </div>
                    </div>
                    <Button
                      size="sm"
                      variant={afternoon?.isComplete ? 'outline' : 'default'}
                      disabled={isAttendanceLoading}
                      onClick={(e) => { e.stopPropagation(); onStartSession(item.classId, 'AFTERNOON_DEPARTURE', afternoon ?? undefined); }}
                      className={`h-8 gap-1 text-xs ${
                        afternoon?.isComplete
                          ? 'border-slate-200 text-slate-700 hover:bg-slate-100'
                          : 'bg-slate-950 text-white hover:bg-slate-800'
                      }`}
                    >
                      {afternoon?.isComplete ? 'Review' : 'Mark Attendance'}
                      <ArrowRight className="h-3 w-3" />
                    </Button>
                  </div>
                </div>
              </div>
            );
          })}
          {!classes.length && <EmptyPanel message="No classes assigned yet." />}
        </div>
      </section>

      <section className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <div className="rounded-md border border-slate-200 bg-white p-5">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">My Classes</p>
              <h2 className="mt-1 text-xl font-semibold text-slate-950">Score entry queue</h2>
            </div>
            <Button onClick={() => onGoTo('scores')} className="bg-slate-950 text-white hover:bg-slate-800">
              Open Scores
            </Button>
          </div>
          <div className="mt-5 grid gap-3">
            {classes.map((item) => (
              <button
                key={item.classId}
                type="button"
                onClick={() => onSelectClass(item.classId)}
                className={`rounded-md border p-4 text-left transition hover:border-slate-400 ${
                  selectedClass?.classId === item.classId ? 'border-slate-950 bg-slate-50' : 'border-slate-200 bg-white'
                }`}
              >
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                  <div>
                    <p className="font-semibold text-slate-950">{item.name}</p>
                    <p className="mt-1 text-sm text-slate-500">{formatGradeCode(item.gradeLevel)} · {item.currentEnrollment} students</p>
                  </div>
                  <div className="flex gap-2">
                    <Badge variant="outline" className="border-slate-200">CA</Badge>
                    <Badge variant="outline" className="border-slate-200">Exam</Badge>
                    {resultRows > 0 && selectedClass?.classId === item.classId && <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">Results</Badge>}
                  </div>
                </div>
              </button>
            ))}
            {!classes.length && <EmptyPanel message="No classes assigned yet." />}
          </div>
        </div>

        <div className="rounded-md border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">Pending Actions</p>
          <div className="mt-4 space-y-3">
            <ActionRow label="Enter CA scores" detail={selectedClass?.name ?? 'Select a class'} onClick={() => onOpenScores('ca')} />
            <ActionRow label="Enter exam scores" detail="Final scores and rankings are computed after save" onClick={() => onOpenScores('exam')} />
            <ActionRow label="Add comments" detail={`${formatNumber(studentsCount)} report cards`} onClick={() => onGoTo('comments')} />
            <ActionRow label="Review results" detail={`${formatNumber(resultRows)} result rows loaded`} onClick={() => onGoTo('results')} />
          </div>
        </div>
      </section>
    </div>
  );
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Attendance Section — full student marking grid
 * ───────────────────────────────────────────────────────────────────────────── */

const BROUGHT_BY_OPTIONS = ['Mother', 'Father', 'Guardian', 'Driver', 'School Bus', 'Self', 'Other'];
const PICKED_UP_OPTIONS  = ['Mother', 'Father', 'Guardian', 'Driver', 'School Bus', 'Self', 'Other'];
const MORNING_STATUS_OPTIONS: Array<{ value: string; label: string; color: string }> = [
  { value: 'PRESENT', label: 'Present', color: 'bg-emerald-100 text-emerald-700' },
  { value: 'ABSENT',  label: 'Absent',  color: 'bg-red-100 text-red-700' },
  { value: 'LATE',    label: 'Late',    color: 'bg-amber-100 text-amber-700' },
  { value: 'EXCUSED', label: 'Excused', color: 'bg-blue-100 text-blue-700' },
];
const AFTERNOON_STATUS_OPTIONS: Array<{ value: string; label: string; color: string }> = [
  { value: 'PRESENT', label: 'Picked Up', color: 'bg-emerald-100 text-emerald-700' },
  { value: 'ABSENT',  label: 'Absent',    color: 'bg-red-100 text-red-700' },
  { value: 'EXCUSED', label: 'Excused',   color: 'bg-blue-100 text-blue-700' },
];

function AttendanceSection({
  session,
  students,
  marks,
  isSaving,
  isLoading,
  editingStudentId,
  onMarksChange,
  onEditStudent,
  onSubmit,
  onUpdateMark,
  onCancel,
}: {
  session: AttendanceSessionResponse | null;
  students: ClassStudent[];
  marks: Record<string, {
    status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED' | 'NOT_MARKED';
    arrivalTime?: string;
    broughtBy?: string;
    departureTime?: string;
    pickedUpBy?: string;
    pickUpPersonName?: string;
    pickUpPersonPhone?: string;
    notes?: string;
    markId?: string;
  }>;
  isSaving: boolean;
  isLoading: boolean;
  editingStudentId: string | null;
  onMarksChange: (marks: Record<string, any>) => void;
  onEditStudent: (studentId: string | null) => void;
  onSubmit: () => void;
  onUpdateMark: (studentId: string) => void;
  onCancel: () => void;
}) {
  if (!session) {
    return (
      <div className="flex min-h-[360px] items-center justify-center rounded-md border border-dashed border-slate-300 bg-white">
        <div className="text-center">
          <UserCheck className="mx-auto h-10 w-10 text-slate-300" />
          <p className="mt-3 text-sm text-slate-500">Select a class and session from the Overview to start marking attendance.</p>
          <Button variant="outline" className="mt-4" onClick={onCancel}>Go to Overview</Button>
        </div>
      </div>
    );
  }

  const isMorning = session.sessionType === 'MORNING_ARRIVAL';
  const statusOptions = isMorning ? MORNING_STATUS_OPTIONS : AFTERNOON_STATUS_OPTIONS;
  const sessionLabel = isMorning ? 'Morning Arrival' : 'Afternoon Departure';
  const dateStr = new Date(session.date + 'T00:00:00').toLocaleDateString('en-NG', { weekday: 'short', year: 'numeric', month: 'long', day: 'numeric' });

  const presentCount = Object.values(marks).filter((m) => m.status === 'PRESENT').length;
  const absentCount  = Object.values(marks).filter((m) => m.status === 'ABSENT').length;
  const lateCount    = Object.values(marks).filter((m) => m.status === 'LATE').length;
  const excusedCount = Object.values(marks).filter((m) => m.status === 'EXCUSED').length;

  const updateMark = (studentId: string, field: string, value: string) => {
    const updated = { ...marks };
    updated[studentId] = { ...updated[studentId], [field]: value };
    // If status changes to ABSENT, clear time/person fields
    if (field === 'status' && value === 'ABSENT') {
      updated[studentId].arrivalTime = undefined;
      updated[studentId].broughtBy = undefined;
      updated[studentId].departureTime = undefined;
      updated[studentId].pickedUpBy = undefined;
      updated[studentId].pickUpPersonName = undefined;
      updated[studentId].pickUpPersonPhone = undefined;
    }
    onMarksChange(updated);
  };

  const markAll = (status: 'PRESENT' | 'ABSENT') => {
    const now = new Date();
    const currentHHmm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    const updated = { ...marks };
    students.forEach((s) => {
      updated[s.studentId] = {
        ...updated[s.studentId],
        status,
        arrivalTime: status !== 'ABSENT' && isMorning ? (updated[s.studentId]?.arrivalTime || currentHHmm) : undefined,
        broughtBy: status !== 'ABSENT' && isMorning ? (updated[s.studentId]?.broughtBy || 'Mother') : undefined,
        departureTime: status !== 'ABSENT' && !isMorning ? (updated[s.studentId]?.departureTime || currentHHmm) : undefined,
        pickedUpBy: status !== 'ABSENT' && !isMorning ? (updated[s.studentId]?.pickedUpBy || 'Mother') : undefined,
      };
    });
    onMarksChange(updated);
  };

  if (isLoading) {
    return (
      <div className="flex min-h-[360px] items-center justify-center rounded-md border border-dashed border-slate-300 bg-white">
        <div className="text-center">
          <Loader2 className="mx-auto h-8 w-8 animate-spin text-slate-500" />
          <p className="mt-3 text-sm text-slate-500">Loading attendance session…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Header */}
      <section className="rounded-md border border-slate-200 bg-white p-5">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <div className={`flex h-9 w-9 items-center justify-center rounded-full ${
                isMorning ? 'bg-amber-100 text-amber-600' : 'bg-indigo-100 text-indigo-600'
              }`}>
                {isMorning ? <Clock className="h-4 w-4" /> : <ArrowRight className="h-4 w-4" />}
              </div>
              <div>
                <h2 className="text-xl font-semibold text-slate-950">{sessionLabel}</h2>
                <p className="text-sm text-slate-500">{session.className} · {dateStr}</p>
              </div>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {session.isComplete && (
              <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">✓ Submitted</Badge>
            )}
            <Button variant="outline" size="sm" onClick={onCancel}>← Back</Button>
          </div>
        </div>

        {/* Summary strip */}
        <div className="mt-4 flex flex-wrap gap-3">
          <div className="flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1">
            <span className="h-2 w-2 rounded-full bg-emerald-500" />
            <span className="text-xs font-medium text-emerald-700">Present: {presentCount}</span>
          </div>
          <div className="flex items-center gap-1.5 rounded-full border border-red-200 bg-red-50 px-3 py-1">
            <span className="h-2 w-2 rounded-full bg-red-500" />
            <span className="text-xs font-medium text-red-700">Absent: {absentCount}</span>
          </div>
          <div className="flex items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-3 py-1">
            <span className="h-2 w-2 rounded-full bg-amber-500" />
            <span className="text-xs font-medium text-amber-700">Late: {lateCount}</span>
          </div>
          {excusedCount > 0 && (
            <div className="flex items-center gap-1.5 rounded-full border border-blue-200 bg-blue-50 px-3 py-1">
              <span className="h-2 w-2 rounded-full bg-blue-500" />
              <span className="text-xs font-medium text-blue-700">Excused: {excusedCount}</span>
            </div>
          )}
          <div className="flex items-center gap-1.5 rounded-full border border-slate-200 bg-slate-50 px-3 py-1">
            <span className="text-xs font-medium text-slate-600">Total: {students.length}</span>
          </div>
        </div>
      </section>

      {/* Bulk Actions */}
      {!session.isComplete && (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" onClick={() => markAll('PRESENT')} className="gap-1.5 border-emerald-200 text-emerald-700 hover:bg-emerald-50">
            <CheckCircle2 className="h-3.5 w-3.5" />
            Mark All Present
          </Button>
          <Button size="sm" variant="outline" onClick={() => markAll('ABSENT')} className="gap-1.5 border-red-200 text-red-700 hover:bg-red-50">
            <XCircle className="h-3.5 w-3.5" />
            Mark All Absent
          </Button>
        </div>
      )}

      {/* Student Grid */}
      <section className="rounded-md border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3 w-12">#</th>
                <th className="px-4 py-3">Student</th>
                <th className="px-4 py-3 w-36">Status</th>
                <th className="px-4 py-3 w-28">{isMorning ? 'Time' : 'Time'}</th>
                <th className="px-4 py-3 w-40">{isMorning ? 'Brought By' : 'Picked Up By'}</th>
                {!isMorning && <th className="px-4 py-3 w-36">Person Name</th>}
                {!isMorning && <th className="px-4 py-3 w-36">Phone</th>}
                <th className="px-4 py-3 w-44">Notes</th>
                {session.isComplete && <th className="px-4 py-3 w-24">Action</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {students.map((student, index) => {
                const mark = marks[student.studentId] ?? { status: session.isComplete ? 'NOT_MARKED' as const : 'PRESENT' as const };
                const isEditing = session.isComplete && editingStudentId === student.studentId;
                const isReadOnly = session.isComplete && !isEditing;
                const showTimeFields = mark.status !== 'ABSENT' && mark.status !== 'NOT_MARKED';
                const statusOpt = statusOptions.find((o) => o.value === mark.status);

                return (
                  <tr key={student.studentId} className={`transition ${
                    mark.status === 'ABSENT' ? 'bg-red-50/40' : mark.status === 'LATE' ? 'bg-amber-50/40' : ''
                  }`}>
                    <td className="px-4 py-3 text-slate-500">{index + 1}</td>
                    <td className="px-4 py-3">
                      <p className="font-medium text-slate-900">{student.firstName} {student.lastName}</p>
                      <p className="text-xs text-slate-500">{student.admissionNumber}</p>
                    </td>
                    <td className="px-4 py-3">
                      {isReadOnly ? (
                        <Badge className={`${statusOpt?.color ?? 'bg-slate-100 text-slate-700'} hover:opacity-90`}>
                          {statusOpt?.label ?? mark.status}
                        </Badge>
                      ) : (
                        <select
                          value={mark.status}
                          onChange={(e) => updateMark(student.studentId, 'status', e.target.value)}
                          className="h-9 w-full rounded-md border border-slate-200 bg-white px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-slate-950"
                        >
                          {statusOptions.map((opt) => (
                            <option key={opt.value} value={opt.value}>{opt.label}</option>
                          ))}
                        </select>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {showTimeFields ? (
                        isReadOnly ? (
                          <span className="text-slate-600">{isMorning ? mark.arrivalTime ?? '—' : mark.departureTime ?? '—'}</span>
                        ) : (
                          <Input
                            type="time"
                            className="h-9"
                            value={isMorning ? (mark.arrivalTime ?? '') : (mark.departureTime ?? '')}
                            onChange={(e) => updateMark(student.studentId, isMorning ? 'arrivalTime' : 'departureTime', e.target.value)}
                          />
                        )
                      ) : (
                        <span className="text-slate-300">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {showTimeFields ? (
                        isReadOnly ? (
                          <span className="text-slate-600">{isMorning ? mark.broughtBy ?? '—' : mark.pickedUpBy ?? '—'}</span>
                        ) : (
                          <select
                            value={isMorning ? (mark.broughtBy ?? 'Mother') : (mark.pickedUpBy ?? 'Mother')}
                            onChange={(e) => updateMark(student.studentId, isMorning ? 'broughtBy' : 'pickedUpBy', e.target.value)}
                            className="h-9 w-full rounded-md border border-slate-200 bg-white px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-slate-950"
                          >
                            {(isMorning ? BROUGHT_BY_OPTIONS : PICKED_UP_OPTIONS).map((opt) => (
                              <option key={opt} value={opt}>{opt}</option>
                            ))}
                          </select>
                        )
                      ) : (
                        <span className="text-slate-300">—</span>
                      )}
                    </td>
                    {!isMorning && (
                      <td className="px-4 py-3">
                        {showTimeFields ? (
                          isReadOnly ? (
                            <span className="text-slate-600">{mark.pickUpPersonName || '—'}</span>
                          ) : (
                            <Input
                              className="h-9"
                              placeholder="Name"
                              value={mark.pickUpPersonName ?? ''}
                              onChange={(e) => updateMark(student.studentId, 'pickUpPersonName', e.target.value)}
                            />
                          )
                        ) : (
                          <span className="text-slate-300">—</span>
                        )}
                      </td>
                    )}
                    {!isMorning && (
                      <td className="px-4 py-3">
                        {showTimeFields ? (
                          isReadOnly ? (
                            <span className="text-slate-600">{mark.pickUpPersonPhone || '—'}</span>
                          ) : (
                            <Input
                              className="h-9"
                              placeholder="08XX XXX XXXX"
                              value={mark.pickUpPersonPhone ?? ''}
                              onChange={(e) => updateMark(student.studentId, 'pickUpPersonPhone', e.target.value)}
                            />
                          )
                        ) : (
                          <span className="text-slate-300">—</span>
                        )}
                      </td>
                    )}
                    <td className="px-4 py-3">
                      {isReadOnly ? (
                        <span className="text-xs text-slate-500">{mark.notes || '—'}</span>
                      ) : (
                        <Input
                          className="h-9"
                          placeholder="Note"
                          value={mark.notes ?? ''}
                          onChange={(e) => updateMark(student.studentId, 'notes', e.target.value)}
                        />
                      )}
                    </td>
                    {session.isComplete && (
                      <td className="px-4 py-3">
                        {isEditing ? (
                          <div className="flex gap-1">
                            <Button
                              size="sm"
                              className="h-7 bg-slate-950 px-2 text-xs text-white hover:bg-slate-800"
                              disabled={isSaving}
                              onClick={() => onUpdateMark(student.studentId)}
                            >
                              {isSaving ? <Loader2 className="h-3 w-3 animate-spin" /> : <Save className="h-3 w-3" />}
                            </Button>
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-7 px-2 text-xs"
                              onClick={() => onEditStudent(null)}
                            >
                              ✕
                            </Button>
                          </div>
                        ) : (
                          mark.markId ? (
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-7 px-2 text-xs border-slate-200"
                              onClick={() => onEditStudent(student.studentId)}
                            >
                              Edit
                            </Button>
                          ) : (
                            <span className="text-xs text-slate-400">—</span>
                          )
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}
              {!students.length && (
                <EmptyTableRow colSpan={isMorning ? (session.isComplete ? 7 : 6) : (session.isComplete ? 9 : 8)} message="No students in this class. Enroll students first." />
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Submit */}
      {!session.isComplete && students.length > 0 && (
        <div className="flex items-center justify-between rounded-md border border-slate-200 bg-white px-5 py-4">
          <p className="text-sm text-slate-500">
            <span className="font-medium text-emerald-600">{presentCount}</span> present ·{' '}
            <span className="font-medium text-red-600">{absentCount}</span> absent ·{' '}
            <span className="font-medium text-amber-600">{lateCount}</span> late
          </p>
          <Button onClick={onSubmit} disabled={isSaving} className="bg-slate-950 text-white hover:bg-slate-800">
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <CheckCircle2 className="mr-2 h-4 w-4" />}
            Submit Attendance
          </Button>
        </div>
      )}
    </div>
  );
}

function ClassesSection({
  classes,
  selectedClassId,
  classDetail,
  visibleStudents,
  studentSearch,
  isClassLoading,
  onSearch,
  onSelectClass,
}: {
  classes: ClassRoom[];
  selectedClassId: string;
  classDetail: ClassDetail | null;
  visibleStudents: ClassStudent[];
  studentSearch: string;
  isClassLoading: boolean;
  onSearch: (value: string) => void;
  onSelectClass: (classId: string) => void;
}) {
  return (
    <div className="grid gap-6 xl:grid-cols-[320px_1fr]">
      <section className="rounded-md border border-slate-200 bg-white p-4">
        <p className="text-sm font-medium text-slate-500">Assigned Classes</p>
        <div className="mt-4 space-y-2">
          {classes.map((item) => (
            <button
              key={item.classId}
              type="button"
              onClick={() => onSelectClass(item.classId)}
              className={`w-full rounded-md border px-3 py-3 text-left text-sm transition ${
                selectedClassId === item.classId ? 'border-slate-950 bg-slate-50' : 'border-slate-200 hover:bg-slate-50'
              }`}
            >
              <p className="font-medium text-slate-900">{item.name}</p>
              <p className="mt-1 text-slate-500">{item.currentEnrollment}/{item.capacity} students</p>
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-md border border-slate-200 bg-white">
        <div className="flex flex-col gap-4 border-b border-slate-200 p-5 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-950">{classDetail?.name ?? 'Class roster'}</h2>
            <p className="text-sm text-slate-500">{classDetail ? `${classDetail.currentEnrollment} students · ${classDetail.sessionName}` : 'Select a class'}</p>
          </div>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <Input className="w-full pl-9 md:w-72" value={studentSearch} onChange={(event) => onSearch(event.target.value)} placeholder="Search roster" />
          </div>
        </div>
        {isClassLoading ? (
          <div className="flex min-h-64 items-center justify-center">
            <Loader2 className="h-6 w-6 animate-spin text-slate-500" />
          </div>
        ) : (
          <StudentRoster students={visibleStudents} />
        )}
      </section>
    </div>
  );
}

function ScoresSection({
  classDetail,
  caConfig,
  gradingRules,
  onOpenScores,
}: {
  classDetail: ClassDetail | null;
  caConfig: CaConfig | null;
  gradingRules: GradingRules | null;
  onOpenScores: (mode: ScoreMode) => void;
}) {
  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
      <section className="rounded-md border border-slate-200 bg-white p-5">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="text-sm font-medium text-slate-500">Score Entry</p>
            <h2 className="mt-1 text-xl font-semibold text-slate-950">{classDetail?.name ?? 'Select a class'}</h2>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => onOpenScores('ca')} className="bg-slate-950 text-white hover:bg-slate-800">
              <ClipboardList className="mr-2 h-4 w-4" />
              Enter CA
            </Button>
            <Button variant="outline" onClick={() => onOpenScores('exam')}>
              <Award className="mr-2 h-4 w-4" />
              Enter Exam
            </Button>
          </div>
        </div>
        <div className="mt-5 grid gap-4 md:grid-cols-3">
          <StatusCard label="CA configuration" value={`${caConfig?.componentCount ?? 0} components`} complete={Boolean(caConfig?.componentCount)} />
          <StatusCard label="Exam weight" value={`${caConfig?.examWeightPercentage ?? 0}%`} complete={Boolean(caConfig?.examWeightPercentage)} />
          <StatusCard label="Grading rules" value={`${gradingRules?.gradesCount ?? 0} grades`} complete={Boolean(gradingRules?.gradesCount)} />
        </div>
      </section>

      <section className="rounded-md border border-slate-200 bg-white p-5">
        <p className="text-sm font-medium text-slate-500">Class Stats</p>
        <div className="mt-4 space-y-3">
          <MetricRow label="Students" value={formatNumber(classDetail?.currentEnrollment ?? 0)} />
          <MetricRow label="Male" value={formatNumber(classDetail?.statistics?.maleCount ?? 0)} />
          <MetricRow label="Female" value={formatNumber(classDetail?.statistics?.femaleCount ?? 0)} />
          <MetricRow label="Pending fees" value={formatNumber(classDetail?.statistics?.pendingFees ?? 0)} />
        </div>
      </section>
    </div>
  );
}

function ResultsSection({ resultSheet, classDetail, currentTermName }: { resultSheet: ClassResultSheet | null; classDetail: ClassDetail | null; currentTermName?: string }) {
  const rows = resultSheet?.students ?? [];
  const subjects = resultSheet?.subjects ?? [];
  return (
    <section className="rounded-md border border-slate-200 bg-white">
      <div className="border-b border-slate-200 p-5">
        <h2 className="text-lg font-semibold text-slate-950">{resultSheet?.className ?? classDetail?.name ?? 'Class result sheet'}</h2>
        <p className="text-sm text-slate-500">{resultSheet?.termName ?? currentTermName ?? 'Current term'} · {rows.length || resultSheet?.classSize || 0} students</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-5 py-3">Pos</th>
              <th className="px-5 py-3">Student</th>
              <th className="px-5 py-3">Admission</th>
              {subjects.map((sub) => (
                <th key={sub} className="px-5 py-3">{sub}</th>
              ))}
              <th className="px-5 py-3">Average</th>
              {/*<th className="px-5 py-3">Grade</th>*/}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.studentId} className="border-b border-slate-100">
                <td className="px-5 py-4 text-slate-600">{row.position || '-'}</td>
                <td className="px-5 py-4 font-medium text-slate-900">{row.name}</td>
                <td className="px-5 py-4 text-slate-600">{row.admissionNumber}</td>
                {subjects.map((sub) => {
                  const score = row.subjects?.find((s) => s.subject === sub);
                  return (
                    <td key={sub} className="px-5 py-4 text-slate-600">
                      {score ? `${score.finalScore.toFixed(0)} (${score.grade ?? ''}` : '—'})
                    </td>
                  );
                })}
                <td className="px-5 py-4 text-slate-600">{Number(row.average ?? 0).toFixed(1)}%</td>
                {/*<td className="px-5 py-4"><Badge variant="outline">{row.overallGrade ?? '-'}</Badge></td>*/}
              </tr>
            ))}
            {!rows.length && <EmptyTableRow colSpan={5 + subjects.length} message="No result rows returned yet." />}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function CommentsSection({
  students,
  selectedStudentId,
  commentText,
  isSaving,
  onSelectStudent,
  onCommentChange,
  onSave,
  onAutoGenerate,
}: {
  students: ClassStudent[];
  selectedStudentId: string;
  commentText: string;
  isSaving: boolean;
  onSelectStudent: (studentId: string) => void;
  onCommentChange: (value: string) => void;
  onSave: () => void;
  onAutoGenerate: () => void;
}) {
  const student = students.find((item) => item.studentId === selectedStudentId);
  return (
    <div className="grid gap-6 xl:grid-cols-[340px_1fr]">
      <section className="rounded-md border border-slate-200 bg-white p-4">
        <p className="text-sm font-medium text-slate-500">Students</p>
        <div className="mt-4 max-h-[620px] space-y-2 overflow-y-auto">
          {students.map((item) => (
            <button
              key={item.studentId}
              type="button"
              onClick={() => onSelectStudent(item.studentId)}
              className={`w-full rounded-md border px-3 py-3 text-left text-sm transition ${
                selectedStudentId === item.studentId ? 'border-slate-950 bg-slate-50' : 'border-slate-200 hover:bg-slate-50'
              }`}
            >
              <p className="font-medium text-slate-900">{item.firstName} {item.lastName}</p>
              <p className="mt-1 text-slate-500">{item.admissionNumber}</p>
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-md border border-slate-200 bg-white p-5">
        <p className="text-sm font-medium text-slate-500">Teacher Comment</p>
        <h2 className="mt-1 text-xl font-semibold text-slate-950">{student ? `${student.firstName} ${student.lastName}` : 'Select a student'}</h2>
        <Textarea
          className="mt-5 min-h-48"
          value={commentText}
          onChange={(event) => onCommentChange(event.target.value)}
          placeholder="Write a clear, constructive report-card comment..."
        />
        <div className="mt-4 flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" onClick={onAutoGenerate} disabled={isSaving || !student}>
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Sparkles className="mr-2 h-4 w-4" />}
            Auto-generate from Result
          </Button>
          <Button onClick={onSave} disabled={isSaving || !student || !commentText.trim()} className="bg-slate-950 text-white hover:bg-slate-800">
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
            Save Comment
          </Button>
        </div>
      </section>
    </div>
  );
}

function AssessmentsSection({
  students,
  traits,
  ratings,
  isSaving,
  onRatingsChange,
  onSave,
}: {
  students: ClassStudent[];
  traits: AssessmentTrait[];
  ratings: Record<string, Record<string, { rating: string; comment: string }>>;
  isSaving: boolean;
  onRatingsChange: (ratings: Record<string, Record<string, { rating: string; comment: string }>>) => void;
  onSave: () => void;
}) {
  const behavioural = traits.filter((trait) => ['AFFECTIVE', 'BEHAVIOURAL'].includes(trait.category));
  const psychomotor = traits.filter((trait) => trait.category === 'PSYCHOMOTOR');
  const orderedTraits = [...behavioural, ...psychomotor];
  const ratingOptions = ['A', 'B', 'C', 'D', 'E'];

  const updateRating = (studentId: string, traitId: string, patch: Partial<{ rating: string; comment: string }>) => {
    const current = ratings[studentId]?.[traitId] ?? { rating: '', comment: '' };
    onRatingsChange({
      ...ratings,
      [studentId]: {
        ...(ratings[studentId] ?? {}),
        [traitId]: { ...current, ...patch },
      },
    });
  };

  return (
    <section className="rounded-md border border-slate-200 bg-white">
      <div className="flex flex-col gap-3 border-b border-slate-200 p-5 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-sm font-medium text-slate-500">Report Card Assessments</p>
          <h2 className="mt-1 text-xl font-semibold text-slate-950">Behavioural & Psychomotor Ratings</h2>
          <p className="mt-1 text-sm text-slate-500">Use A–E or your school’s preferred rating shorthand.</p>
        </div>
        <Button onClick={onSave} disabled={isSaving || !students.length || !orderedTraits.length} className="bg-slate-950 text-white hover:bg-slate-800">
          {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
          Save Assessments
        </Button>
      </div>

      {!orderedTraits.length ? (
        <div className="p-5">
          <EmptyPanel message="No assessment traits are configured for this class yet." />
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[960px] text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-5 py-3">Student</th>
                {orderedTraits.map((trait) => (
                  <th key={trait.traitId} className="px-5 py-3">
                    <span>{trait.name}</span>
                    <span className="ml-1 text-[10px] normal-case text-slate-400">
                      {trait.category === 'PSYCHOMOTOR' ? 'Psychomotor' : 'Behavioural'}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {students.map((student) => (
                <tr key={student.studentId} className="border-b border-slate-100 align-top">
                  <td className="px-5 py-4">
                    <p className="font-medium text-slate-900">{student.firstName} {student.lastName}</p>
                    <p className="text-xs text-slate-500">{student.admissionNumber}</p>
                  </td>
                  {orderedTraits.map((trait) => {
                    const value = ratings[student.studentId]?.[trait.traitId] ?? { rating: '', comment: '' };
                    return (
                      <td key={`${student.studentId}-${trait.traitId}`} className="px-5 py-4">
                        <div className="space-y-2">
                          <select
                            value={value.rating}
                            onChange={(event) => updateRating(student.studentId, trait.traitId, { rating: event.target.value })}
                            className="h-9 w-24 rounded-md border border-slate-200 bg-white px-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-950"
                          >
                            <option value="">—</option>
                            {ratingOptions.map((rating) => (
                              <option key={rating} value={rating}>{rating}</option>
                            ))}
                          </select>
                          <Input
                            value={value.comment}
                            onChange={(event) => updateRating(student.studentId, trait.traitId, { comment: event.target.value })}
                            placeholder="Optional note"
                            className="h-9 min-w-36"
                          />
                        </div>
                      </td>
                    );
                  })}
                </tr>
              ))}
              {!students.length && <EmptyTableRow colSpan={orderedTraits.length + 1} message="No students in this class." />}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function ReferenceSection({ classDetail, caConfig, gradingRules }: { classDetail: ClassDetail | null; caConfig: CaConfig | null; gradingRules: GradingRules | null }) {
  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
      <section className="rounded-md border border-slate-200 bg-white">
        <div className="border-b border-slate-200 p-5">
          <h2 className="text-lg font-semibold text-slate-950">Fee Status Reference</h2>
          <p className="text-sm text-slate-500">{classDetail?.name ?? 'Selected class'}</p>
        </div>
        <StudentRoster students={classDetail?.students ?? []} showFees />
      </section>
      <section className="space-y-6">
        <div className="rounded-md border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">CA Configuration</p>
          <h3 className="mt-2 text-xl font-semibold text-slate-950">{formatNumber(caConfig?.componentCount ?? 0)} components</h3>
          <p className="mt-1 text-sm text-slate-500">{caConfig?.examWeightPercentage ?? 0}% exam weight</p>
        </div>
        <div className="rounded-md border border-slate-200 bg-white p-5">
          <p className="text-sm font-medium text-slate-500">Grading Rules</p>
          <h3 className="mt-2 text-xl font-semibold text-slate-950">{formatNumber(gradingRules?.gradesCount ?? 0)} grade bands</h3>
          <p className="mt-1 text-sm text-slate-500">{gradingRules?.message ?? 'Configured by school admin'}</p>
        </div>
      </section>
    </div>
  );
}

function SelectField({
  label,
  value,
  onChange,
  options,
  required,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: Array<{ value: string; label: string }>;
  required?: boolean;
}) {
  const id = label.replace(/\s+/g, '-').toLowerCase();
  return (
    <div>
      <Label htmlFor={id}>{label}</Label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        required={required}
        className="mt-2 flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-950 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {options.length === 0 ? (
          <option value="">No options available</option>
        ) : (
          options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))
        )}
      </select>
    </div>
  );
}

function ScoreDialog({
  open,
  mode,
  form,
  students,
  isSaving,
  caConfig,
  gradingRules,
  lookupSubjects,
  lookupComponents,
  lookupExams,
  onOpenChange,
  onChange,
  onSubjectChange,
  onAssessmentChange,
  onSubmit,
}: {
  open: boolean;
  mode: ScoreMode;
  form: ScoreForm;
  students: ClassStudent[];
  isSaving: boolean;
  caConfig: CaConfig | null;
  gradingRules: GradingRules | null;
  lookupSubjects: Array<{ id: string; name: string; code: string }>;
  lookupComponents: Array<{ id: string; name: string; maxScore: number; weightPercentage?: number; sortOrder?: number }>;
  lookupExams: Array<{ id: string; name: string; maxScore: number }>;
  onOpenChange: (open: boolean) => void;
  onChange: (form: ScoreForm) => void;
  onSubjectChange: (id: string) => void | Promise<void>;
  onAssessmentChange: (id: string, mode: ScoreMode) => void | Promise<void>;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
}) {
  const maxScore = Number(form.maxScore) || 0;
  const enteredScores = Object.values(form.scores)
    .filter((score) => score.trim() !== '')
    .map(Number)
    .filter((score) => !Number.isNaN(score));
  const average = enteredScores.length ? enteredScores.reduce((sum, score) => sum + score, 0) / enteredScores.length : 0;

  const gridStyle = mode === 'ca'
    ? { gridTemplateColumns: `56px 2.5fr repeat(${lookupComponents.length}, 1fr)` }
    : { gridTemplateColumns: '56px 2.5fr 140px' };

  let totalEnteredCount = 0;
  if (mode === 'ca') {
    if (form.caScores) {
      Object.values(form.caScores).forEach((studentScores) => {
        Object.values(studentScores).forEach((score) => {
          if (score && score.trim() !== '') {
            totalEnteredCount++;
          }
        });
      });
    }
  } else {
    totalEnteredCount = enteredScores.length;
  }

  const visibleScoreStudents = students.filter(
    (student) => !form.studentIdFilter || form.studentIdFilter === 'ALL' || student.studentId === form.studentIdFilter,
  );
  const selectedExam = lookupExams.find((exam) => exam.id === form.assessmentId);
  const configuredExamMax = caConfig?.examWeightPercentage
    ? Math.round(caConfig.examWeightPercentage)
    : undefined;
  const examMax = selectedExam?.maxScore ?? lookupExams[0]?.maxScore ?? configuredExamMax ?? 60;
  const examWeight = caConfig?.examWeightPercentage ?? examMax;
  const overallMaxScore = 100;
  const configuredGrades =
    gradingRules?.config?.grades ??
    Object.values(gradingRules?.config?.byEducationLevel ?? {}).find((scheme) => scheme?.grades?.length)?.grades ??
    [
      { grade: 'A1', minScore: 75, maxScore: 100, remark: 'Excellent' },
      { grade: 'B2', minScore: 70, maxScore: 74, remark: 'Very Good' },
      { grade: 'B3', minScore: 65, maxScore: 69, remark: 'Good' },
      { grade: 'C4', minScore: 60, maxScore: 64, remark: 'Credit' },
      { grade: 'C5', minScore: 55, maxScore: 59, remark: 'Credit' },
      { grade: 'C6', minScore: 50, maxScore: 54, remark: 'Credit' },
      { grade: 'D7', minScore: 45, maxScore: 49, remark: 'Pass' },
      { grade: 'E8', minScore: 40, maxScore: 44, remark: 'Pass' },
      { grade: 'F9', minScore: 0, maxScore: 39, remark: 'Fail' },
    ];
  const resolvePreviewGrade = (score: number) => configuredGrades.find((grade) => score >= Number(grade.minScore) && score <= Number(grade.maxScore));
  const parseScore = (value?: string | number | null) => {
    if (value === undefined || value === null) return null;
    if (typeof value === 'string' && value.trim() === '') return null;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  };
  const formatPreviewScore = (value: number | null | undefined) => {
    if (value === undefined || value === null || !Number.isFinite(value)) return '—';
    return Number.isInteger(value) ? String(value) : value.toFixed(1);
  };
  const previewRows = visibleScoreStudents.map((student) => {
    const existing = form.existingSubjectResults?.[student.studentId];
    const caItems = lookupComponents.map((component) => {
      const typedScore = parseScore(form.caScores?.[student.studentId]?.[component.id]);
      const savedScore = existing?.caScores?.find((ca) => ca.component.trim().toLowerCase() === component.name.trim().toLowerCase());
      const score = typedScore ?? parseScore(savedScore?.score);
      const max = component.maxScore || savedScore?.maxScore || 0;
      const weight = component.weightPercentage ?? max;
      const weighted = score !== null && max > 0 ? (score / max) * weight : 0;
      return { name: component.name, score, max, weighted };
    });
    const rawCaTotal = caItems.reduce((sum, item) => sum + (item.score ?? 0), 0);
    const caMaxTotal = caItems.reduce((sum, item) => sum + item.max, 0);
    const caWeighted = caItems.reduce((sum, item) => sum + item.weighted, 0);
    const examScore = parseScore(form.scores[student.studentId]) ?? parseScore(existing?.examScore);
    const examWeighted = examScore !== null && examMax > 0 ? (examScore / examMax) * examWeight : 0;
    const finalScore = caWeighted + examWeighted;
    const grade = resolvePreviewGrade(finalScore);

    return {
      student,
      caItems,
      rawCaTotal,
      caMaxTotal,
      examScore,
      examMax,
      finalScore,
      grade: grade?.grade ?? existing?.grade ?? '—',
      remark: grade?.remark ?? existing?.remark,
      hasAnyScore: caItems.some((item) => item.score !== null) || examScore !== null,
    };
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto bg-white sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>{mode === 'ca' ? 'Enter CA Scores' : 'Enter Exam Scores'}</DialogTitle>
          <DialogDescription>Scores are saved for the selected class and current term.</DialogDescription>
        </DialogHeader>
        <form className="space-y-5" onSubmit={onSubmit}>
          {mode === 'ca' ? (
            <div className="grid gap-4 md:grid-cols-2">
              <SelectField
                label="Subject"
                value={form.subjectId}
                onChange={(value) => void onSubjectChange(value)}
                options={lookupSubjects.map((s) => ({ value: s.id, label: `${s.name} (${s.code || 'N/A'})` }))}
                required
              />
              <SelectField
                label="Student (Optional)"
                value={form.studentIdFilter || 'ALL'}
                onChange={(value) => onChange({ ...form, studentIdFilter: value })}
                options={[
                  { value: 'ALL', label: 'All Students' },
                  ...students.map((s) => ({ value: s.studentId, label: `${s.firstName} ${s.lastName}` }))
                ]}
              />
            </div>
          ) : (
            <div className="grid gap-4 md:grid-cols-4">
              <SelectField
                label="Subject"
                value={form.subjectId}
                onChange={(value) => void onSubjectChange(value)}
                options={lookupSubjects.map((s) => ({ value: s.id, label: `${s.name} (${s.code || 'N/A'})` }))}
                required
              />
              <SelectField
                label="Exam"
                value={form.assessmentId}
                onChange={(value) => void onAssessmentChange(value, mode)}
                options={lookupExams.map((e) => ({ value: e.id, label: `${e.name} (Exam max: ${e.maxScore})` }))}
                required
              />
              <Field label="Exam max score" type="number" min="1" value={form.maxScore} onChange={(value) => onChange({ ...form, maxScore: value })} required disabled />
              <SelectField
                label="Student (Optional)"
                value={form.studentIdFilter || 'ALL'}
                onChange={(value) => onChange({ ...form, studentIdFilter: value })}
                options={[
                  { value: 'ALL', label: 'All Students' },
                  ...students.map((s) => ({ value: s.studentId, label: `${s.firstName} ${s.lastName}` }))
                ]}
              />
            </div>
          )}

          <div className="rounded-md border border-slate-200 overflow-x-auto">
            <div className="min-w-[750px]">
              <div
                style={gridStyle}
                className="grid border-b border-slate-200 bg-slate-50 px-4 py-3 text-xs font-semibold uppercase text-slate-500"
              >
                <span>#</span>
                <span>Student</span>
                {mode === 'ca' ? (
                  lookupComponents.map((comp) => (
                    <span key={comp.id} className="text-center">
                      {comp.name}{' '}
                      <span className="text-[10px] text-slate-400 lowercase">(max {comp.maxScore})</span>
                    </span>
                  ))
                ) : (
                  <span>Score</span>
                )}
              </div>
              <div className="divide-y divide-slate-100">
                {visibleScoreStudents
                  .map((student, index) => (
                    <div
                      key={student.studentId}
                      style={gridStyle}
                      className="grid items-center px-4 py-3"
                    >
                      <span className="text-sm text-slate-500">{index + 1}</span>
                      <div>
                        <p className="text-sm font-medium text-slate-900">{student.firstName} {student.lastName}</p>
                        <p className="text-xs text-slate-500">{student.admissionNumber}</p>
                      </div>
                      {mode === 'ca' ? (
                        lookupComponents.map((comp) => (
                          <div key={comp.id} className="px-2">
                            <Input
                              type="number"
                              min="0"
                              max={comp.maxScore || undefined}
                              value={form.caScores?.[student.studentId]?.[comp.id] ?? ''}
                              onChange={(event) => {
                                const newCaScores = {
                                  ...form.caScores,
                                  [student.studentId]: {
                                    ...(form.caScores?.[student.studentId] ?? {}),
                                    [comp.id]: event.target.value,
                                  },
                                };
                                onChange({
                                  ...form,
                                  caScores: newCaScores,
                                });
                              }}
                              className="text-center"
                            />
                          </div>
                        ))
                      ) : (
                        <Input
                          type="number"
                          min="0"
                          max={maxScore || undefined}
                          value={form.scores[student.studentId] ?? ''}
                          onChange={(event) => onChange({
                            ...form,
                            scores: { ...form.scores, [student.studentId]: event.target.value },
                          })}
                        />
                      )}
                    </div>
                  ))}
              </div>
            </div>
          </div>

          <section className="rounded-md border border-blue-100 bg-blue-50/40">
            <div className="border-b border-blue-100 px-4 py-3">
              <p className="text-sm font-semibold text-slate-900">Result preview</p>
              <p className="text-xs text-slate-500">
                Live check of CA + exam before saving. Saved scores are reused where an input is blank.
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-xs">
                <thead className="border-b border-blue-100 text-slate-500">
                  <tr>
                    <th className="px-4 py-2">Student</th>
                    <th className="px-4 py-2">CA breakdown</th>
                    <th className="px-4 py-2">CA total</th>
                    <th className="px-4 py-2">Exam</th>
                    <th className="px-4 py-2">Final</th>
                    <th className="px-4 py-2">Grade</th>
                  </tr>
                </thead>
                <tbody>
                  {previewRows.map((row) => (
                    <tr key={row.student.studentId} className="border-b border-blue-100/70 last:border-0">
                      <td className="px-4 py-3">
                        <p className="font-medium text-slate-900">{row.student.firstName} {row.student.lastName}</p>
                        <p className="text-[11px] text-slate-500">{row.student.admissionNumber}</p>
                      </td>
                      <td className="px-4 py-3 text-slate-600">
                        {row.caItems.length ? (
                          <div className="flex flex-wrap gap-1.5">
                            {row.caItems.map((item) => (
                              <span key={item.name} className="rounded-full bg-white px-2 py-1 ring-1 ring-blue-100">
                                {item.name}: {formatPreviewScore(item.score)}/{item.max || '—'}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <span className="text-slate-400">No CA components</span>
                        )}
                      </td>
                      <td className="px-4 py-3 font-medium text-slate-700">
                        {row.hasAnyScore ? `${formatPreviewScore(row.rawCaTotal)} / ${formatPreviewScore(row.caMaxTotal)}` : '—'}
                      </td>
                      <td className="px-4 py-3 font-medium text-slate-700">
                        {formatPreviewScore(row.examScore)} / {formatPreviewScore(row.examMax)}
                      </td>
                      <td className="px-4 py-3">
                        <span className="font-semibold text-slate-950">
                          {row.hasAnyScore ? `${formatPreviewScore(row.finalScore)} / ${overallMaxScore}` : '—'}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant="outline" className="bg-white">
                          {row.hasAnyScore ? row.grade : '—'}
                        </Badge>
                        {row.hasAnyScore && row.remark && <p className="mt-1 text-[11px] text-slate-500">{row.remark}</p>}
                      </td>
                    </tr>
                  ))}
                  {!previewRows.length && <EmptyTableRow colSpan={6} message="No students selected for preview." />}
                </tbody>
              </table>
            </div>
          </section>

          <div className="flex flex-col gap-3 border-t border-slate-200 pt-4 md:flex-row md:items-center md:justify-between">
            <p className="text-sm text-slate-500">
              {mode === 'ca' ? (
                `${totalEnteredCount} score(s) entered across components`
              ) : (
                `${enteredScores.length} entered · average ${average.toFixed(1)} / ${maxScore || '-'} exam marks`
              )}
            </p>
            <Button type="submit" disabled={isSaving} className="bg-slate-950 text-white hover:bg-slate-800">
              {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
              Save Scores
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function StudentRoster({ students, showFees = false }: { students: ClassStudent[]; showFees?: boolean }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <th className="px-5 py-3">Student</th>
            <th className="px-5 py-3">Admission</th>
            <th className="px-5 py-3">Gender</th>
            <th className="px-5 py-3">Guardian</th>
            {showFees && <th className="px-5 py-3">Fee Status</th>}
          </tr>
        </thead>
        <tbody>
          {students.map((student) => (
            <tr key={student.studentId} className="border-b border-slate-100">
              <td className="px-5 py-4 font-medium text-slate-900">{student.firstName} {student.lastName}</td>
              <td className="px-5 py-4 text-slate-600">{student.admissionNumber}</td>
              <td className="px-5 py-4 text-slate-600">{student.gender ?? '-'}</td>
              <td className="px-5 py-4 text-slate-600">{student.parentPhone ?? 'Not linked'}</td>
              {showFees && (
                <td className="px-5 py-4">
                  <Badge className={student.feeStatus?.status === 'PAID' ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-100' : 'bg-amber-100 text-amber-700 hover:bg-amber-100'}>
                    {student.feeStatus?.status ?? 'UNKNOWN'}
                  </Badge>
                </td>
              )}
            </tr>
          ))}
          {!students.length && <EmptyTableRow colSpan={showFees ? 5 : 4} message="No students returned for this class." />}
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

function ActionRow({ label, detail, onClick }: { label: string; detail: string; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex w-full items-center justify-between rounded-md border border-slate-200 px-3 py-3 text-left hover:bg-slate-50">
      <span>
        <span className="block text-sm font-medium text-slate-900">{label}</span>
        <span className="mt-1 block text-xs text-slate-500">{detail}</span>
      </span>
      <CheckCircle2 className="h-4 w-4 text-slate-300" />
    </button>
  );
}

function StatusCard({ label, value, complete }: { label: string; value: string; complete: boolean }) {
  return (
    <div className="rounded-md border border-slate-200 p-4">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        {complete ? <CheckCircle2 className="h-4 w-4 text-emerald-600" /> : <XCircle className="h-4 w-4 text-slate-300" />}
      </div>
      <p className="mt-3 text-lg font-semibold text-slate-950">{value}</p>
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

function formatNumber(value: number) {
  return new Intl.NumberFormat('en-NG').format(value || 0);
}

function formatGradeCode(value: string) {
  return value.replace(/_/g, ' ');
}

function readError(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { errors?: Array<{ message?: string }>; message?: string } } }).response;
    return response?.data?.errors?.[0]?.message || response?.data?.message || fallback;
  }
  return fallback;
}

export default TeacherDashboard;
