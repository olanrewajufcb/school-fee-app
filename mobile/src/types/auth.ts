export type UserType = 'PARENT' | 'TEACHER' | 'SCHOOL_ADMIN' | 'ACCOUNTANT' | 'SUPER_ADMIN';

export interface UserProfile {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  userType: UserType;
  roles: string[];
  schoolId?: string;
  schoolName?: string;
  status: string;
}

export interface CheckAccountRequest {
  phoneNumber: string;
}

export interface SchoolOption {
  schoolId: string;
  schoolName?: string;
  guardianName: string;
}

export interface CheckAccountResponse {
  found: boolean;
  schoolName?: string;
  guardianName?: string;
  childrenCount?: number;
  message?: string;
  options?: SchoolOption[];
}

export interface SendOtpRequest {
  phoneNumber: string;
  schoolId?: string;
}

export interface VerifyOtpRequest {
  phoneNumber: string;
  otpCode: string;
  schoolId?: string;
}

export interface SetPasswordRequest {
  phoneNumber: string;
  password: string;
}

export interface KeycloakTokenResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  refresh_expires_in: number;
}
