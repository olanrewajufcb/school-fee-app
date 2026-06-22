import api from '@/lib/api';

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  errors?: Array<{ code?: string; message?: string; field?: string }>;
  timestamp?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SchoolProfile {
  schoolId: string;
  name: string;
  code: string;
  email: string;
  phone: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  status?: string;
  currentTerm?: {
    termId: string;
    name: string;
    sessionName: string;
    startDate?: string;
    endDate?: string;
  };
}

export interface GradeLevel {
  code: string;
  name: string;
  category: string;
  sortOrder: number;
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
  studentIds?: string[];
  status?: string;
}

export interface UserSummary {
  userId: string;
  email: string;
  phoneNumber?: string;
  firstName?: string;
  lastName?: string;
  userType: string;
  roles?: string[];
  isActive?: boolean;
  childrenCount?: number;
  lastLogin?: string;
  createdAt?: string;
}

export interface StudentSummary {
  studentId: string;
  admissionNumber: string;
  firstName: string;
  lastName: string;
  middleName?: string;
  gender?: string;
  dateOfBirth?: string;
  currentClass?: {
    classId: string;
    name: string;
    gradeLevel: string;
  };
  enrollmentDate?: string;
  status?: string;
  parentPhone?: string;
  parentName?: string;
}

export interface FeeDashboard {
  termName?: string;
  summary?: {
    totalExpected?: number;
    totalCollected?: number;
    totalOutstanding?: number;
    collectionRate?: number;
    fullyPaidStudents?: number;
    partiallyPaidStudents?: number;
    unpaidStudents?: number;
  };
  byClass?: Array<{
    classId: string;
    className: string;
    studentCount: number;
    expectedAmount: number;
    collectedAmount: number;
    collectionRate: number;
  }>;
  upcomingDeadlines?: {
    dueIn3Days?: { count?: number; amount?: number };
    dueToday?: { count?: number; amount?: number };
    overdue?: { count?: number; amount?: number };
  };
}

export interface FeeStructure {
  structureId: string;
  name: string;
  termName?: string;
  sessionName?: string;
  totalAmount: number;
  mandatoryAmount?: number;
  applicableToClasses?: string[];
  applicableClassCount?: number;
  studentCount?: number;
  collectionRate?: number;
  dueDate?: string;
  status?: string;
}

export interface DailySummary {
  totalCollected?: number;
  totalTransactions?: number;
  dailyBreakdown?: Array<{ date: string; amount: number; transactions: number }>;
  byPaymentMethod?: Record<string, { amount: number; count: number }>;
}

export interface NotificationBalance {
  provider?: string;
  balance?: number;
  currency?: string;
  costPerSms?: number;
}

export interface NotificationTemplate {
  templateId: string;
  code: string;
  name: string;
  channel: string;
  body: string;
  variables?: string[];
  isDefault?: boolean;
  isActive?: boolean;
}

export interface CreateClassPayload {
  name: string;
  gradeLevel: string;
  section?: string;
  academicSessionId: string;
  capacity: number;
}

export interface CreateStaffPayload {
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  userType: 'SCHOOL_ADMIN' | 'ACCOUNTANT' | 'TEACHER';
  roles: string[];
}

export interface EnrollStudentPayload {
  firstName: string;
  lastName: string;
  middleName?: string;
  gender: 'MALE' | 'FEMALE';
  dateOfBirth?: string;
  classId: string;
  medicalNotes?: string;
  guardians: Array<{
    firstName: string;
    lastName: string;
    phone: string;
    email?: string;
    relationship: string;
    isPrimaryContact: boolean;
    canPickUpChild: boolean;
    canViewFees: boolean;
    canViewResults: boolean;
    canViewAttendance: boolean;
    canReceiveSms: boolean;
    contactPriority: number;
  }>;
}

export interface CreateFeeStructurePayload {
  name: string;
  sessionId: string;
  termId: string;
  applicableToClassIds: string[];
  dueDate: string;
  items: Array<{
    description: string;
    amount: number;
    isMandatory: boolean;
    sortOrder: number;
  }>;
  lateFeeConfig?: {
    applyAfterDays: number;
    percentageAmount: number;
  };
}

