export interface SubjectScore {
  subjectId: string;
  subjectName: string;
  caScore: number;
  maxCaScore: number;
  examScore: number;
  maxExamScore: number;
  totalScore: number;
  grade: string;
  remark: string;
  classAverage?: number;
}

export interface StudentTerminalResult {
  studentId: string;
  studentName: string;
  admissionNumber: string;
  className: string;
  termName: string;
  sessionName: string;
  totalScore: number;
  averageScore: number;
  classPosition?: number;
  totalStudentsInClass: number;
  status: 'DRAFT' | 'PUBLISHED';
  publishedAt?: string;
  teacherComment?: string;
  principalComment?: string;
  subjectScores: SubjectScore[];
  affectiveTraits?: Record<string, number>;
  psychomotorTraits?: Record<string, number>;
  attendanceSummary?: {
    daysPresent: number;
    daysAbsent: number;
    totalSchoolDays: number;
  };
}

export interface EnterCaScoreRequest {
  classId: string;
  subjectId: string;
  termId: string;
  componentName: string; // e.g., 'CA1', 'CA2'
  scores: Array<{
    studentId: string;
    score: number;
  }>;
}

export interface EnterExamScoreRequest {
  classId: string;
  subjectId: string;
  termId: string;
  scores: Array<{
    studentId: string;
    score: number;
  }>;
}
