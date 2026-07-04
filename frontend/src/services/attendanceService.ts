import api from '@/lib/api';

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  errors?: Array<{ code?: string; message?: string; field?: string }>;
}

export interface AttendanceSessionResponse {
  sessionId: string;
  classId: string;
  className: string;
  termId: string;
  termName: string;
  date: string;
  sessionType: 'MORNING_ARRIVAL' | 'AFTERNOON_DEPARTURE';
  isComplete: boolean;
  totalStudents: number;
  markedCount: number;
  presentCount: number;
  absentCount: number;
  lateCount: number;
}

export interface AttendanceResponse {
  attendanceId: string;
  studentId: string;
  studentName: string;
  admissionNumber: string;
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED' | 'PICKED_UP_EARLY';
  date: string;
  sessionType: 'MORNING_ARRIVAL' | 'AFTERNOON_DEPARTURE';
  arrivalTime?: string; // HH:mm
  broughtBy?: string;
  departureTime?: string; // HH:mm
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

export interface ParentAttendanceResponse {
  studentId: string;
  studentName: string;
  className: string;
  date: string;
  arrivalTime?: string;
  broughtBy?: string;
  arrivalStatus?: string;
  departureTime?: string;
  departureStatus?: string;
  pickedUpBy?: string;
  pickUpPersonName?: string;
  pickUpPersonPhone?: string;
}

export interface AttendanceSummaryResponse {
  totalSchoolDays: number;
  daysPresent: number;
  daysAbsent: number;
  daysLate: number;
  earlyPickups: number;
  attendancePercentage: number;
}

export interface CreateAttendanceSessionPayload {
  classId: string;
  termId: string;
  date: string; // YYYY-MM-DD
  sessionType: 'MORNING_ARRIVAL' | 'AFTERNOON_DEPARTURE';
}

export interface AttendanceMarkEntry {
  studentId: string;
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED';
  arrivalTime?: string; // HH:mm
  broughtBy?: string;
  departureTime?: string; // HH:mm
  pickedUpBy?: string;
  pickUpPersonName?: string;
  pickUpPersonPhone?: string;
  notes?: string;
}

export interface MarkAttendancePayload {
  marks: AttendanceMarkEntry[];
}

export interface UpdateAttendancePayload {
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED' | 'PICKED_UP_EARLY';
  arrivalTime?: string;
  broughtBy?: string;
  departureTime?: string;
  pickedUpBy?: string;
  pickUpPersonName?: string;
  pickUpPersonPhone?: string;
  notes?: string;
}

function unwrap<T>(response: { data: ApiEnvelope<T> | T }): T {
  const body = response.data as ApiEnvelope<T>;
  if (body && typeof body === 'object' && 'data' in body && 'success' in body) {
    return body.data;
  }
  return response.data as T;
}

export const attendanceService = {
  async createSession(payload: CreateAttendanceSessionPayload) {
    const response = await api.post<ApiEnvelope<AttendanceSessionResponse>>('/api/v1/attendance/sessions', payload);
    return unwrap(response);
  },

  async getSessions(classId: string, date?: string) {
    const response = await api.get<ApiEnvelope<AttendanceSessionResponse[]>>('/api/v1/attendance/sessions', {
      params: { classId, date },
    });
    return unwrap(response);
  },

  async getSessionMarks(sessionId: string) {
    const response = await api.get<ApiEnvelope<AttendanceResponse[]>>(`/api/v1/attendance/sessions/${sessionId}/marks`);
    return unwrap(response);
  },

  async markAttendance(sessionId: string, payload: MarkAttendancePayload) {
    const response = await api.post<ApiEnvelope<AttendanceResponse[]>>(`/api/v1/attendance/sessions/${sessionId}/marks`, payload);
    return unwrap(response);
  },

  async updateMark(markId: string, payload: UpdateAttendancePayload) {
    const response = await api.put<ApiEnvelope<AttendanceResponse>>(`/api/v1/attendance/marks/${markId}`, payload);
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

  async getTodayClassAttendance(classId: string) {
    const response = await api.get<ApiEnvelope<TodayAttendanceResponse>>(`/api/v1/attendance/classes/${classId}/today`);
    return unwrap(response);
  },

  async getMyChildrenAttendance() {
    const response = await api.get<ApiEnvelope<ParentAttendanceResponse[]>>('/api/v1/attendance/my-children');
    return unwrap(response);
  },

  async getMyChildAttendance(studentId: string, date?: string) {
    const response = await api.get<ApiEnvelope<ParentAttendanceResponse[]>>(`/api/v1/attendance/my-children/${studentId}`, {
      params: { date },
    });
    return unwrap(response);
  },
};