export interface FeeSummary {
  studentFeeId?: string;
  termName?: string;
  totalFee?: number;
  amountPaid?: number;
  balance?: number;
  status?: string;
  dueDate?: string;
}

export interface StudentDetail {
  studentId: string;
  admissionNumber: string;
  firstName: string;
  lastName: string;
  middleName?: string;
  gender?: string;
  dateOfBirth?: string;
  currentClass?: {
    classId: string;
    name: string;
    gradeLevel: string;
    classTeacher?: string;
  };
  enrollmentDate?: string;
  status?: string;
  parents?: Array<{
    userId?: string;
    guardianId?: string;
    name: string;
    phoneNumber?: string;
    relationship?: string;
    isPrimaryContact?: boolean;
  }>;
  currentTermFeeSummary?: FeeSummary;
  upcomingTermFeeSummary?: FeeSummary;
  medicalNotes?: string;
  profilePhotoUrl?: string;
}

export interface UpdateStudentPayload {
  firstName?: string;
  lastName?: string;
  middleName?: string;
  gender?: string;
  dateOfBirth?: string;
  classId?: string;
  medicalNotes?: string;
}

export interface RecordOfflinePaymentPayload {
  studentFeeId: string;
  amount: number;
  paymentMethod: 'CASH' | 'BANK_TRANSFER' | 'POS' | 'CHEQUE';
  receivedBy: string;
  generateReceipt?: boolean;
  notes?: string;
}

export interface PaymentRecord {
  paymentId: string;
  studentName?: string;
  amount: number;
  paymentMethod?: string;
  status?: string;
  reference?: string;
  receiptNumber?: string;
  paidAt?: string;
  createdAt?: string;
}

export interface ReceiptDetail {
  receiptNumber: string;
  studentName?: string;
  amount: number;
  paymentMethod?: string;
  paidAt?: string;
  items?: Array<{ description: string; amount: number }>;
}

export interface GradingRulesPayload {
  config: {
    grades: Array<{ grade: string; minScore: number; maxScore: number; remark: string }>;
    passMark: number;
  };
}

export interface GradingRulesResponse {
  schoolId: string;
  gradesCount: number;
  message: string;
  config: {
    grades: Array<{ grade: string; minScore: number; maxScore: number; remark: string }>;
    passMark: number;
  } | null;
}

export interface ExamLookupResponse {
  id: string;
  name: string;
  maxScore: number;
}

export interface CaConfig {
  componentCount: number;
  examWeightPercentage?: number;
  message?: string;
}

export interface CaConfigPayload {
  components: Array<{ name: string; maxScore: number; weightPercentage: number; sortOrder: number }>;
  examWeightPercentage: number;
}

export interface PromoteStudentsPayload {
  fromClassId: string;
  toClassId: string;
  studentIds: string[];
  newSessionId: string;
}

export interface GenerateReportCardsPayload {
  termId: string;
  classId: string;
  studentIds?: string[];
  format?: 'PDF';
}

export interface SendBulkNotificationPayload {
  studentFeeIds: string[];
  templateCode: string;
  channel: 'SMS' | 'EMAIL';
}

export interface SubjectResponse {
  subjectId: string;
  name: string;
  code?: string;
  category?: string;
  isActive: boolean;
}

export interface ClassSubjectResponse {
  classSubjectId: string;
  subjectId: string;
  subjectName: string;
  subjectCode?: string;
  teacherId?: string;
  teacherName?: string;
}

export interface CreateSubjectPayload {
  name: string;
  code?: string;
  category?: string;
}

export interface AssignSubjectPayload {
  subjectId: string;
  teacherId?: string;
}

export interface AttendanceResponse {
  attendanceId?: string;
  studentId: string;
  studentName?: string;
  admissionNumber?: string;
  status: string;
  date: string;
  sessionType?: string;
  arrivalTime?: string;
  departureTime?: string;
  broughtBy?: string;
  pickedUpBy?: string;
  pickUpPersonName?: string;
  pickUpPersonPhone?: string;
  notes?: string;
}

