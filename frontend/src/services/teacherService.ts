import api from '@/lib/api';

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  errors?: Array<{ code?: string; message?: string; field?: string }>;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AcademicSession {
  sessionId: string;
  name: string;
  startDate: string;
  endDate: string;
  isCurrent: boolean;
  terms: Array<{
    termId: string;
    name: string;
    termNumber: number;
    startDate: string;
    endDate: string;
    isCurrent: boolean;
  }>;
}

export interface ClassRoom {
  classId: string;
  name: string;
  gradeLevel: string;
  section?: string;
  sessionName?: string;
  classTeacher?: { userId: string; name: string } | null;
  capacity: number;
  currentEnrollment: number;
  availableSpots: number;
  status?: string;
}

export interface ClassDetail {
  classId: string;
  name: string;
  gradeLevel: string;
  section?: string;
  sessionName?: string;
  classTeacher?: {
    userId: string;
    name: string;
    phoneNumber?: string;
    email?: string;
  } | null;
  capacity: number;
  currentEnrollment: number;
  students: ClassStudent[];
  statistics?: {
    maleCount: number;
    femaleCount: number;
    fullyPaidFees: number;
    pendingFees: number;
  };
}

export interface ClassStudent {
  studentId: string;
  admissionNumber: string;
  firstName: string;
  lastName: string;
  gender?: string;
  parentPhone?: string;
  feeStatus?: {
    termName?: string;
    status?: string;
    balance?: number;
  };
}

export interface StudentSummary {
  studentId: string;
  admissionNumber: string;
  firstName: string;
  lastName: string;
  middleName?: string;
  currentClass?: { classId: string; name: string; gradeLevel: string };
  parentPhone?: string;
  parentName?: string;
  status?: string;
}

export interface CaConfig {
  componentCount: number;
  examWeightPercentage?: number;
  message?: string;
}

export interface GradingRules {
  schoolId?: string;
  gradesCount: number;
  message?: string;
  config?: {
    grades?: Array<{ grade: string; minScore: number; maxScore: number; remark?: string }>;
    byEducationLevel?: Record<string, { grades?: Array<{ grade: string; minScore: number; maxScore: number; remark?: string }> }>;
  } | null;
}

export interface ClassResultSheet {
  className?: string;
  termName?: string;
  classSize?: number;
  subjects?: string[];
  students?: Array<{
    studentId: string;
    admissionNumber: string;
    name: string;
    position: number;
    average: number;
    overallGrade?: string;
    subjects?: Array<{
      subject: string;
      finalScore: number;
      grade?: string;
      position: number;
    }>;
  }>;
}

export interface StudentResult {
  student?: {
    studentId: string;
    admissionNumber: string;
    fullName: string;
    className: string;
    classSize: number;
  };
  term?: { termId: string; name: string; sessionName: string };
  subjects?: Array<{
    subjectId: string;
    subjectName: string;
    caScores?: Array<{ component: string; score: number; maxScore: number }>;
    caTotal?: number;
    caMaxTotal?: number;
    examScore?: number;
    examMaxScore?: number;
    finalScore?: number;
    grade?: string;
    remark?: string;
    subjectPosition?: number;
    scoreId?: string;
    examId?: string;
  }>;
  summary?: {
    totalScore?: number;
    totalMaxScore?: number;
    average?: number;
    overallGrade?: string;
    subjectsTaken?: number;
  };
  teacherComment?: string;
}

export type StudentSubjectResult = NonNullable<StudentResult['subjects']>[number];

export interface StudentFee {
  studentFeeId: string;
  structureName: string;
  termName: string;
  isCurrentTerm?: boolean;
  isUpcomingTerm?: boolean;
  totalAmount: number;
  amountPaid: number;
  balance: number;
  dueDate?: string;
  status?: string;
}

export interface ScoreEntryPayload {
  studentId: string;
  score: number;
}

export interface CaScorePayload {
  termId: string;
  classId: string;
  subjectId: string;
  caComponentId: string;
  maxScore: number;
  scores: ScoreEntryPayload[];
}

export interface ExamScorePayload {
  examId: string;
  classId: string;
  subjectId: string;
  termId: string;
  maxScore: number;
  scores: ScoreEntryPayload[];
}

export interface UpdateScorePayload {
  score: number;
  reason?: string;
}

export interface UpdateScoreResponse {
  scoreId: string;
  newScore: number;
  previousScore?: number;
  updatedAt?: string;
}

export interface AssessmentTrait {
  traitId: string;
  name: string;
  category: 'AFFECTIVE' | 'PSYCHOMOTOR' | string;
  sortOrder?: number;
}

