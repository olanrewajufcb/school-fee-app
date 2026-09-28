export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED';

export interface AttendanceSession {
  sessionId: string;
  classId: string;
  className: string;
  date: string;
  sessionType: 'MORNING' | 'AFTERNOON';
  isClosed: boolean;
  takenBy: string;
  presentCount: number;
  absentCount: number;
  lateCount: number;
  totalStudents: number;
}

export interface StudentAttendanceMark {
  markId: string;
  studentId: string;
  studentName: string;
  admissionNumber: string;
  status: AttendanceStatus;
  arrivalTime?: string;
  remarks?: string;
}

export interface MarkAttendancePayload {
  sessionId: string;
  marks: Array<{
    studentId: string;
    status: AttendanceStatus;
    remarks?: string;
  }>;
}

export interface StudentAttendanceSummary {
  studentId: string;
  totalDays: number;
  presentDays: number;
  absentDays: number;
  lateDays: number;
  attendancePercentage: number;
  history: Array<{
    date: string;
    status: AttendanceStatus;
    remarks?: string;
  }>;
}
