export interface StudentSummary {
  studentId: string;
  admissionNumber: string;
  firstName: string;
  middleName?: string;
  lastName: string;
  gender: 'MALE' | 'FEMALE';
  classId: string;
  className: string;
  gradeLevel: string;
  schoolId: string;
  schoolName?: string;
  enrollmentStatus: 'ACTIVE' | 'GRADUATED' | 'SUSPENDED' | 'WITHDRAWN';
}

export interface StudentDetail extends StudentSummary {
  dateOfBirth?: string;
  medicalNotes?: string;
  guardians: Array<{
    guardianId: string;
    firstName: string;
    lastName: string;
    phone: string;
    relationship: string;
    isPrimary: boolean;
  }>;
}
