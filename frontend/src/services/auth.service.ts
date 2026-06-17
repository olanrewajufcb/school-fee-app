import axios from 'axios';

// Public API instance — NO Keycloak interceptor
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

export interface CheckAccountResponse {
  found: boolean;
  schoolName?: string;
  guardianName?: string;
  childrenCount?: number;
  message: string;
  options?: Array<{
    schoolId: string;
    schoolName?: string;
    guardianName: string;
  }>;
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

export const authService = {
  async checkAccount(phone: string) {
    const { data } = await publicApi.post<{ data: CheckAccountResponse }>(
      '/api/v1/auth/check-account',
      { phoneNumber: phone }
    );
    return data.data;
  },

  async sendOtp(phone: string, schoolId?: string) {
    const { data } = await publicApi.post(
      '/api/v1/auth/send-otp',
      { phoneNumber: phone, schoolId }
    );
    return data;
  },

  async verifyOtp(request: VerifyOtpRequest) {
    const { data } = await publicApi.post(
      '/api/v1/auth/verify-otp',
      request
    );
    return data;
  },

  async setPassword(request: SetPasswordRequest) {
    const { data } = await publicApi.post(
      '/api/v1/auth/set-password',
      request
    );
    return data;
  },
};