export interface TodayAttendanceResponse {
  classId: string;
  className: string;
  date: string;
  totalStudents: number;
  present: number;
  absent: number;
  late: number;
  notMarked: number;
  students: AttendanceResponse[];
}

export interface AttendanceSummaryResponse {
  totalSchoolDays: number;
  daysPresent: number;
  daysAbsent: number;
  daysLate: number;
  earlyPickups: number;
  attendancePercentage: number;
}

function unwrap<T>(response: { data: ApiEnvelope<T> | T }): T {
  const body = response.data as ApiEnvelope<T>;
  if (body && typeof body === 'object' && 'data' in body && 'success' in body) {
    return body.data;
  }
  return response.data as T;
}

export const schoolAdminService = {
  async getCurrentSchool() {
    const response = await api.get<ApiEnvelope<SchoolProfile>>('/api/v1/schools/current');
    return unwrap(response);
  },

  async getAvailableGradeLevels() {
    const response = await api.get<ApiEnvelope<GradeLevel[]>>('/api/v1/schools/current/grade-levels/available');
    return unwrap(response);
  },

  async getGradeLevels() {
    const response = await api.get<ApiEnvelope<GradeLevel[]>>('/api/v1/schools/current/grade-levels');
    return unwrap(response);
  },

  async configureGradeLevels(enabledLevels: string[]) {
    const response = await api.put<ApiEnvelope<unknown>>('/api/v1/schools/current/grade-levels', {
      enabledLevels,
    });
    return unwrap(response);
  },

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

  async createClass(payload: CreateClassPayload) {
    const response = await api.post<ApiEnvelope<ClassRoom>>('/api/v1/schools/current/classes', payload);
    return unwrap(response);
  },

  async listUsers(userType?: string) {
    const response = await api.get<ApiEnvelope<PageResponse<UserSummary>>>('/api/v1/auth/users', {
      params: { userType, status: 'ACTIVE', page: 0, size: 50, sortBy: 'userId' },
    });
    return unwrap(response);
  },

  async createStaff(payload: CreateStaffPayload) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/auth/staff', payload);
    return unwrap(response);
  },

  async listStudents(classId?: string, search?: string) {
    const response = await api.get<ApiEnvelope<PageResponse<StudentSummary>>>('/api/v1/students', {
      params: { classId, search, status: 'ACTIVE', page: 0, size: 50 },
    });
    return unwrap(response);
  },

  async enrollStudent(payload: EnrollStudentPayload) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/students', payload);
    return unwrap(response);
  },

  async getFeeDashboard() {
    const response = await api.get<ApiEnvelope<FeeDashboard>>('/api/v1/fees/dashboard', {
      params: { termId: 'current' },
    });
    return unwrap(response);
  },

  async getFeeStructures() {
    const response = await api.get<ApiEnvelope<FeeStructure[]>>('/api/v1/fees/structures', {
      params: { status: 'ACTIVE' },
    });
    return unwrap(response);
  },

  async createFeeStructure(payload: CreateFeeStructurePayload) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/fees/structures', payload);
    return unwrap(response);
  },

  async assignFeeStructure(structureId: string) {
    const response = await api.post<ApiEnvelope<unknown>>(`/api/v1/fees/structures/${structureId}/assign`);
    return unwrap(response);
  },

  async getDailySummary(startDate: string, endDate: string) {
    const response = await api.get<ApiEnvelope<DailySummary>>('/api/v1/reports/daily-summary', {
      params: { startDate, endDate },
    });
    return unwrap(response);
  },

  async getNotificationBalance() {
    const response = await api.get<ApiEnvelope<NotificationBalance>>('/api/v1/notifications/balance');
    return unwrap(response);
  },

  async getNotificationTemplates() {
    const response = await api.get<ApiEnvelope<NotificationTemplate[]>>('/api/v1/notifications/templates');
    return unwrap(response);
  },

  async updateNotificationTemplate(templateId: string, payload: { name?: string; body: string; isActive: boolean }) {
    const response = await api.put<ApiEnvelope<unknown>>(`/api/v1/notifications/templates/${templateId}`, payload);
    return unwrap(response);
  },

  async getStudentDetails(studentId: string) {
    const response = await api.get<ApiEnvelope<StudentDetail>>(`/api/v1/students/${studentId}`);
    return unwrap(response);
  },

  async updateStudent(studentId: string, payload: UpdateStudentPayload) {
    const response = await api.put<ApiEnvelope<unknown>>(`/api/v1/students/${studentId}`, payload);
    return unwrap(response);
  },

  async inviteGuardian(guardianId: string) {
    const response = await api.post<ApiEnvelope<unknown>>(`/api/v1/auth/guardians/${guardianId}/invite`);
    return unwrap(response);
  },

  async recordOfflinePayment(payload: RecordOfflinePaymentPayload) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/payments/offline', payload);
    return unwrap(response);
  },

  async getPaymentHistory(params?: { studentId?: string; page?: number; size?: number }) {
    const response = await api.get<ApiEnvelope<PageResponse<PaymentRecord>>>('/api/v1/payments/history', { params });
    return unwrap(response);
  },

  async getReceipt(receiptNumber: string) {
    const response = await api.get<ApiEnvelope<ReceiptDetail>>(`/api/v1/receipts/${receiptNumber}`);
    return unwrap(response);
  },

  async downloadReceiptPdf(receiptNumber: string) {
    const response = await api.get(`/api/v1/receipts/${receiptNumber}/pdf`, { responseType: 'blob' });
    return response.data;
  },

  async getOutstandingFeeIds(termId: string = 'current', filter: string = 'overdue') {
    const response = await api.get<ApiEnvelope<string[]>>('/api/v1/fees/outstanding-ids', {
      params: { termId, filter }
    });
    return unwrap(response);
  },

  async sendBulkNotifications(payload: SendBulkNotificationPayload) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/notifications/send-bulk', payload);
    return unwrap(response);
  },

  async configureGradingRules(payload: GradingRulesPayload) {
    const response = await api.put<ApiEnvelope<unknown>>('/api/v1/results/grading-rules', payload);
    return unwrap(response);
  },

  async configureCaComponents(payload: CaConfigPayload) {
    const response = await api.put<ApiEnvelope<unknown>>('/api/v1/results/ca-config', payload);
    return unwrap(response);
  },

  async getCaConfig() {
    const response = await api.get<ApiEnvelope<CaConfig>>('/api/v1/results/ca-config');
    return unwrap(response);
  },

  async getGradingRules() {
    const response = await api.get<ApiEnvelope<GradingRulesResponse>>('/api/v1/results/grading-rules');
    return unwrap(response);
  },

  async getExamsForTerm(termId: string) {
    const response = await api.get<ApiEnvelope<ExamLookupResponse[]>>(`/api/v1/results/terms/${termId}/exams`);
    return unwrap(response);
  },

  async getCaComponents() {
    const response = await api.get<ApiEnvelope<Array<{ id: string; name: string; maxScore: number; weightPercentage?: number; sortOrder?: number }>>>('/api/v1/results/ca-components');
    return unwrap(response);
  },

  async getClassResults(classId: string, termId: string) {
    const response = await api.get<ApiEnvelope<unknown>>(`/api/v1/results/classes/${classId}/term/${termId}/result-sheet`);
    return unwrap(response);
  },

  async getStudentResult(studentId: string, termId: string) {
    const response = await api.get<ApiEnvelope<any>>(`/api/v1/results/students/${studentId}/term/${termId}`);
    return unwrap(response);
  },

  async addPrincipalComment(studentId: string, termId: string, comment: string) {
    const response = await api.put<ApiEnvelope<unknown>>(`/api/v1/results/report-cards/${studentId}/term/${termId}/principal-comment`, { comment });
    return unwrap(response);
  },

  async publishResults(termId: string) {
    const response = await api.put<ApiEnvelope<unknown>>(`/api/v1/results/terms/${termId}/publish`);
    return unwrap(response);
  },

  async unpublishResults(termId: string) {
    const response = await api.put<ApiEnvelope<unknown>>(`/api/v1/results/terms/${termId}/unpublish`);
    return unwrap(response);
  },

  async recomputeRankings(classId: string, termId: string) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/results/rankings/recompute', { classId, termId });
    return unwrap(response);
  },

  async generateReportCards(payload: GenerateReportCardsPayload) {
    const response = await api.post<ApiEnvelope<{ jobId: string }>>('/api/v1/results/report-cards', payload);
    return unwrap(response);
  },

  async checkReportCardJob(jobId: string) {
    const response = await api.get<ApiEnvelope<{ jobId: string; status: string; progress?: number; downloadUrl?: string }>>(`/api/v1/results/report-cards/jobs/${jobId}`);
    return unwrap(response);
  },

  async downloadReportCardPdf(downloadUrl: string) {
    const response = await api.get(downloadUrl, { responseType: 'blob' });
    return response.data;
  },

  async promoteStudents(payload: PromoteStudentsPayload) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/schools/current/classes/promote', payload);
    return unwrap(response);
  },

  async updateSchoolProfile(payload: Partial<SchoolProfile>) {
    const response = await api.put<ApiEnvelope<SchoolProfile>>('/api/v1/schools/current', payload);
    return unwrap(response);
  },

  async updateClass(classId: string, payload: Partial<{ name: string; capacity: number; section: string }>) {
    const response = await api.put<ApiEnvelope<unknown>>(`/api/v1/schools/current/classes/${classId}`, payload);
    return unwrap(response);
  },

  async closeSession(sessionId: string) {
    const response = await api.put<ApiEnvelope<unknown>>(`/api/v1/schools/current/sessions/${sessionId}/close`);
    return unwrap(response);
  },

  async createSession(payload: { name: string; startDate: string; endDate: string }) {
    const response = await api.post<ApiEnvelope<unknown>>('/api/v1/schools/current/sessions', payload);
    return unwrap(response);
  },

  async getFeeCollectionReport(params?: { termId?: string; format?: string }) {
    const response = await api.get('/api/v1/reports/fee-collection', { params, responseType: 'blob' });
    return response.data;
  },

  async listSubjects() {
    const response = await api.get<ApiEnvelope<SubjectResponse[]>>('/api/v1/subjects');
    return unwrap(response);
  },

  async createSubject(payload: CreateSubjectPayload) {
    const response = await api.post<ApiEnvelope<SubjectResponse>>('/api/v1/subjects', payload);
    return unwrap(response);
  },

  async updateSubject(subjectId: string, payload: CreateSubjectPayload) {
    const response = await api.put<ApiEnvelope<SubjectResponse>>(`/api/v1/subjects/${subjectId}`, payload);
    return unwrap(response);
  },

  async deactivateSubject(subjectId: string) {
    const response = await api.delete<ApiEnvelope<void>>(`/api/v1/subjects/${subjectId}`);
    return unwrap(response);
  },

  async getSubjectsForClass(classId: string) {
    const response = await api.get<ApiEnvelope<ClassSubjectResponse[]>>(`/api/v1/subjects/class/${classId}`);
    return unwrap(response);
  },

  async getTodayClassAttendance(classId: string) {
    const response = await api.get<ApiEnvelope<TodayAttendanceResponse>>(`/api/v1/attendance/classes/${classId}/today`);
    return unwrap(response);
  },

  async getStudentAttendance(studentId: string, termId: string) {
    const response = await api.get<ApiEnvelope<AttendanceResponse[]>>(`/api/v1/attendance/students/${studentId}`, {
      params: { termId },
    });
    return unwrap(response);
  },

  async getStudentAttendanceSummary(studentId: string, termId: string) {
    const response = await api.get<ApiEnvelope<AttendanceSummaryResponse>>(`/api/v1/attendance/students/${studentId}/summary`, {
      params: { termId },
    });
    return unwrap(response);
  },

  async assignSubjectToClass(classId: string, payload: AssignSubjectPayload) {
    const response = await api.post<ApiEnvelope<ClassSubjectResponse>>(`/api/v1/subjects/class/${classId}/assign`, payload);
    return unwrap(response);
  },

  async removeSubjectFromClass(classId: string, subjectId: string) {
    const response = await api.delete<ApiEnvelope<void>>(`/api/v1/subjects/class/${classId}/subject/${subjectId}`);
    return unwrap(response);
  },
};
