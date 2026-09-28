import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import * as SecureStore from 'expo-secure-store';
import ENV from '../config/environment';

export const apiClient = axios.create({
  baseURL: ENV.API_URL,
  timeout: ENV.TIMEOUT_MS,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// Request Interceptor: Attach JWT Bearer Token
apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    try {
      const token = await SecureStore.getItemAsync(ENV.STORAGE_KEYS.ACCESS_TOKEN);
      if (token && config.headers) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch {
      // In web or testing environments, SecureStore may fail gracefully
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Handle global errors & token expirations
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<{ message?: string; errors?: any }>) => {
    if (error.response?.status === 401) {
      // Clear token on 401 Unauthorized
      try {
        await SecureStore.deleteItemAsync(ENV.STORAGE_KEYS.ACCESS_TOKEN);
      } catch {}
    }
    const message =
      error.response?.data?.message ||
      error.message ||
      'An unexpected network error occurred';
    return Promise.reject(new Error(message));
  }
);

export default apiClient;