export interface TraitAssessmentPayload {
  classId: string;
  termId: string;
  entries: Array<{
    studentId: string;
    traitId: string;
    rating: string;
    comment?: string;
  }>;
}

export interface TraitAssessmentValue {
  studentId: string;
  traitId: string;
  rating: string;
  comment?: string;
}

function unwrap<T>(response: { data: ApiEnvelope<T> | T }): T {
  const body = response.data as ApiEnvelope<T>;
  if (body && typeof body === 'object' && 'data' in body && 'success' in body) {
    return body.data;
  }
  return response.data as T;
}

export const teacherService = {
  async getSessions() {
    const response = await api.get<ApiEnvelope<AcademicSession[]>>('/api/v1/schools/current/sessions');
    return unwrap(response);
  },

  async listClasses() {
    const response = await api.get<ApiEnvelope<ClassRoom[]>>('/api/v1/schools/current/classes', {
      params: { sessionId: 'current', status: 'ACTIVE' },
    });
    return unwrap(response);
  },

  async getClassDetails(classId: string) {
    const response = await api.get<ApiEnvelope<ClassDetail>>(`/api/v1/schools/current/classes/${classId}`);
    return unwrap(response);
  },

  async listStudents(classId: string) {
    const response = await api.get<ApiEnvelope<PageResponse<StudentSummary>>>('/api/v1/students', {
      params: { classId, status: 'ACTIVE', page: 0, size: 100 },
    });
    return unwrap(response);
  },

  async getCaConfig() {
    const response = await api.get<ApiEnvelope<CaConfig>>('/api/v1/results/ca-config');
    return unwrap(response);
  },

  async getGradingRules() {
    const response = await api.get<ApiEnvelope<GradingRules>>('/api/v1/results/grading-rules');
    return unwrap(response);
  },

  async getSubjectsForClass(classId: string) {
    const response = await api.get<ApiEnvelope<Array<{ id: string; name: string; code: string }>>>(`/api/v1/results/classes/${classId}/subjects`);
    return unwrap(response);
  },

  async getCaComponents() {
    const response = await api.get<ApiEnvelope<Array<{ id: string; name: string; maxScore: number; weightPercentage?: number; sortOrder?: number }>>>('/api/v1/results/ca-components');
    return unwrap(response);
  },

  async getExamsForTerm(termId: string) {
    const response = await api.get<ApiEnvelope<Array<{ id: string; name: string; maxScore: number }>>>(`/api/v1/results/terms/${termId}/exams`);
    return unwrap(response);
  },

  async getAssessmentTraits(classId: string) {
    const response = await api.get<ApiEnvelope<AssessmentTrait[]>>(`/api/v1/results/classes/${classId}/assessment-traits`);
    return unwrap(response);
  },

  async getTraitAssessmentValues(classId: string, termId: string) {
    const response = await api.get<ApiEnvelope<TraitAssessmentValue[]>>(`/api/v1/results/classes/${classId}/terms/${termId}/trait-assessments`);
    return unwrap(response);
  },

  async enterCaScores(payload: CaScorePayload) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/results/ca-scores', payload);
    return unwrap(response);
  },

  async enterExamScores(payload: ExamScorePayload) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/results/exam-scores', payload);
    return unwrap(response);
  },

  async updateScore(scoreId: string, payload: UpdateScorePayload) {
    const response = await api.put<ApiEnvelope<UpdateScoreResponse>>(`/api/v1/results/scores/${scoreId}`, payload);
    return unwrap(response);
  },

  async saveTraitAssessments(payload: TraitAssessmentPayload) {
    const response = await api.put<ApiEnvelope<unknown>>('/api/v1/results/trait-assessments', payload);
    return unwrap(response);
  },

  async getClassResultSheet(classId: string, termId: string) {
    const response = await api.get<ApiEnvelope<ClassResultSheet>>(`/api/v1/results/classes/${classId}/term/${termId}/result-sheet`);
    return unwrap(response);
  },

  async getStudentResult(studentId: string, termId: string) {
    const response = await api.get<ApiEnvelope<StudentResult>>(`/api/v1/results/students/${studentId}/term/${termId}`);
    return unwrap(response);
  },

  async saveTeacherComment(studentId: string, termId: string, comment: string, autoGenerate = false) {
    const response = await api.put<ApiEnvelope<unknown>>(`/api/v1/results/report-cards/${studentId}/term/${termId}/teacher-comment`, {
      comment,
      autoGenerate,
    });
    return unwrap(response);
  },

  async getStudentFees(studentId: string) {
    const response = await api.get<ApiEnvelope<StudentFee[]>>(`/api/v1/fees/students/${studentId}`);
    return unwrap(response);
  },
};
