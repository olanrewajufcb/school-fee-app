import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Auth API
export const authApi = {
  login: (username: string, password: string) =>
    api.post('/auth/login', { username, password }),
  register: (data: { username: string; password: string; email: string; fullName: string; role?: string }) =>
    api.post('/auth/register', data),
  getCurrentUser: () => api.get('/auth/me'),
};

// Schools API
export const schoolsApi = {
  getAll: () => api.get('/schools'),
  getById: (id: number) => api.get(`/schools/${id}`),
  create: (data: Record<string, unknown>) => api.post('/schools', data),
  update: (id: number, data: Record<string, unknown>) => api.put(`/schools/${id}`, data),
  delete: (id: number) => api.delete(`/schools/${id}`),
};

// Fees API
export const feesApi = {
  getAllFeeTypes: () => api.get('/fees/types'),
  getFeeTypeById: (id: number) => api.get(`/fees/types/${id}`),
  createFeeType: (data: Record<string, unknown>) => api.post('/fees/types', data),
  updateFeeType: (id: number, data: Record<string, unknown>) => api.put(`/fees/types/${id}`, data),
  deleteFeeType: (id: number) => api.delete(`/fees/types/${id}`),
  getStudentFees: (studentId: number) => api.get(`/fees/student/${studentId}`),
  assignFee: (data: Record<string, unknown>) => api.post('/fees/assign', data),
  recordPayment: (studentFeeId: number, amount: string) =>
    api.post(`/fees/${studentFeeId}/pay`, { amount }),
  getFeeSummary: (studentId: number) => api.get(`/fees/student/${studentId}/summary`),
};

// Payments API
export const paymentsApi = {
  getAll: () => api.get('/payments'),
  getById: (id: number) => api.get(`/payments/${id}`),
  process: (data: Record<string, unknown>) => api.post('/payments', data),
  getByStudent: (studentId: number) => api.get(`/payments/student/${studentId}`),
  getSummary: () => api.get('/payments/summary'),
};

// Notifications API
export const notificationsApi = {
  getAll: () => api.get('/notifications'),
  send: (data: Record<string, unknown>) => api.post('/notifications/send', data),
  getTemplates: () => api.get('/notifications/templates'),
  sendReminders: (data: Record<string, unknown>) => api.post('/notifications/reminders/fee-due', data),
};

export default api;
