export interface UserSummaryResponse {
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  userType: 'SUPER_ADMIN' | 'SCHOOL_ADMIN' | 'TEACHER' | 'ACCOUNTANT' | 'PARENT';
  status: 'ACTIVE' | 'INACTIVE';
  lastLogin: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export interface CreateStaffRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  userType: 'SCHOOL_ADMIN' | 'TEACHER' | 'ACCOUNTANT';
}

export interface CreateParentRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  studentIds: string[];
}
