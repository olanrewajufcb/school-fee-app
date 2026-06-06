export interface ChildInfo {
    studentId: string;
    guardianId: string;
    relationship: string;
    canViewFees: boolean;
    canViewResults: boolean;
    canViewAttendance: boolean;
}

export interface UserProfile {
    userId: string;
    keycloakId: string;
    email: string;
    phoneNumber: string;
    firstName: string;
    lastName: string;
    userType: 'SUPER_ADMIN' | 'SCHOOL_ADMIN' | 'ACCOUNTANT' | 'TEACHER' | 'PARENT';
    schoolId: string;
    schoolName: string;
    roles: string[];
    children: ChildInfo[];
    lastLogin: string;
    isActive: boolean;
}

export interface AuthContextType {
    isLoading: boolean;
    isAuthenticated: boolean;
    user: UserProfile | null;
    error: string | null;
    login: () => void;
    logout: () => void;
    hasRole: (role: string) => boolean;
    isParent: boolean;
    isSchoolAdmin: boolean;
    isAccountant: boolean;
    isTeacher: boolean;
    isSuperAdmin: boolean;
}