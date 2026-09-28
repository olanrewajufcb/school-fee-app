import axios from 'axios';
import apiClient from './client';
import ENV from '../config/environment';
import {
  ApiResponse,
  UserProfile,
  CheckAccountRequest,
  CheckAccountResponse,
  SendOtpRequest,
  VerifyOtpRequest,
  SetPasswordRequest,
  KeycloakTokenResponse,
} from '../types';

export const authApi = {
  /**
   * Direct login using Keycloak Resource Owner Password Credentials Grant
   */
  async login(username: string, password: string):Promise<{ token: string; refreshToken: string; user: UserProfile }> {
    const tokenUrl = `${ENV.KEYCLOAK_URL}/realms/${ENV.REALM}/protocol/openid-connect/token`;
    const params = new URLSearchParams();
    params.append('grant_type', 'password');
    params.append('client_id', ENV.CLIENT_ID);
    params.append('username', username.trim());
    params.append('password', password);

    const tokenResponse = await axios.post<KeycloakTokenResponse>(tokenUrl, params.toString(), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      timeout: ENV.TIMEOUT_MS,
    });

    const accessToken = tokenResponse.data.access_token;
    const refreshToken = tokenResponse.data.refresh_token;

    // Fetch user profile with the acquired access token
    const profileResponse = await apiClient.get<ApiResponse<UserProfile>>('/api/v1/auth/me', {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    return {
      token: accessToken,
      refreshToken,
      user: profileResponse.data.data,
    };
  },

  /**
   * Get currently authenticated user profile
   */
  async getCurrentUser(): Promise<UserProfile> {
    const res = await apiClient.get<ApiResponse<UserProfile>>('/api/v1/auth/me');
    return res.data.data;
  },

  /**
   * Check if user account / parent phone exists in any registered school
   */
  async checkAccount(phoneNumber: string): Promise<CheckAccountResponse> {
    const res = await apiClient.post<ApiResponse<CheckAccountResponse>>('/api/v1/auth/check-account', {
      phoneNumber: phoneNumber.trim(),
    });
    return res.data.data;
  },

  /**
   * Send 6-digit OTP verification code to guardian phone number
   */
  async sendOtp(phoneNumber: string, schoolId?: string): Promise<{ message: string }> {
    const res = await apiClient.post<ApiResponse<{ message: string }>>('/api/v1/auth/send-otp', {
      phoneNumber: phoneNumber.trim(),
      schoolId,
    });
    return res.data.data;
  },

  /**
   * Verify OTP code submitted by guardian
   */
  async verifyOtp(payload: VerifyOtpRequest): Promise<any> {
    const res = await apiClient.post<ApiResponse<any>>('/api/v1/auth/verify-otp', {
      phoneNumber: payload.phoneNumber.trim(),
      otpCode: payload.otpCode.trim(),
      schoolId: payload.schoolId,
    });
    return res.data.data;
  },

  /**
   * Set secure password after OTP verification
   */
  async setPassword(payload: SetPasswordRequest): Promise<any> {
    const res = await apiClient.post<ApiResponse<any>>('/api/v1/auth/set-password', {
      phoneNumber: payload.phoneNumber.trim(),
      password: payload.password,
    });
    return res.data.data;
  },

  /**
   * Fetch Keycloak SSO Configuration
   */
  async getKeycloakConfig(): Promise<{ authServerUrl: string; realm: string; clientId: string }> {
    const res = await apiClient.get<ApiResponse<any>>('/api/v1/auth/keycloak-config');
    return res.data.data;
  },
};

export default authApi;
