import apiClient from './client';
import {
  ApiResponse,
  AttendanceSession,
  StudentAttendanceMark,
  MarkAttendancePayload,
  StudentAttendanceSummary,
} from '../types';

export const attendanceApi = {
  /**
   * Get attendance sessions for a class on a given date
   */
  async getSessions(classId: string, date?: string): Promise<AttendanceSession[]> {
    const res = await apiClient.get<ApiResponse<AttendanceSession[]>>('/api/v1/attendance/sessions', {
      params: { classId, date },
    });
    return res.data.data;
  },

  /**
   * Create a new attendance session for today's roll call
   */
  async createSession(classId: string, sessionType: 'MORNING' | 'AFTERNOON' = 'MORNING'): Promise<AttendanceSession> {
    const res = await apiClient.post<ApiResponse<AttendanceSession>>('/api/v1/attendance/sessions', {
      classId,
      sessionType,
      date: new Date().toISOString().split('T')[0],
    });
    return res.data.data;
  },

  /**
   * Get student marks for an attendance session
   */
  async getSessionMarks(sessionId: string): Promise<StudentAttendanceMark[]> {
    const res = await apiClient.get<ApiResponse<StudentAttendanceMark[]>>(`/api/v1/attendance/sessions/${sessionId}/marks`);
    return res.data.data;
  },

  /**
   * Teacher: Mark attendance for students (bulk submit)
   */
  async markAttendance(payload: MarkAttendancePayload): Promise<void> {
    await apiClient.post(`/api/v1/attendance/sessions/${payload.sessionId}/marks`, {
      marks: payload.marks,
    });
  },

  /**
   * Parent/Admin: Get attendance summary and punctuality history for a student
   */
  async getStudentAttendanceSummary(studentId: string): Promise<StudentAttendanceSummary> {
    const res = await apiClient.get<ApiResponse<StudentAttendanceSummary>>(`/api/v1/attendance/students/${studentId}/summary`);
    return res.data.data;
  },
};

export default attendanceApi;
